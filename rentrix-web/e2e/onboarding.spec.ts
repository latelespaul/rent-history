import { expect, test } from "@playwright/test"
import { freshLogin, pickRole } from "./support"

test("a fresh account picks a role on first sign-in and enters the app", async ({ page }) => {
  await freshLogin(page)
  await pickRole(page, "I rent a home")
  await expect(page).toHaveURL(/\/me\/tenancies$/)
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible()
})

// Click-test bug: after the role pick the verification steps were a dead end and never led
// back to what the user had started. The sign-in crew is fixing it (design v4, #38); flip this
// to `test` when that fix merges.
test.fixme("onboarding runs role pick and verification, then returns to the started action [click-test verification dead-end; sign-in crew fm/rxm-onboarding-v4]", async ({
  page,
}) => {
  // Start an action that needs an account: record a tenancy on a listed home.
  await freshLogin(page, "/search")
  await pickRole(page, "I rent a home")
  await expect(page).toHaveURL(/\/search/)
  await page.getByRole("search").getByRole("combobox", { name: "Locality" }).fill("Koramangala")
  await page.getByRole("search").getByRole("button", { name: "Search" }).click()
  await page.getByRole("link", { name: "Demo Greens · Tower 1 · Wing A", exact: true }).click()
  const started = page.url()
  await page.getByRole("link", { name: /I rent or rented this home/ }).click()

  // The verification steps (phone code in dev), then back to the started action.
  await expect(page.getByRole("heading", { name: /verify/i })).toBeVisible()
  await page.getByRole("button", { name: /continue|done/i }).click()
  await expect(page).toHaveURL(/\/tenancies\/new\?property=/)
  expect(started).toMatch(/\/p\//)
})
