import { expect, test } from "@playwright/test"
import { freshLogin, pickRole } from "./support"

/**
 * "Add property" (owner dashboard) with the real Bangalore areas (stabilization pass, captain
 * 2026-11-24): a PIN code typed in the form's PIN field narrows the Areas list to that PIN's
 * areas. Before, the list kept the city's first 20 areas A–Z, so Koramangala (560034) was not
 * offered until its name was typed.
 */
test("the PIN code narrows the Areas list on Add property", async ({ page }) => {
  const building = `Pin Areas ${Date.now().toString(36)}`
  await freshLogin(page)
  await pickRole(page, "I rent out a property")
  await expect(page).toHaveURL(/\/me\/dashboard$/)

  await page.getByRole("link", { name: "Add property" }).first().click()
  const form = page.getByRole("form", { name: "Property" })
  await form.getByLabel("City").selectOption({ label: "Bangalore" })
  await form.getByLabel("PIN code").fill("560034")

  const areas = form.getByRole("list", { name: "Areas" })
  await expect(areas.getByRole("button", { name: /^Koramangala/ })).toBeVisible()
  await expect(areas.getByRole("button", { name: /^Agara/ })).toBeVisible()
  // Areas of other PIN codes are gone, e.g. Adugodi (560030), first A–Z in the city.
  await expect(areas.getByRole("button", { name: /^Adugodi/ })).toHaveCount(0)

  await areas.getByRole("button", { name: /^Koramangala/ }).click()
  await form.getByLabel(/Building name/).fill(building)
  await form.getByRole("button", { name: "Save as draft" }).click()
  await expect(page).toHaveURL(/\/me\/properties\/[^/]+\/edit\?saved=1$/)
})

test("a PIN code with no area says so and still lets the user suggest one", async ({ page }) => {
  const area = `Pin Gap ${Date.now().toString(36)}`
  await freshLogin(page)
  await pickRole(page, "I rent out a property")
  await expect(page).toHaveURL(/\/me\/dashboard$/)

  await page.getByRole("link", { name: "Add property" }).first().click()
  const form = page.getByRole("form", { name: "Property" })
  await form.getByLabel("City").selectOption({ label: "Bangalore" })
  // A valid Bangalore-range PIN that no area in the V18 seed carries.
  await form.getByLabel("PIN code").fill("560999")

  const areas = form.getByRole("list", { name: "Areas" })
  await expect(areas.getByText("No area has PIN 560999 yet. Type the area name.")).toBeVisible()
  await expect(areas.getByRole("button")).toHaveCount(0)

  await form.locator("#pf-area").fill(area)
  await expect(areas.getByRole("button", { name: `Suggest new area “${area}”` })).toBeVisible()
  await expect(areas.getByText(/No area has PIN/)).toHaveCount(0)
})
