import { expect, test } from "@playwright/test"
import { daysAgo, devLogin } from "./support"

// Click-test bug (a): the invite form showed the raw 422 "must not be null" under Start date and
// a generic "invalid fields" for a missing rent. Every problem now reads in plain words next to
// its field, and the rent is required before anything is sent.
test("the invite form names each missing or wrong field in plain words [click-test (a): invite form errors]", async ({
  page,
}) => {
  await devLogin(page, "Demo Landlord BLR-1")
  await page.goto("/tenancies/new")
  const form = page.getByRole("form", { name: "Tenancy request" })
  const send = form.getByRole("button", { name: "Send invite" })
  const raw = form.getByText(/must not be null|invalid fields|well-formed/i)

  await expect(form.getByLabel(/Monthly rent/)).toHaveAttribute("aria-required", "true")

  await test.step("nothing filled in", async () => {
    await send.click()
    await expect(form.getByText("Enter the tenant's email address.")).toBeVisible()
    await expect(form.getByText("Enter the start date.")).toBeVisible()
    await expect(form.getByText("Enter the monthly rent.")).toBeVisible()
    await expect(raw).toHaveCount(0)
  })

  await test.step("only the rent missing, then not a number", async () => {
    await form.getByLabel("Tenant email").fill("new.tenant@example.test")
    await form.getByLabel("Start date").fill(daysAgo(3))
    await send.click()
    await expect(form.getByText("Enter the monthly rent.")).toBeVisible()
    await expect(form.getByText("Enter the start date.")).toHaveCount(0)
    await form.getByLabel(/Monthly rent/).fill("twenty thousand")
    await send.click()
    await expect(form.getByText(/whole number of rupees/)).toBeVisible()
    await expect(page).toHaveURL(/\/tenancies\/new/)
  })

  await test.step("a field the API refuses reads in plain words too", async () => {
    await form.getByLabel(/Monthly rent/).fill("21000")
    await form.getByLabel("Tenant email").fill("not-an-email")
    await send.click()
    await expect(form.getByText(/Enter a valid email address/)).toBeVisible()
    await expect(raw).toHaveCount(0)
    await expect(page).toHaveURL(/\/tenancies\/new/)
  })
})
