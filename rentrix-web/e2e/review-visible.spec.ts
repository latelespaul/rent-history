import { mkdirSync } from "node:fs"
import path from "node:path"
import { expect, test, type Browser, type Page, type TestInfo } from "@playwright/test"
import { daysAgo, freshLogin, pickRole, testPhone, verifyPhone, writeHomeReview } from "./support"

/**
 * Release gate (captain 2026-11-24 00:31, D-161, D-162), at 390 px: a new tenant adds a home in a
 * society they type ("Sobha …", HSR Layout 560102, flat C202) and reviews it; a second user and a
 * logged-out visitor find it by the society name, the area and the PIN code, open it and read the
 * review; the author reopens it from My tenancies and edits it. The tenancy started 120 days ago,
 * past the 60-day wait before the first review (D-39).
 *
 * One screenshot per step goes to the test output, and to `REVIEW_VISIBLE_SHOTS` when set.
 */

async function shot(page: Page, testInfo: TestInfo, name: string) {
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })
  const out = process.env.REVIEW_VISIBLE_SHOTS
  if (out) {
    mkdirSync(out, { recursive: true })
    await page.screenshot({ path: path.join(out, `${name}.png`), fullPage: true })
  }
}

/** Searches from the one box, newest first (the new home has no average yet), and opens the home. */
async function findAndOpen(page: Page, words: string, society: string) {
  await page.goto("/search")
  const search = page.getByRole("search")
  await search.getByRole("combobox", { name: /Locality, society, building or PIN/ }).fill(words)
  await search.getByRole("button", { name: "Search" }).click()
  const matching = page.getByText(`Matching “${words}”.`)
  // Sort only once the results show the words, so the sort change keeps them.
  await expect(matching).toBeVisible()
  await page.getByLabel("Sort").selectOption("newest")
  await expect(page).toHaveURL(/[?&]q=.*sort=newest/)
  await expect(matching).toBeVisible()
  const card = page.locator("article").filter({ hasText: society })
  await expect(card).toBeVisible()
  // Open by default: the tenant's home shows at once, tagged rather than hidden.
  await expect(card.getByTestId("card-not-verified")).toBeVisible()
  await expect(card.getByTestId("location-pending")).toBeVisible()
  return card
}

async function noHorizontalScroll(page: Page) {
  const fits = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
  expect(fits, "no horizontal scroll at 390 px").toBe(true)
}

async function newPage(browser: Browser, testInfo: TestInfo) {
  const { baseURL, viewport, deviceScaleFactor, isMobile, hasTouch } = testInfo.project.use
  const context = await browser.newContext({
    baseURL,
    viewport,
    deviceScaleFactor,
    isMobile,
    hasTouch,
  })
  return context.newPage()
}

