import { expect, test } from "@playwright/test"
import { daysAgo, devLogin } from "./support"

// E2E-notes bug: a tenant whose home is not listed yet cannot record the tenancy (the request
// form needs a listed home). Queued for a fix crew; flip this to `test` when that fix merges.
test.fixme("a tenant adds a home that is not listed and records the tenancy [E2E notes: unlisted home; queued fix]", async ({
  page,
}) => {
  await devLogin(page, "Demo Tenant BLR-2")
  await page.goto("/tenancies/new")
  await page.getByRole("link", { name: /not listed|add (the|your) home/i }).click()

  const building = `E2E Unlisted ${Date.now().toString(36)}`
  await page.getByLabel("City").selectOption({ label: "Bangalore" })
  await page.getByLabel("PIN code").fill("560102")
  await page.getByLabel(/Building name/).fill(building)
  await page.getByLabel("Start date").fill(daysAgo(30))
  await page.getByLabel("Monthly rent (₹)").fill("18000")
  await page.getByRole("button", { name: /send|save/i }).click()

  await page.goto("/me/tenancies")
  await page.getByRole("tab", { name: /^Pending/ }).click()
  await expect(
    page.getByRole("tabpanel").getByRole("article").filter({ hasText: building }),
  ).toBeVisible()
})
