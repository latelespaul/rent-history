import { mkdirSync } from "node:fs"
import path from "node:path"
import { expect, test, type Page, type TestInfo } from "@playwright/test"
import { apiBaseUrl, recordNewUsers, testPhone, verifyPhone, writeHomeReview } from "./support"

/**
 * Release gate (captain 2026-11-23 13:26): the complete new-user chain at 390 px. Landing, sign
 * up (the fresh-user dev login stands in for Google, which is checked by hand on LIVE), phone
 * verification with the held code accepted, profile, the any-one-method gate, "Add your home"
 * steps 1-4 with the home-size chip, the next-steps screen (#63) and a review written from it, the
 * home's page showing what was entered, then sign out and back in keeping it all.
 *
 * One screenshot per step goes to the test output, and to `SIGNUP_GATE_SHOTS` when set
 * (`rentrix-docs/screenshots/signup-gate/` for the PR).
 */

const GENERIC = "The request has invalid fields."

async function shot(page: Page, testInfo: TestInfo, name: string) {
  await expect(page.getByText(GENERIC)).toHaveCount(0)
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })
  const out = process.env.SIGNUP_GATE_SHOTS
  if (out) {
    mkdirSync(out, { recursive: true })
    await page.screenshot({ path: path.join(out, `${name}.png`), fullPage: true })
  }
}

