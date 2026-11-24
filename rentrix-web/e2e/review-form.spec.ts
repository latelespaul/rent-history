import { expect, test, type Page, type TestInfo } from "@playwright/test"
import { approveReview, devLogin } from "./support"

/**
 * The review form (captain 2026-11-23 21:29, SYSTEM_DESIGN D-157, D-158) at 390 px: fill every
 * parameter of the home and the landlord, check, submit, then the home's page and the landlord's
 * page show the per-parameter averages.
 *
 * Seed data (DemoSeeder): Demo Tenant BLR-3's current tenancy of Demo Villa C started in 2024, so
 * the 60-day lock has passed; only the landlord has reviewed it, so both tenant reviews are still
 * to write. The home's two earlier tenants rated every parameter, so this review is the third
 * rating of each and its average shows (D-52) once the team ticks it: only verified reviews count
 * in the headline and the averages (D-163), so the spec approves both new reviews as the demo admin.
 * It changes that seed tenancy, which exists once, so the spec runs in the mobile project only.
 */
const HOME = {
  WATER_SUPPLY: 5,
  POWER_BACKUP: 4,
  MAINTENANCE: 4,
  CLEANLINESS: 3,
  SECURITY: 5,
  PARKING: 4,
  NOISE: 2,
  NEIGHBOURHOOD: 5,
  VALUE_FOR_RENT: 3,
}
const LANDLORD = {
  RESPONSIVENESS: 4,
  DEPOSIT_RETURN: 3,
  TRANSPARENCY: 4,
  PRIVACY: 5,
  BEHAVIOUR: 5,
}

async function shot(page: Page, testInfo: TestInfo, name: string) {
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })
}

