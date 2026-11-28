import { mkdirSync } from "node:fs"
import path from "node:path"
import { expect, request, test, type Page, type TestInfo } from "@playwright/test"
import {
  apiBaseUrl,
  devLogin,
  recordNewUsers,
  testPhone,
  verifyPhone,
  writeHomeReview,
} from "./support"

/**
 * Delete my account (captain 2026-11-23 14:49; D-58, D-157 – D-160) at 390 px: a new tenant adds
 * a home and reviews it, then deletes the account from Settings. The old session is refused, the
 * home's page shows the review by "Deleted user", and signing up again with the same Google
 * account (email; the keyed fresh-user dev login stands in for Google) and the same phone starts a
 * new, empty account.
 *
 * One screenshot per step goes to the test output, and to `DELETE_ACCOUNT_SHOTS` when set
 * (`rentrix-docs/screenshots/delete-account/` for the PR).
 */

async function shot(page: Page, testInfo: TestInfo, name: string) {
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })
  const out = process.env.DELETE_ACCOUNT_SHOTS
  if (out) {
    mkdirSync(out, { recursive: true })
    await page.screenshot({ path: path.join(out, `${name}.png`), fullPage: true })
  }
}

/** Picks the tenant role and leaves the account-created screen by its verify-phone button. */
async function tenantRoleThenVerify(page: Page, testInfo: TestInfo, prefix: string) {
  await expect(page).toHaveURL(/\/welcome/, { timeout: 30_000 })
  await page.locator("label").filter({ hasText: "I rent a home" }).click()
  await page.getByRole("checkbox", { name: /I accept the Terms/ }).check()
  await page.getByRole("button", { name: "Continue" }).click()
  await expect(page.getByText("Your account is created.")).toBeVisible()
  await shot(page, testInfo, `${prefix}-account-created`)
  await page.getByTestId("welcome-verify").click()
}

