import { expect, test } from "@playwright/test"
import { devLogin } from "./support"

// Captain 2026-11-28: "whenever I click on Record a tenancy after signing in it directs me to the
// search page". The button now opens "Which home did you rent?": search, pick a home, then the
// tenancy form for that home. Read-only: nothing is sent, so the seed stays as it was.
test("a signed-in tenant records a tenancy by picking the home first [captain 2026-11-28: Record a tenancy]", async ({
  page,
}) => {
  await devLogin(page, "Demo Tenant BLR-1")
  await page.goto("/me/tenancies")

  await test.step("Record a tenancy opens the pick-a-home step, not search", async () => {
    await page.getByRole("link", { name: /record a tenancy/i }).click()
    await expect(page).toHaveURL(/\/tenancies\/new\?as=tenant$/)
    await expect(page.getByRole("heading", { name: "Which home did you rent?" })).toBeVisible()
    await expect(page.getByTestId("pick-add-home")).toHaveAttribute("href", "/homes/new")
  })

  await test.step("search and pick a home", async () => {
    await page.getByLabel("Area, society or building").fill("Koramangala")
    await page.getByRole("button", { name: "Search", exact: true }).click()
    const first = page.getByTestId("pick-home-result").first()
    await expect(first).toBeVisible()
    // the card's place line ("Koramangala, Bangalore"); the form shows the same home's place
    const place = (await first.locator("p").nth(1).textContent())?.trim() ?? ""
    await first.getByRole("link", { name: /^pick /i }).click()
    await expect(page).toHaveURL(/\/tenancies\/new\?property=[^&]+&as=tenant$/)
    await expect(page.getByRole("heading", { name: "Record your tenancy" })).toBeVisible()
    const form = page.getByRole("form", { name: "Tenancy request" })
    await expect(form.getByText(place)).toBeVisible()
    await expect(form.getByRole("button", { name: "Send to landlord" })).toBeEnabled()
  })

  await test.step("Back returns to the same results", async () => {
    await page.goBack()
    await expect(page.getByRole("heading", { name: "Which home did you rent?" })).toBeVisible()
    await expect(page.getByTestId("pick-home-result").first()).toBeVisible()
  })
})