test("fill every parameter, submit, and see the averages on the home and landlord pages", async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "the seed tenancy can be reviewed only once")
  test.setTimeout(testInfo.timeout * 2)
  const pros = `Steady water and a calm lane ${Date.now().toString(36)}`
  const cons = "The road outside gets noisy in the evening."
  const reviewIds: string[] = []
  const step = page.getByTestId("review-step")

  await devLogin(page, "Demo Tenant BLR-3", "/me/tenancies")

  await test.step("open the form from My tenancies; the stay is filled in", async () => {
    const mine = page.getByTestId("my-next-steps")
    const villa = mine.locator("div").filter({ hasText: "Demo Villa C" }).first()
    await villa.getByTestId("add-home-write-review").click()
    await expect(page).toHaveURL(/\/tenancies\/[^/]+\/review$/)
    await expect(step).toHaveAttribute("data-section", "stay")
    await expect(page.getByTestId("review-progress")).toContainText("Step 1 of 5")
    await expect(page.getByTestId("review-period")).toContainText("Still living here")
    await shot(page, testInfo, "01-stay")
    await page.getByTestId("review-next").click()
  })

  await test.step("rate all nine home parameters", async () => {
    await expect(step).toHaveAttribute("data-section", "home")
    for (const [criterion, stars] of Object.entries(HOME))
      await page.getByTestId(`rate-${criterion}-${stars}`).click()
    await page.getByLabel(/anything else about the home/i).fill("Good light in every room.")
    await shot(page, testInfo, "02-home")
    // Save draft keeps the answers on this device.
    await page.getByTestId("review-save-draft").click()
    await expect(page.getByTestId("review-draft-saved")).toBeVisible()
    await page.getByTestId("review-next").click()
  })

  await test.step("rate all five landlord parameters", async () => {
    await expect(step).toHaveAttribute("data-section", "landlord")
    await expect(page.getByTestId("review-progress")).toContainText("Step 3 of 5")
    for (const [criterion, stars] of Object.entries(LANDLORD))
      await page.getByTestId(`rate-${criterion}-${stars}`).click()
    await shot(page, testInfo, "03-landlord")
    await page.getByTestId("review-next").click()
  })

  await test.step("overall: worked out for the home, their own for the landlord", async () => {
    await expect(step).toHaveAttribute("data-section", "overall")
    // home: 35 / 9 = 3.9 → 4 stars; landlord: 21 / 5 = 4.2 → 4, overridden to 5
    await expect(page.getByTestId("overall-home-4")).toHaveAttribute("aria-checked", "true")
    await expect(page.getByTestId("overall-home-worked-out")).toBeVisible()
    await page.getByTestId("overall-landlord-5").click()
    await page.getByTestId("recommend-home-yes").click()
    await page.getByTestId("recommend-landlord-yes").click()
    await page.getByLabel(/^pros/i).fill(pros)
    await page.getByLabel(/^cons/i).fill(cons)
    await shot(page, testInfo, "04-overall")
    await page.getByTestId("review-next").click()
  })

  await test.step("check every answer, then submit", async () => {
    await expect(step).toHaveAttribute("data-section", "check")
    const home = page.getByTestId("review-summary-home")
    await expect(home).toContainText("Water supply")
    await expect(home).not.toContainText("Skipped")
    await expect(page.getByTestId("review-summary-landlord")).not.toContainText("Skipped")
    await expect(page.getByTestId("review-summary-pros-cons")).toContainText(pros)
    await shot(page, testInfo, "05-check")
    const answer = page.waitForResponse(
      (r) => r.request().method() === "POST" && /\/review-form$/.test(new URL(r.url()).pathname),
    )
    await page.getByTestId("review-submit").click()
    const written = (await (await answer).json()) as {
      home?: { id: string }
      landlord?: { id: string }
    }
    expect(written.home?.id && written.landlord?.id, "both reviews are written").toBeTruthy()
    reviewIds.push(written.home!.id, written.landlord!.id)
  })

  await test.step("success: both reviews are published", async () => {
    const submitted = page.getByTestId("review-submitted")
    await expect(submitted.getByText(/you submitted this review/i)).toBeVisible()
    // the landlord had already reviewed this tenancy, so both publish together (D-46)
    await expect(submitted.getByTestId("review-landlord-state")).toContainText(
      /published together/i,
    )
    await shot(page, testInfo, "06-success")
    await submitted.getByTestId("review-view-home").click()
  })

  await test.step("the new review shows Not verified yet until the team ticks it", async () => {
    await expect(page).toHaveURL(/\/p\//)
    const mine = page.getByTestId("property-review").filter({ hasText: pros })
    await expect(mine.getByTestId("tick-unverified")).toBeVisible()
    for (const id of reviewIds) await approveReview(page, id)
    await page.reload()
  })

  await test.step("the home's page shows the per-parameter averages and the review", async () => {
    const breakdown = page.getByTestId("home-breakdown").filter({ visible: true })
    await expect(breakdown).toBeVisible()
    for (const criterion of Object.keys(HOME))
      await expect(breakdown.getByTestId(`home-breakdown-${criterion}`)).toContainText(/\d\.\d/)
    await expect(breakdown.getByTestId("home-breakdown-recommend")).toContainText(
      /% would recommend/,
    )
    const mine = page.getByTestId("property-review").filter({ hasText: pros })
    await expect(mine).toContainText("Would recommend")
    await expect(mine).toContainText(cons)
    await expect(mine.getByTestId("tick-verified")).toBeVisible()
    await breakdown.scrollIntoViewIfNeeded()
    await shot(page, testInfo, "07-home-averages")
  })

  await test.step("the landlord's page shows the landlord averages", async () => {
    await page.getByTestId("landlord-profile-link").filter({ visible: true }).click()
    await expect(page).toHaveURL(/\/u\//)
    const breakdown = page.getByTestId("landlord-breakdown")
    for (const criterion of Object.keys(LANDLORD))
      await expect(breakdown.getByTestId(`landlord-breakdown-${criterion}`)).toContainText(/\d\.\d/)
    await expect(
      page.getByTestId("property-review").filter({ hasText: "Would recommend" }).first(),
    ).toBeVisible()
    await shot(page, testInfo, "08-landlord-averages")
  })

  // No horizontal scroll at 390 px on the form's last page.
  const width = await page.evaluate(() => document.documentElement.scrollWidth)
  expect(width).toBeLessThanOrEqual(390)
})
