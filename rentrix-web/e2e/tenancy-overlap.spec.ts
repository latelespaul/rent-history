import { expect, test, type Page } from "@playwright/test"
import { daysAgo, devLogin } from "./support"

/**
 * Click-test bug: a whole home could get a second active tenancy (fixed in #42).
 *
 * A landlord adds a property and submits it for review. Listings go public only after an admin
 * approves them, and the admin console has no listing approval yet, so the invite then runs on
 * one of the landlord's seeded published homes: its current tenant moves out, the landlord
 * invites a new tenant, the tenant confirms, and a second overlapping invite is refused.
 *
 * Each project uses its own seed landlord, home and tenants, so the desktop and mobile runs do
 * not meet each other's tenancies on the one shared database.
 */
const CAST = {
  desktop: {
    landlord: "Demo Landlord BLR-2",
    home: "Demo Studio D",
    tenant: "Demo Tenant BLR-1",
    tenantEmail: "demo-tenant-blr-1@example.test",
    otherTenantEmail: "demo-tenant-blr-2@example.test",
    city: "Bangalore",
    pincode: "560038",
    area: "Indiranagar",
  },
  mobile: {
    landlord: "Demo Landlord PNQ-1",
    home: "Demo Park F",
    tenant: "Demo Tenant PNQ-1",
    tenantEmail: "demo-tenant-pnq-1@example.test",
    otherTenantEmail: "demo-tenant-pnq-3@example.test",
    city: "Pune",
    pincode: "411057",
    area: "Hinjewadi",
  },
} as const

async function openTenancy(page: Page, home: string, tab: RegExp) {
  await page.goto("/me/tenancies")
  await page.getByRole("tab", { name: tab }).click()
  const card = page.getByRole("tabpanel").getByRole("article").filter({ hasText: home })
  await card.getByRole("link", { name: "Open" }).click()
  await expect(page.getByRole("heading", { level: 1, name: new RegExp(home) })).toBeVisible()
}

async function invite(page: Page, home: string, email: string, startDate: string) {
  await page.goto("/tenancies/new")
  const form = page.getByRole("form", { name: "Tenancy request" })
  const property = form.getByLabel("Property")
  const value = await property.locator("option", { hasText: home }).getAttribute("value")
  await property.selectOption(value ?? "")
  await form.getByLabel("Tenant email").fill(email)
  await form.getByLabel("Start date").fill(startDate)
  await form.getByLabel("Monthly rent (₹)").fill("21000")
  await form.getByRole("button", { name: "Send invite" }).click()
  return form
}

/** Signs out by dropping the refresh cookie (the access token lives in page memory only). */
async function switchUser(page: Page, name: string) {
  await page.context().clearCookies()
  await devLogin(page, name)
}

test("landlord adds a home, invites a tenant who confirms, and a second tenancy is refused", async ({
  page,
}, testInfo) => {
  const cast = CAST[testInfo.project.name as keyof typeof CAST] ?? CAST.desktop
  const building = `E2E Home ${Date.now().toString(36)}`

  await test.step("landlord adds a property and submits it for review", async () => {
    await devLogin(page, cast.landlord)
    await page.goto("/me/properties/new")
    const form = page.getByRole("form", { name: "Property" })
    await form.getByLabel("City").selectOption({ label: cast.city })
    await form.getByLabel("PIN code").fill(cast.pincode)
    await form
      .getByRole("list", { name: "Areas" })
      .getByRole("button", { name: new RegExp(`^${cast.area}`) })
      .click()
    await form.getByLabel(/Building name/).fill(building)
    await form.getByRole("button", { name: "Save as draft" }).click()
    await expect(page).toHaveURL(/\/edit\?saved=1$/)
    const submit = page.getByRole("button", { name: "Submit for review" })
    await submit.click()
    // The button goes once the submit is saved; reloading earlier races the write (SQLITE_BUSY).
    await expect(submit).toHaveCount(0)

    await page.goto("/me/properties")
    const row = page
      .getByRole("list", { name: "My properties" })
      .getByRole("listitem")
      .filter({ hasText: building })
    await expect(row.getByText("Pending review")).toBeVisible()
  })

  await test.step("the current tenant moves out", async () => {
    await openTenancy(page, cast.home, /^Current/)
    await page.getByRole("button", { name: "End tenancy" }).click()
    await page.getByLabel("Last day").fill(daysAgo(10))
    await page.getByRole("button", { name: "End tenancy" }).last().click()
    await expect(page.getByRole("heading", { level: 1 }).getByText("Ended")).toBeVisible()
  })

  await test.step("landlord invites a new tenant", async () => {
    await invite(page, cast.home, cast.tenantEmail, daysAgo(5))
    await expect(page).toHaveURL(/\/tenancies\/[^/?]+$/)
    await expect(
      page.getByRole("heading", { level: 1 }).getByText("Waiting for confirmation"),
    ).toBeVisible()
  })

  await test.step("the tenant confirms", async () => {
    await switchUser(page, cast.tenant)
    await page.goto("/me/tenancies")
    const request = page
      .getByRole("region", { name: /needs your action/i })
      .getByRole("article")
      .filter({ hasText: cast.home })
    await request.getByRole("button", { name: "Confirm tenancy" }).click()
    await expect(request).toHaveCount(0)
    await openTenancy(page, cast.home, /^Current/)
    await expect(page.getByRole("heading", { level: 1 }).getByText("Active")).toBeVisible()
  })

  await test.step("a second tenancy on the same dates is refused", async () => {
    await switchUser(page, cast.landlord)
    const form = await invite(page, cast.home, cast.otherTenantEmail, daysAgo(1))
    await expect(form.getByText(/this home already has a tenancy/i).first()).toBeVisible()
    await expect(page).toHaveURL(/\/tenancies\/new/)
  })
})
