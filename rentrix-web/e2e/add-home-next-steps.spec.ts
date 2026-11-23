import { expect, test } from "@playwright/test"
import { daysAgo, freshLogin, verifyPhone } from "./support"

/**
 * After "Save my home" the flow goes on (captain 2026-11-23 14:53): "Home added. 1 optional step
 * left", rate and review the home; the rental agreement card below it is a "Coming soon"
 * placeholder until PR #62 and is not counted. The review is skippable and stays on My tenancies
 * and the home's page until done.
 */
test("the next steps after adding a home: skip both, then review from My tenancies", async ({
  page,
}, testInfo) => {
  test.setTimeout(testInfo.timeout * 2)
  const tag = `${Date.now().toString(36)}${testInfo.project.name[0]}`
  const building = `Next Steps Court ${tag}`
  const phone = `${testInfo.project.name === "mobile" ? 8 : 9}${Date.now().toString().slice(-9)}`
  const review =
    "Quiet street and a fair landlord. The water supply was steady through every summer month."

  await freshLogin(page)
  await page.locator("label").filter({ hasText: "I rent a home" }).click()
  await page.getByRole("checkbox", { name: /I accept the Terms/ }).check()
  await page.getByRole("button", { name: "Continue" }).click()
  await page.getByTestId("welcome-verify").click()
  await verifyPhone(page, phone)
  await expect(page).toHaveURL(/\/homes\/new$/)

  await test.step("add a home, then the next-steps screen", async () => {
    await page.locator("#ah-city").selectOption({ label: "Pune" })
    await page.locator("#pf-area").fill("Baner")
    await page
      .getByRole("list", { name: "Areas" })
      .getByRole("button", { name: /^Baner/ })
      .click()
    await page.locator("#ah-building").fill(building)
    await page.getByLabel("Flat / house no.").fill(`N-${tag.slice(-3)}`)
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
    await expect(done.getByTestId("add-home-steps-left")).toHaveText(
      /home added\. 1 optional step left/i,
    )
    await expect(done.getByTestId("next-step-review")).toBeVisible()
    await expect(done.getByTestId("next-step-agreement")).toContainText(/coming soon/i)
    await page.screenshot({ path: testInfo.outputPath("next-steps-saved.png"), fullPage: true })
    await done.getByTestId("add-home-skip").click()
  })

  await test.step("skipped: both prompts stay on My tenancies and the home's page", async () => {
    await expect(page).toHaveURL(/\/me\/tenancies$/)
    const mine = page.getByTestId("my-next-steps")
    await expect(mine.getByText(building)).toBeVisible()
    await expect(mine.getByTestId("next-step-review")).toBeVisible()
    await expect(mine.getByTestId("next-step-agreement")).toBeVisible()
    await page.screenshot({
      path: testInfo.outputPath("next-steps-my-tenancies.png"),
      fullPage: true,
    })
    await mine.getByRole("link", { name: building }).click()
    const onPage = page.getByTestId("home-next-steps")
    await expect(onPage).toContainText(/1 optional step left/i)
    await expect(onPage.getByTestId("next-step-agreement")).toBeVisible()
  })

  await test.step("review it: no next-steps prompt remains", async () => {
    await page.getByTestId("home-next-steps").getByTestId("add-home-write-review").click()
    await page.getByTestId("review-star-4").click()
    await page.getByLabel(/your review/i).fill(review)
    await page.getByTestId("review-submit").click()
    await page.getByTestId("review-submitted").getByTestId("review-view-home").click()
    await expect(page.getByTestId("property-review").filter({ hasText: review })).toBeVisible()
    await expect(page.getByTestId("home-next-steps")).toHaveCount(0)
    await page.screenshot({
      path: testInfo.outputPath("next-steps-after-review.png"),
      fullPage: true,
    })
  })
})
