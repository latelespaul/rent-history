import { expect, test, type TestInfo } from "@playwright/test"
import { devLogin } from "./support"

/**
 * The admin console lives on its own host (SYSTEM_DESIGN §13.3): admin.<main host>, e.g.
 * http://admin.localhost:8081 next to the e2e web at http://localhost:8081 (Chromium resolves
 * *.localhost to loopback). `E2E_ADMIN_BASE_URL` overrides it.
 */
function adminBase(testInfo: TestInfo): string {
  if (process.env.E2E_ADMIN_BASE_URL) return process.env.E2E_ADMIN_BASE_URL.replace(/\/+$/, "")
  const main = new URL(String(testInfo.project.use.baseURL))
  main.hostname = `admin.${main.hostname}`
  return main.origin
}

test("the main site never shows, offers or serves the admin console", async ({ page }) => {
  await page.goto("/login")
  const panel = page.getByRole("region", { name: /dev login/i })
  await expect(panel.getByRole("radio").first()).toBeVisible()
  await expect(panel.getByRole("radio", { name: /^Demo Admin\b/ })).toHaveCount(0)
  await expect(panel.getByText(/^Staff/)).toHaveCount(0)

  await devLogin(page, "Demo Tenant BLR-1")
  const banner = page.getByRole("banner")
  await expect(banner.getByRole("button", { name: /^Account menu for/ })).toBeVisible()
  await expect(banner.getByText("Admin", { exact: true })).toHaveCount(0)

  await page.goto("/admin")
  await expect(page.getByText("Page not found")).toBeVisible()

  // The API refuses admin endpoints from the main site whatever the caller holds.
  const status = await page.evaluate(async () => {
    const config = (window as { __RENTRIX_CONFIG__?: { apiBaseUrl?: string } }).__RENTRIX_CONFIG__
    const base = config?.apiBaseUrl || "/api/v1"
    return (await fetch(`${base}/admin/sms-relay`, { credentials: "include" })).status
  })
  expect(status).toBe(404)
})

test("the admin host signs in the seed admin and opens the OTP relay", async ({
  page,
}, testInfo) => {
  const admin = adminBase(testInfo)
  await page.goto(`${admin}/`)
  await expect(page).toHaveURL(/\/login/)
  await expect(page.getByRole("heading", { name: /doorway living admin/i })).toBeVisible()
  await testInfo.attach("admin-sign-in", {
    body: await page.screenshot({ fullPage: true }),
    contentType: "image/png",
  })

  const dev = page.getByRole("region", { name: /dev login · seed admins/i })
  // Only seed admins are offered here, never tenants or landlords.
  await expect(dev.getByRole("button", { name: /^Sign in as Demo Tenant/ })).toHaveCount(0)
  await dev.getByRole("button", { name: "Sign in as Demo Admin" }).click()

  await expect(page).toHaveURL(/\/sms-relay$/, { timeout: 30_000 })
  await expect(page.getByRole("heading", { name: "OTP relay" })).toBeVisible()
  // The docker e2e stack sends codes to the API log (SMS_BACKEND=log), so the relay says it is off;
  // a stack with SMS_BACKEND=admin-relay lists its codes instead.
  await expect(
    page
      .getByText(/the otp relay is off|no codes waiting/i)
      .or(page.getByRole("button", { name: /^Copy code/ }))
      .first(),
  ).toBeVisible()
  await expect(page.getByRole("button", { name: /sign out/i })).toBeVisible()

  // The admin session survives a reload (its own refresh cookie) and ends on sign out.
  await page.reload()
  await expect(page.getByRole("heading", { name: "OTP relay" })).toBeVisible()
  await page.getByRole("button", { name: /sign out/i }).click()
  await expect(page).toHaveURL(/\/login/)
})
