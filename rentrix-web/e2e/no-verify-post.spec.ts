import { mkdirSync } from "node:fs"
import path from "node:path"
import { expect, test, type Page, type TestInfo } from "@playwright/test"
import { daysAgo, freshLogin, writeHomeReview } from "./support"

/**
 * Post with no verification (captain 2026-11-24 17:35, SYSTEM_DESIGN D-163) at 390 px: a brand-new
 * user with no phone, Aadhaar or LinkedIn verified adds their home and reviews it. Nothing sends
 * them to a verify step first; the welcome screen leads with "Add your home" and offers "Verify to
 * get a green tick" as an optional second button. The review shows "Not verified yet" and does not
 * count in the home's rating until the team verifies it.
 *
 * One screenshot per step goes to the test output, and to `NO_VERIFY_SHOTS` when set.
 */

async function shot(page: Page, testInfo: TestInfo, name: string) {
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })
  const out = process.env.NO_VERIFY_SHOTS
  if (out) {
    mkdirSync(out, { recursive: true })
    await page.screenshot({ path: path.join(out, `${name}.png`), fullPage: true })
  }
}

async function noSidewaysScroll(page: Page) {
  const width = await page.evaluate(() => document.documentElement.scrollWidth)
  expect(width).toBeLessThanOrEqual(390)
}

test.describe("post with no verification (390 px)", () => {
  test.skip(({ viewport }) => (viewport?.width ?? 0) > 390, "runs in the 390 px mobile project")

  test("a new user adds a home and reviews it without verifying anything", async ({
    page,
  }, testInfo) => {
    test.setTimeout(testInfo.timeout * 2)
    const tag = `${Date.now().toString(36)}${testInfo.project.name[0]}`
    const building = `No Verify Court ${tag}`
    const review =
      "Bright rooms and a fair landlord. The water supply was steady and repairs came quickly."

    await test.step("sign up: the welcome screen leads with Add your home", async () => {
      await freshLogin(page)
      await page.locator("label").filter({ hasText: "I rent a home" }).click()
      await page.getByRole("checkbox", { name: /I accept the Terms/ }).check()
      await page.getByRole("button", { name: "Continue" }).click()
      await expect(page.getByText("Your account is created.")).toBeVisible()
      await expect(page.getByText("You can post right away.")).toBeVisible()
      const addHome = page.getByTestId("welcome-add-home")
      const verify = page.getByTestId("welcome-verify")
      await expect(addHome).toContainText("Add your home")
      await expect(verify).toContainText("Verify to get a green tick")
      // The add-home button comes first, above the optional verify one.
      const [addTop, verifyTop] = await Promise.all([
        addHome.evaluate((el) => el.getBoundingClientRect().top),
        verify.evaluate((el) => el.getBoundingClientRect().top),
      ])
      expect(addTop).toBeLessThan(verifyTop)
      await noSidewaysScroll(page)
      await shot(page, testInfo, "01-welcome")
      await addHome.click()
    })

    await test.step("add the home: no verify step in between", async () => {
      await expect(page).toHaveURL(/\/homes\/new$/)
      await page.locator("#ah-city").selectOption({ label: "Pune" })
      await page.locator("#pf-area").fill("Baner")
      await page
        .getByRole("listbox", { name: "Areas" })
        .getByRole("option", { name: /^Baner/ })
        .click()
      await page.locator("#ah-building").fill(building)
      await page.getByLabel("Flat / house no.").fill(`V-${tag.slice(-3)}`)
      await shot(page, testInfo, "02-add-home")
      await page.getByTestId("ah-next").click()
      const step2 = page.getByTestId("add-home-step").filter({ hasText: /step 2 of 4/i })
      await expect(step2.or(page.getByTestId("home-matches"))).toBeVisible()
      if (await page.getByTestId("home-matches").isVisible())
        await page.getByTestId("home-match-none").click()
      await page.getByTestId("ah-relation-rent").click()
      await page.getByTestId("ah-next").click()
      await page.getByLabel("Moved in").fill(daysAgo(120))
      await page.getByTestId("ah-next").click()
      await page.getByTestId("ah-save").click()
      const done = page.getByTestId("add-home-done")
      await expect(done.getByTestId("add-home-steps-left")).toHaveText(/home added/i)
      await expect(page).not.toHaveURL(/\/verify/)
      await shot(page, testInfo, "03-home-added")
      await done.getByTestId("add-home-write-review").click()
    })

    await test.step("review the home: posted at once", async () => {
      await writeHomeReview(page, 4, review)
      await page.getByTestId("review-submit").click()
      const submitted = page.getByTestId("review-submitted")
      await expect(submitted.getByText(/you submitted this review/i)).toBeVisible()
      await shot(page, testInfo, "04-review-submitted")
      await submitted.getByTestId("review-view-home").click()
    })

    await test.step("the home's page lists it as Not verified yet, outside the rating", async () => {
      await expect(page).toHaveURL(/\/p\//)
      const mine = page.getByTestId("property-review").filter({ hasText: review })
      await expect(mine).toBeVisible()
      await expect(mine.getByTestId("tick-unverified")).toHaveText("Not verified yet")
      await expect(page.getByTestId("unverified-note")).toBeVisible()
      // The list's count names the listed review, not the verified-only "0 reviews".
      await expect(page.getByTestId("reviews-count")).toHaveText("1 review · 0 verified")
      // On one line beside the heading, never broken as "0 / verified" at 390 px.
      const countHeight = await page
        .getByTestId("reviews-count")
        .evaluate((el) => el.getBoundingClientRect().height)
      expect(countHeight).toBeLessThanOrEqual(24)
      await expect(
        page.getByRole("region", { name: "Property rating" }).filter({ visible: true }),
      ).toContainText("No verified reviews yet")
      // The optional nudge stays; nothing is closed.
      await expect(page.getByRole("region", { name: "Verification" })).toContainText(
        "You can post now.",
      )
      await noSidewaysScroll(page)
      await shot(page, testInfo, "05-home-page")
    })
  })
})
