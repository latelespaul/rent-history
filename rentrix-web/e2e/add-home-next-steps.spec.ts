import { expect, test } from "@playwright/test"
import { daysAgo, freshLogin, verifyPhone, testPhone } from "./support"

/** Open model (firstmate 2026-11-23): a home left more than 2 years ago can still be reviewed. */
test("a home left years ago can be reviewed from the next-steps screen", async ({
  page,
}, testInfo) => {
  test.setTimeout(testInfo.timeout * 2)
  const tag = `${Date.now().toString(36)}${testInfo.project.name[0]}`
  const building = `Old Steps Court ${tag}`
  const phone = testPhone()
  const review =
    "A calm lane and a landlord who fixed things quickly. The water supply was steady all year."

  await freshLogin(page)
  await page.locator("label").filter({ hasText: "I rent a home" }).click()
  await page.getByRole("checkbox", { name: /I accept the Terms/ }).check()
  await page.getByRole("button", { name: "Continue" }).click()
  await page.getByTestId("welcome-verify").click()
  await verifyPhone(page, phone)
  await expect(page).toHaveURL(/\/homes\/new$/)

  await page.locator("#ah-city").selectOption({ label: "Pune" })
  await page.locator("#pf-area").fill("Baner")
  await page
    .getByRole("list", { name: "Areas" })
    .getByRole("button", { name: /^Baner/ })
    .click()
  await page.locator("#ah-building").fill(building)
  await page.getByLabel("Flat / house no.").fill(`O-${tag.slice(-3)}`)
  await page.getByTestId("ah-next").click()
  const step2 = page.getByTestId("add-home-step").filter({ hasText: /step 2 of 4/i })
  await expect(step2.or(page.getByTestId("home-matches"))).toBeVisible()
  if (await page.getByTestId("home-matches").isVisible())
    await page.getByTestId("home-match-none").click()
  await page.getByTestId("ah-relation-rent").click()
  await page.getByTestId("ah-next").click()
  await page.getByLabel("Moved in").fill(daysAgo(365 * 3))
  await page.locator("#ah-still").uncheck()
  await page.getByLabel("Moved out").fill(daysAgo(365 * 2 + 30))
  await page.getByTestId("ah-next").click()
  await page.getByTestId("ah-save").click()

  const done = page.getByTestId("add-home-done")
  await expect(done.getByTestId("steps-left")).toHaveText("Home added. 2 optional steps left")
  await done.getByTestId("next-step-review").click()
  await page.getByTestId("review-star-4").click()
  await page.getByLabel(/your review/i).fill(review)
  await page.getByTestId("review-submit").click()
  await page.getByTestId("review-submitted").getByTestId("review-view-home").click()
  await expect(page.getByTestId("property-review").filter({ hasText: review })).toBeVisible()
  const here = page.getByTestId("your-tenancy-here")
  await expect(here.getByTestId("review-status")).toHaveText("You reviewed this home")
  await expect(here.getByTestId("next-step-review")).toHaveCount(0)
  await expect(here.getByTestId("steps-left")).toHaveText("1 optional step left")
})