test.describe("review visible and home findable release gate (390 px)", () => {
  test.skip(({ viewport }) => (viewport?.width ?? 0) > 390, "runs in the 390 px mobile project")

  test("a tenant's review shows on the home, the home is found by anyone, the author edits it", async ({
    page,
    browser,
  }, testInfo) => {
    test.setTimeout(testInfo.timeout * 4)
    const tag = Date.now().toString(36)
    const society = `Sobha ${tag}`
    const review =
      "Steady water all year, a lift that works and a quiet lane. Repairs took a week at most."
    const edited =
      "Steady water all year and a quiet lane. The lift broke once; it was fixed in two days."

    await test.step("1 a new tenant signs up and verifies their phone", async () => {
      await freshLogin(page)
      await page.locator("label").filter({ hasText: "I rent a home" }).click()
      await page.getByRole("checkbox", { name: /I accept the Terms/ }).check()
      await page.getByRole("button", { name: "Continue" }).click()
      await page.getByTestId("welcome-verify").click()
      await verifyPhone(page, testPhone())
      await expect(page).toHaveURL(/\/homes\/new$/)
    })

    await test.step("2 add the home: Bangalore, HSR Layout, a typed society, flat C202", async () => {
      await page.locator("#ah-city").selectOption({ label: "Bangalore" })
      await page.locator("#pf-area").fill("HSR")
      await page
        .getByRole("list", { name: "Areas" })
        .getByRole("button", { name: /^HSR Layout/ })
        .click()
      await page.locator("#pf-society-find").fill(society)
      await page.getByTestId("pf-society-matches").getByTestId("pf-society-add-typed").click()
      await page.getByLabel("Flat / house no.").fill("C202")
      await shot(page, testInfo, "01-add-home")
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
      await shot(page, testInfo, "02-home-added")
    })

    await test.step("3 write the review, then see it on the home's page", async () => {
      await page.getByTestId("add-home-done").getByTestId("add-home-write-review").click()
      await writeHomeReview(page, 4, review)
      await page.getByTestId("review-submit").click()
      await expect(page.getByTestId("review-submitted")).toBeVisible()
      await shot(page, testInfo, "03-review-submitted")
      await page.getByTestId("review-submitted").getByTestId("review-view-home").click()
      const mine = page.getByTestId("property-review").filter({ hasText: review })
      await expect(mine).toBeVisible()
      await expect(mine.getByTestId("my-review")).toBeVisible()
      await expect(mine.getByRole("img", { name: "Rated 4 out of 5" })).toBeVisible()
      // Captain 2026-11-24 (3): the tenant of this home is not asked "I rent or rented this home".
      await expect(page.getByTestId("i-rent-this-home")).toHaveCount(0)
      await expect(page.getByTestId("home-my-review")).toBeVisible()
      await noHorizontalScroll(page)
      await shot(page, testInfo, "04-author-home-page")
    })

    await test.step("4 the author reopens it from My tenancies and the tenancy page", async () => {
      await page.goto("/me/tenancies")
      const card = page.locator("article").filter({ hasText: society })
      await expect(card.getByTestId("tenancy-my-review")).toBeVisible()
      await shot(page, testInfo, "05-my-tenancies")
      await card.getByRole("link", { name: "Open", exact: true }).click()
      await expect(page).toHaveURL(/\/tenancies\//)
      await expect(page.getByTestId("tenancy-write-review")).toHaveCount(0)
      await shot(page, testInfo, "06-tenancy-page")
      await page.getByTestId("tenancy-my-review").click()
      await expect(page).toHaveURL(/\/p\/[^/]+#reviews$/)
      await expect(page.getByTestId("my-review")).toBeInViewport()
      await shot(page, testInfo, "07-reopened")
    })

    await test.step("5 the author edits the review", async () => {
      const mine = page.getByTestId("property-review").filter({ hasText: review })
      await mine.getByRole("button", { name: /Edit my review/ }).click()
      await mine.getByLabel("Your review").fill(edited)
      await shot(page, testInfo, "08-editing")
      await mine.getByRole("button", { name: "Save" }).click()
      const saved = page.getByTestId("property-review").filter({ hasText: edited })
      await expect(saved).toBeVisible()
      await expect(saved.getByTestId("review-dates")).toContainText(/edited/)
      await shot(page, testInfo, "09-edited")
    })

    await test.step("6 a second user finds it by society, area and PIN, and reads it", async () => {
      const other = await newPage(browser, testInfo)
      await freshLogin(other)
      await pickRole(other, "I rent a home")
      for (const [words, name] of [
        [society, "10-second-user-society"],
        ["HSR Layout", "11-second-user-area"],
        ["560102", "12-second-user-pin"],
      ] as const) {
        await findAndOpen(other, words, society)
        await shot(other, testInfo, name)
      }
      const card = other.locator("article").filter({ hasText: society })
      await card.getByRole("link", { name: society }).click()
      const theirs = other.getByTestId("property-review").filter({ hasText: edited })
      await expect(theirs).toBeVisible()
      await expect(theirs.getByTestId("my-review")).toHaveCount(0)
      await expect(theirs.getByRole("button", { name: /Edit my review/ })).toHaveCount(0)
      await expect(other.getByTestId("i-rent-this-home")).toBeVisible()
      await noHorizontalScroll(other)
      await shot(other, testInfo, "13-second-user-home")
      await other.context().close()
    })

    await test.step("7 a logged-out visitor finds it by society, area and PIN, and reads it", async () => {
      const visitor = await newPage(browser, testInfo)
      for (const [words, name] of [
        [society, "14-visitor-society"],
        ["HSR Layout", "15-visitor-area"],
        ["560102", "16-visitor-pin"],
      ] as const) {
        await findAndOpen(visitor, words, society)
        await expect(visitor).not.toHaveURL(/\/login/)
        await shot(visitor, testInfo, name)
      }
      const card = visitor.locator("article").filter({ hasText: society })
      await card.getByRole("link", { name: society }).click()
      const read = visitor.getByTestId("property-review").filter({ hasText: edited })
      await expect(read).toBeVisible()
      await expect(read.getByRole("img", { name: "Rated 4 out of 5" })).toBeVisible()
      await expect(read.getByRole("button", { name: /Edit my review/ })).toHaveCount(0)
      await expect(visitor.getByText(/Sign in to read/)).toHaveCount(0)
      await noHorizontalScroll(visitor)
      await shot(visitor, testInfo, "17-visitor-home")
      await visitor.context().close()
    })
  })
})
