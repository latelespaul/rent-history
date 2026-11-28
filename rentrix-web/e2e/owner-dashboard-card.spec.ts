import { expect, test } from "@playwright/test"
import { freshLogin, pickRole } from "./support"

/**
 * The owner dashboard's property card at 390 px (stabilization pass, captain 2026-11-24): the
 * home's name, flat and address use the card's full width, with the rating line under them. The
 * rating line ("No verified reviews yet") used to take the right half and squeeze the details into
 * a narrow left column.
 */
test("the owner's property card uses the full width at 390 px", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "the 390 px phone layout")
  const building = `Owner Card ${Date.now().toString(36)}`
  await freshLogin(page)
  await pickRole(page, "I rent out a property")
  await expect(page).toHaveURL(/\/me\/dashboard$/)

  await page.getByRole("link", { name: "Add property" }).first().click()
  const form = page.getByRole("form", { name: "Property" })
  await form.getByLabel("City").selectOption({ label: "Bangalore" })
  await form.getByLabel("PIN code").fill("560034")
  await form.locator("#pf-area").fill("Koramangala")
  await form
    .getByRole("listbox", { name: "Areas" })
    .getByRole("option", { name: /^Koramangala/ })
    .click()
  await form.getByLabel(/Building name/).fill(building)
  await form.getByRole("button", { name: "Save as draft" }).click()
  await expect(page).toHaveURL(/\/me\/properties\/[^/]+\/edit\?saved=1$/)

  await page.goto("/me/dashboard")
  const card = page.getByTestId("owner-property").filter({ hasText: building })
  await expect(card).toBeVisible()
  const layout = await card.evaluate((li) => {
    const inner = li.getBoundingClientRect().width
    const details = li
      .querySelector("[data-testid=owner-property-details]")!
      .getBoundingClientRect()
    const rating = li.querySelector("[data-testid=owner-property-rating]")!.getBoundingClientRect()
    return { inner, details: details.width, detailsBottom: details.bottom, ratingTop: rating.top }
  })
  // The details take (nearly) the whole card; the rating line sits below them, not beside them.
  expect(layout.details).toBeGreaterThan(layout.inner * 0.8)
  expect(layout.ratingTop).toBeGreaterThanOrEqual(layout.detailsBottom - 1)
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  )
  expect(overflow).toBeLessThanOrEqual(0)
  await testInfo.attach("owner-property-card-390", {
    body: await card.screenshot(),
    contentType: "image/png",
  })
})
