import { expect, test, type Locator, type Page } from "@playwright/test"
import { daysAgo, freshLogin, testPhone, verifyPhone, writeHomeReview } from "./support"

/**
 * Captain 2026-11-24 09:44, at 390 px with touch: after a new tenant submits a review, a tap on the
 * home's name or card opens the home's page from the success screen, from My tenancies and from
 * search results. Taps land where a finger would, at the centre of what is tapped, so anything
 * drawn over a link (a badge, an overlay) fails here.
 */

/** A finger tap at the centre of `target`, whatever element is on top there. */
async function fingerTap(page: Page, target: Locator) {
  await target.scrollIntoViewIfNeeded()
  const box = await target.boundingBox()
  if (!box) throw new Error("nothing to tap")
  await page.touchscreen.tap(box.x + box.width / 2, box.y + box.height / 2)
}

async function expectHomePage(page: Page, society: string) {
  await expect(page).toHaveURL(/\/p\/[^/#?]+/)
  await expect(page.getByRole("heading", { level: 1, name: society })).toBeVisible()
}

test.describe("open the home after a review (390 px, touch)", () => {
  test.skip(({ viewport }) => (viewport?.width ?? 0) > 390, "runs in the 390 px mobile project")

  test("a tap on the home's name or card opens its page", async ({ page }, testInfo) => {
    test.setTimeout(testInfo.timeout * 4)
    const society = `Sobha ${Date.now().toString(36)}`

    await test.step("a new tenant adds a home in a typed society and reviews it", async () => {
      await freshLogin(page)
      await page.locator("label").filter({ hasText: "I rent a home" }).click()
      await page.getByRole("checkbox", { name: /I accept the Terms/ }).check()
      await page.getByRole("button", { name: "Continue" }).click()
      await page.getByTestId("welcome-verify").click()
      await verifyPhone(page, testPhone())
      await expect(page).toHaveURL(/\/homes\/new$/)
      await page.locator("#ah-city").selectOption({ label: "Bangalore" })
      await page.locator("#pf-area").fill("HSR")
      await page
        .getByRole("list", { name: "Areas" })
        .getByRole("button", { name: /^HSR Layout/ })
        .click()
      await page.locator("#pf-society-find").fill(society)
      await page.getByTestId("pf-society-matches").getByTestId("pf-society-add-typed").click()
      await page.getByLabel("Flat / house no.").fill("C202")
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
      await expect(page.getByTestId("add-home-done")).toBeVisible()
      await page.getByTestId("add-home-done").getByTestId("add-home-write-review").tap()
      await writeHomeReview(
        page,
        4,
        "Steady water all year, a lift that works and a quiet lane. Repairs were quick.",
      )
      await page.getByTestId("review-submit").tap()
      await expect(page.getByTestId("review-submitted")).toBeVisible()
    })

    await test.step("the success screen: the home's name opens it", async () => {
      const home = page.getByTestId("review-submitted").getByTestId("review-home-link")
      await expect(home).toContainText(society)
      await fingerTap(page, home.getByText(society))
      await expectHomePage(page, society)
    })

    await test.step("My tenancies: the home's name opens it, Open opens the tenancy", async () => {
      await page.goto("/me/tenancies")
      const card = page.locator("article").filter({ hasText: society })
      await fingerTap(page, card.getByTestId("tenancy-home-link"))
      await expectHomePage(page, society)
      await page.goto("/me/tenancies")
      await fingerTap(page, card.getByRole("link", { name: "Open" }))
      await expect(page).toHaveURL(/\/tenancies\/[^/]+$/)
    })

    await test.step("search results: the name, the badge and the card body open it", async () => {
      // The area, newest first: the new home has no average yet.
      const results = "/search?q=HSR+Layout&sort=newest"
      const card = page.locator("article").filter({ hasText: society })
      for (const part of [
        card.getByRole("link", { name: society }),
        card.getByTestId("location-pending"),
        card.getByText(/landlord/),
      ]) {
        await page.goto(results)
        await expect(card).toBeVisible()
        await fingerTap(page, part)
        await expectHomePage(page, society)
      }
    })
  })
})
