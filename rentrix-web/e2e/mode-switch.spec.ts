import { expect, test, type Page } from "@playwright/test"
import { devLogin } from "./support"

/**
 * PLAN R10: one account, both sides. The header's Tenant | Owner switch on a 390 px phone flips
 * the views and is remembered per user, across a reload and a new sign-in. Read-only: it changes
 * no seed data, so it runs once, in the mobile project.
 */
test.skip(({ isMobile }) => !isMobile, "the 390 px header is the case under test (mobile project)")

const modeButton = (page: Page, name: "Tenant" | "Owner") =>
  page.getByRole("banner").getByRole("group", { name: "Mode" }).getByRole("button", { name })

async function expectNoSideScroll(page: Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  )
  expect(overflow).toBeLessThanOrEqual(0)
}

test("a tenant switches to Owner mode, and the choice survives a reload and a new sign-in", async ({
  page,
}) => {
  await devLogin(page, "Demo Tenant BLR-1")

  await test.step("Tenant mode is the default and lists the tenant side", async () => {
    await expect(modeButton(page, "Tenant")).toHaveAttribute("aria-pressed", "true")
    await page.goto("/me/tenancies")
    await expect(page.getByRole("heading", { name: "My tenancies" })).toBeVisible()
    await expect(page.getByTestId("tenant-reviews-written")).toBeVisible()
    await expect(page.getByTestId("add-home-button")).toHaveAttribute("href", "/homes/new")
    await expectNoSideScroll(page)
  })

  await test.step("Owner mode opens the owner home", async () => {
    await modeButton(page, "Owner").click()
    await expect(page).toHaveURL(/\/me\/dashboard$/)
    await expect(modeButton(page, "Owner")).toHaveAttribute("aria-pressed", "true")
    await expect(page.getByText("No homes you own yet")).toBeVisible()
    await expect(page.getByTestId("owner-add-home")).toHaveAttribute("href", "/homes/new?own=1")
    await expect(page.getByTestId("add-home-button")).toHaveAttribute("href", "/homes/new?own=1")
    await expectNoSideScroll(page)
  })

  await test.step("a reload keeps Owner mode", async () => {
    await page.reload()
    await expect(modeButton(page, "Owner")).toHaveAttribute("aria-pressed", "true")
    await expect(page.getByText("No homes you own yet")).toBeVisible()
  })

  await test.step("sign out and back in lands on the Owner home", async () => {
    await page.goto("/settings")
    await page.getByRole("button", { name: "Sign out" }).click()
    await expect(page).toHaveURL(/\/$/)
    await devLogin(page, "Demo Tenant BLR-1")
    await expect(page).toHaveURL(/\/me\/dashboard$/)
    await expect(modeButton(page, "Owner")).toHaveAttribute("aria-pressed", "true")
  })

  await test.step("back to Tenant mode", async () => {
    await modeButton(page, "Tenant").click()
    await expect(page).toHaveURL(/\/me\/tenancies$/)
    await expect(modeButton(page, "Tenant")).toHaveAttribute("aria-pressed", "true")
  })
})

test("a landlord sees their homes, tenancies and reviews in Owner mode", async ({ page }) => {
  await devLogin(page, "Demo Landlord BLR-1")
  await page.goto("/me/dashboard")

  // An owner-only account starts in Owner mode.
  await expect(modeButton(page, "Owner")).toHaveAttribute("aria-pressed", "true")
  await expect(page.getByRole("heading", { name: "My properties" })).toBeVisible()
  await expect(page.getByTestId("owner-tenancies")).toBeVisible()
  await expect(page.getByTestId("owner-reviews-received")).toBeVisible()
  await expect(page.getByTestId("owner-reviews-written")).toBeVisible()
  await expectNoSideScroll(page)

  await modeButton(page, "Tenant").click()
  await expect(page).toHaveURL(/\/me\/tenancies$/)
  await expect(page.getByText("Homes you rent or rented, and your reviews of them.")).toBeVisible()
  await expect(page.getByTestId("tenant-reviews-written")).toBeVisible()
})