test.describe("delete my account (390 px)", () => {
  test.skip(({ viewport }) => (viewport?.width ?? 0) > 390, "runs in the 390 px mobile project")

  test("delete, old session refused, Deleted user on the home, sign up again fresh", async ({
    page,
    browser,
  }, testInfo) => {
    test.setTimeout(testInfo.timeout * 3)
    const tag = Date.now().toString(36)
    const name = `Deleter ${tag.slice(-4).toUpperCase()} Rao`
    const building = `Erase Towers ${tag}`
    const phone = testPhone()
    const review =
      "Bright rooms and a fair owner. The society kept the water and power steady all year round."
    let key = ""
    let email = ""
    let bearer = ""
    let homeUrl = ""

    recordNewUsers(page)
    page.on("response", async (response) => {
      if (!/\/auth\/dev-login\/new$/.test(new URL(response.url()).pathname) || !response.ok())
        return
      const body = (await response.json().catch(() => null)) as { user?: { email?: string } } | null
      email = body?.user?.email ?? email
    })
    page.on("request", (req) => {
      const auth = req.headers()["authorization"]
      if (auth?.startsWith("Bearer ")) bearer = auth
    })

    await test.step("1 sign up, verify the phone, set a name", async () => {
      await page.goto("/login")
      await page
        .getByRole("region", { name: /dev login/i })
        .getByRole("button", { name: /sign in as a new user/i })
        .click()
      await tenantRoleThenVerify(page, testInfo, "01")
      await verifyPhone(page, phone)
      await expect(page).toHaveURL(/\/homes\/new$/)
      expect(email).toMatch(/^new-user-[a-z0-9]+@example\.test$/)
      key = email.slice("new-user-".length, email.indexOf("@"))
      await page.goto("/settings")
      await page.getByLabel("Display name").fill(name)
      await page.getByRole("button", { name: "Save profile" }).click()
      await expect(page.getByRole("status").filter({ hasText: "Saved." })).toBeVisible()
      await shot(page, testInfo, "02-profile")
    })

    await test.step("2 add a home and review it", async () => {
      await page.goto("/homes/new")
      await page.locator("#ah-city").selectOption({ label: "Pune" })
      await page.locator("#pf-area").fill("Baner")
      await page
        .getByRole("listbox", { name: "Areas" })
        .getByRole("option", { name: /^Baner/ })
        .click()
      await page.locator("#ah-building").fill(building)
      await page.getByLabel("Flat / house no.").fill(`D-${tag.slice(-3)}`)
      await page.getByLabel("PIN code").fill("411045")
      await page.getByTestId("ah-next").click()
      const step2 = page.getByTestId("add-home-step").filter({ hasText: /step 2 of 4/i })
      await expect(step2.or(page.getByTestId("home-matches"))).toBeVisible()
      if (await page.getByTestId("home-matches").isVisible())
        await page.getByTestId("home-match-none").click()
      await page.getByTestId("ah-relation-rent").click()
      await page.getByTestId("ah-next").click()
      await page.getByLabel("Moved in").fill("2024-04-01")
      await page.locator("#ah-rent").fill("26000")
      await page.getByTestId("ah-next").click()
      await page.locator("#ah-owner-name").fill("Ramesh Owner")
      await page.getByTestId("ah-save").click()
      await page.getByTestId("add-home-done").getByTestId("add-home-write-review").click()
      await writeHomeReview(page, 4, review)
      await page.getByTestId("review-submit").click()
      await page.getByTestId("review-submitted").getByTestId("review-view-home").click()
      await expect(page).toHaveURL(/\/p\//)
      homeUrl = new URL(page.url()).pathname
      await expect(page.getByTestId("property-review").filter({ hasText: review })).toBeVisible()
      await shot(page, testInfo, "03-home-with-review")
    })

    const cookies = await page.context().cookies()
    const oldBearer = bearer
    expect(oldBearer, "the session's access token was seen").not.toBe("")

    await test.step("3 Settings → Delete my account: the warning, then type DELETE", async () => {
      await page.goto("/settings")
      await page.getByTestId("settings-delete").click()
      await expect(page).toHaveURL(/\/settings\/delete-account$/)
      await expect(page.getByRole("heading", { name: "Delete my account" })).toBeVisible()
      await expect(page.getByTestId("delete-removed")).toContainText("phone number")
      await expect(page.getByTestId("delete-kept")).toContainText("Deleted user")
      await expect(page.getByTestId("delete-submit")).toBeDisabled()
      await shot(page, testInfo, "04-delete-warning")
      await page.getByLabel("Type DELETE to confirm").fill("DELETE")
      await expect(page.getByTestId("delete-submit")).toBeEnabled()
      await shot(page, testInfo, "05-delete-typed")
      await page.getByTestId("delete-submit").click()
      await expect(page.getByText("Your account is deleted.")).toBeVisible()
      await expect(
        page.getByRole("link", { name: "Sign in / Sign up with Google" }).first(),
      ).toBeVisible()
      await shot(page, testInfo, "06-deleted-signed-out")
    })

    await test.step("4 the old session is refused", async () => {
      const apiBase = await apiBaseUrl(page)
      const api = await request.newContext()
      const me = await api.get(`${apiBase}/me`, { headers: { Authorization: oldBearer } })
      expect(me.status()).toBe(401)
      const refreshCookie = cookies.find((c) => c.name === "rentrix_refresh")
      expect(refreshCookie, "the old refresh cookie").toBeTruthy()
      const refreshed = await api.post(`${apiBase}/auth/refresh`, {
        headers: { Cookie: `rentrix_refresh=${refreshCookie!.value}` },
      })
      expect(refreshed.status()).toBe(401)
      await api.dispose()
      // A browser that still holds the old cookies is signed out too.
      const old = await browser.newContext({ viewport: { width: 390, height: 844 } })
      await old.addCookies(cookies)
      const oldPage = await old.newPage()
      await oldPage.goto("/settings")
      await expect(oldPage).toHaveURL(/\/login/, { timeout: 30_000 })
      await shot(oldPage, testInfo, "07-old-session-refused")
      await old.close()
    })

    await test.step("5 the home's page shows the review by Deleted user", async () => {
      const viewer = await browser.newContext({ viewport: { width: 390, height: 844 } })
      const viewerPage = await viewer.newPage()
      await devLogin(viewerPage, "Demo Tenant PNQ-1")
      await viewerPage.goto(homeUrl)
      await expect(viewerPage.getByRole("heading", { level: 1 })).toContainText(building)
      const card = viewerPage.getByTestId("property-review").filter({ hasText: review })
      await expect(card).toBeVisible()
      await expect(card).toContainText("Deleted user")
      await expect(viewerPage.getByText(name)).toHaveCount(0)
      await expect(viewerPage.getByText(name.split(" ")[0] + " R.")).toHaveCount(0)
      await shot(viewerPage, testInfo, "08-home-deleted-user")
      await viewer.close()
    })

    await test.step("6 signing up again with the same email and phone starts fresh", async () => {
      const again = await browser.newContext({ viewport: { width: 390, height: 844 } })
      const fresh = await again.newPage()
      recordNewUsers(fresh)
      await fresh.goto("/")
      const apiBase = await apiBaseUrl(fresh)
      const signedUp = await fresh.request.post(`${apiBase}/auth/dev-login/new`, { data: { key } })
      expect(signedUp.ok()).toBe(true)
      const body = (await signedUp.json()) as { user: { email: string; needsOnboarding: boolean } }
      expect(body.user.email).toBe(email)
      expect(body.user.needsOnboarding).toBe(true)
      await fresh.goto("/welcome")
      await tenantRoleThenVerify(fresh, testInfo, "09")
      await verifyPhone(fresh, phone)
      await expect(fresh).toHaveURL(/\/homes\/new$/)
      await fresh.goto("/settings")
      await expect(fresh.getByLabel("Display name")).not.toHaveValue(name)
      await expect(fresh.getByText(email)).toBeVisible()
      await expect(fresh.getByText("Verified", { exact: true }).first()).toBeVisible()
      await shot(fresh, testInfo, "10-fresh-settings")
      await fresh.goto("/me/tenancies")
      await expect(fresh.getByText(building)).toHaveCount(0)
      await shot(fresh, testInfo, "11-fresh-no-tenancies")
      await again.close()
    })
  })
})
