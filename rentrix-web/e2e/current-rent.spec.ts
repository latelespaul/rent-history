import { expect, test } from "@playwright/test"
import { devLogin } from "./support"

// Click-test bug (b): the tenancy card said "Since <tenancy start> Rs X / month", but X was the
// rent from a later rent change. The rent now reads "Current rent" with the date it applies from,
// and the tenancy page keeps the starting rent next to it.
//
// Seed: Demo Tenant BLR-1 rents Demo Residency A since 1 Mar 2024 at ₹32,000; the rent went to
// ₹34,000 from 1 Mar 2025.
test("a tenancy shows its current rent with the date it applies from [click-test (b): current rent label]", async ({
  page,
}) => {
  await devLogin(page, "Demo Tenant BLR-1")
  await page.goto("/me/tenancies")
  await page.getByRole("tab", { name: /^Current/ }).click()
  const card = page
    .getByRole("tabpanel")
    .getByRole("article")
    .filter({ hasText: "Demo Residency A" })

  await expect(card.getByText("Since 1 Mar 2024")).toBeVisible()
  await expect(card.getByText(/^Current rent ₹34,000 \/ month from 1 Mar 2025$/)).toBeVisible()

  await card.getByRole("link", { name: "Open" }).click()
  const facts = page.getByRole("region", { name: "Tenancy" })
  await expect(facts.getByText(/^Current rent ₹34,000 \/ month from 1 Mar 2025$/)).toBeVisible()
  await expect(facts.getByText("₹32,000 / month")).toBeVisible()
})