test.describe("new-user sign-up release gate (390 px)", () => {
  test.skip(({ viewport }) => (viewport?.width ?? 0) > 390, "runs in the 390 px mobile project")

  test("landing to a saved home, kept after signing out and back in", async ({
    page,
  }, testInfo) => {
    test.setTimeout(testInfo.timeout * 3)
    const tag = Date.now().toString(36)
    const name = `Gate Tenant ${tag.slice(-4).toUpperCase()}`
    const building = `Signup Towers ${tag}`
    const flat = `B-${tag.slice(-3)}`
    const phone = testPhone()
    const review =
      "Good light, a fair owner and quick repairs. The society water supply was steady all year."
    let homeUrl = ""

    recordNewUsers(page)
    await test.step("1 landing", async () => {
      await page.goto("/")
      await expect(
        page.getByRole("link", { name: "Sign in / Sign up with Google" }).first(),
      ).toBeVisible()
      await shot(page, testInfo, "01-landing")
      await page.getByRole("link", { name: "Sign in / Sign up with Google" }).first().click()
      await expect(page).toHaveURL(/\/login/)
    })

    await test.step("2 sign up (fresh-user dev login for Google) and pick the role", async () => {
      await shot(page, testInfo, "02-sign-in")
      await page
        .getByRole("region", { name: /dev login/i })
        .getByRole("button", { name: /sign in as a new user/i })
        .click()
      await expect(page).toHaveURL(/\/welcome/, { timeout: 30_000 })
      await page.locator("label").filter({ hasText: "I rent a home" }).click()
      await page.getByRole("checkbox", { name: /I accept the Terms/ }).check()
      await shot(page, testInfo, "03-role")
      await page.getByRole("button", { name: "Continue" }).click()
      await expect(page.getByText("Your account is created.")).toBeVisible()
      await shot(page, testInfo, "04-account-created")
    })

    await test.step("3 phone verification, the held code accepted", async () => {
      await page.getByTestId("welcome-verify").click()
      await verifyPhone(page, phone)
      await expect(page).toHaveURL(/\/homes\/new$/)
      await shot(page, testInfo, "05-phone-verified")
    })

    await test.step("4 profile: display name saved, phone shown verified", async () => {
      await page.goto("/settings")
      await page.getByLabel("Display name").fill(name)
      await page.getByRole("button", { name: "Save profile" }).click()
      await expect(page.getByRole("status").filter({ hasText: "Saved." })).toBeVisible()
      await expect(page.getByText("Verified", { exact: true }).first()).toBeVisible()
      await shot(page, testInfo, "06-profile")
    })

    await test.step("5 the any-one-method gate lets a verified user post", async () => {
      await page.goto("/verify")
      await expect(page.getByText(/verified/i).first()).toBeVisible()
      await shot(page, testInfo, "07-verify-gate")
    })

    await test.step("6 add your home, steps 1-4", async () => {
      await page.goto("/homes/new")
      await page.locator("#ah-city").selectOption({ label: "Pune" })
      await page.locator("#pf-area").fill("Baner")
      await page
        .getByRole("listbox", { name: "Areas" })
        .getByRole("option", { name: /^Baner/ })
        .click()
      await page.locator("#ah-building").fill(building)
      await page.getByLabel("Flat / house no.").fill(flat)
      await page.getByLabel("PIN code").fill("411045")
      await page
        .getByTestId("ah-home-size")
        .getByRole("button", { name: "2 BHK", exact: true })
        .click()
      await shot(page, testInfo, "08-step1-where")
      await page.getByTestId("ah-next").click()
      const step2 = page.getByTestId("add-home-step").filter({ hasText: /step 2 of 4/i })
      await expect(step2.or(page.getByTestId("home-matches"))).toBeVisible()
      if (await page.getByTestId("home-matches").isVisible())
        await page.getByTestId("home-match-none").click()
      await page.getByTestId("ah-relation-rent").click()
      await shot(page, testInfo, "09-step2-role")
      await page.getByTestId("ah-next").click()
      await page.getByLabel("Moved in").fill("2024-04-01")
      await page.locator("#ah-rent").fill("28000")
      await shot(page, testInfo, "10-step3-tenancy")
      await page.getByTestId("ah-next").click()
      await page.locator("#ah-owner-name").fill("Suresh Owner")
      await shot(page, testInfo, "11-step4-owner")
      await page.getByTestId("ah-save").click()
      await expect(page.getByText("Verify your phone to post")).toHaveCount(0)
      const done = page.getByTestId("add-home-done")
      await expect(done.getByTestId("add-home-steps-left")).toHaveText(
        /home added\. 1 optional step left/i,
      )
      await shot(page, testInfo, "12-saved-next-steps")
    })

    await test.step("7 rate and review it from the next-steps screen", async () => {
      await page.getByTestId("add-home-done").getByTestId("add-home-write-review").click()
      await writeHomeReview(page, 4, review)
      await shot(page, testInfo, "13-review")
      await page.getByTestId("review-submit").click()
      await page.getByTestId("review-submitted").getByTestId("review-view-home").click()
    })

    await test.step("8 the home's page shows what was entered and the review", async () => {
      await expect(page).toHaveURL(/\/p\//)
      homeUrl = new URL(page.url()).pathname
      await expect(page.getByRole("heading", { level: 1 })).toContainText(building)
      await expect(page.getByText(/Baner, Pune · 411045/)).toBeVisible()
      await expect(page.getByText(new RegExp(`Flat ${flat}`)).first()).toBeVisible()
      await expect(page.getByText("2 BHK").first()).toBeVisible()
      await expect(page.getByText(/Suresh Owner/)).toHaveCount(0)
      await expect(page.getByTestId("property-review").filter({ hasText: review })).toBeVisible()
      await expect(page.getByTestId("home-next-steps")).toHaveCount(0)
      await shot(page, testInfo, "14-home-page")
    })

    await test.step("9 sign out and back in keeps everything", async () => {
      // The user's id, read through the session cookie before signing out.
      const apiBase = await apiBaseUrl(page)
      const refreshed = await page.request.post(`${apiBase}/auth/refresh`)
      expect(refreshed.ok()).toBe(true)
      const { user } = (await refreshed.json()) as { user: { id: string } }
      await page.goto("/settings")
      await page.getByRole("button", { name: "Sign out" }).click()
      await expect(
        page.getByRole("link", { name: "Sign in / Sign up with Google" }).first(),
      ).toBeVisible()
      await shot(page, testInfo, "15-signed-out")
      // Dev login stands in for Google here too: the same account signs in again.
      const again = await page.request.post(`${apiBase}/auth/dev-login`, {
        data: { userId: user.id },
      })
      expect(again.ok()).toBe(true)
      await page.goto("/settings")
      await expect(page.getByLabel("Display name")).toHaveValue(name)
      await page.goto("/me/tenancies")
      await expect(page.getByText(building).first()).toBeVisible()
      await shot(page, testInfo, "16-signed-in-again-tenancies")
      await page.goto(homeUrl)
      await expect(page.getByRole("heading", { level: 1 })).toContainText(building)
      await expect(page.getByText(new RegExp(`Flat ${flat}`)).first()).toBeVisible()
      await expect(page.getByTestId("property-review").filter({ hasText: review })).toBeVisible()
      await shot(page, testInfo, "17-signed-in-again-home")
    })
  })
})
