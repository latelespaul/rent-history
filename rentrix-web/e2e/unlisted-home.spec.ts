import { expect, test } from "@playwright/test"
import { devLogin } from "./support"

// E2E-notes bug B3: a tenant whose home is not listed had no way to record the tenancy. The
// "+ Add your home" flow (D-139) now takes them there; new-user.spec.ts walks the whole flow.
test("a tenant whose home is not listed is sent to Add your home [E2E notes: unlisted home]", async ({
  page,
}) => {
  await devLogin(page, "Demo Tenant BLR-2")
  await page.goto("/tenancies/new")
  await page
    .getByRole("link", { name: /add your home/i })
    .first()
    .click()
  await expect(page).toHaveURL(/\/homes\/new$/)
  await expect(page.getByTestId("add-home-step")).toHaveText(/step 1 of 4 · where is it/i)
})
