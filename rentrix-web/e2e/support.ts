import { expect, type Page } from "@playwright/test"

/**
 * Shared steps for the e2e specs. They drive the real UI only; the stack must run the API in the
 * `dev` profile with `DEV_LOGIN_ENABLED=true` and the demo seed (`SEED_DEMO=true`) on an empty
 * database, as `docker-compose.e2e.yml` does.
 */

/** Sign-in creates a session and loads the user; slow runners need more than the default wait. */
const SIGN_IN_TIMEOUT = 30_000

/** Signs in through the dev-login picker as one demo seed user, e.g. "Demo Tenant BLR-1". */
export async function devLogin(page: Page, name: string, next?: string) {
  await page.goto(next ? `/login?next=${encodeURIComponent(next)}` : "/login")
  const panel = page.getByRole("region", { name: /dev login/i })
  await panel.getByRole("radio", { name: new RegExp(`^${escape(name)}\\b`) }).check()
  await panel.getByRole("button", { name: /sign in as selected seed user/i }).click()
  await expect(page).not.toHaveURL(/\/login/, { timeout: SIGN_IN_TIMEOUT })
}

/** Signs in as a brand-new account that still has to pick a role (dev only). */
export async function freshLogin(page: Page, next?: string) {
  await page.goto(next ? `/login?next=${encodeURIComponent(next)}` : "/login")
  await page
    .getByRole("region", { name: /dev login/i })
    .getByRole("button", { name: /sign in as a new user/i })
    .click()
  await expect(page).toHaveURL(/\/welcome/, { timeout: SIGN_IN_TIMEOUT })
}

/**
 * The first-sign-in role pick on `/welcome`: one role card, the terms box, Continue. Then the
 * account-created screen, left with "Not now" so the user goes on to `next` or the role home.
 */
export async function pickRole(page: Page, role: "I rent a home" | "I rent out a property") {
  await expect(page.getByRole("heading", { name: /welcome to doorway living/i })).toBeVisible()
  // The checkbox itself is visually hidden; people click the role card around it.
  await page.locator("label").filter({ hasText: role }).click()
  await expect(page.getByRole("checkbox", { name: new RegExp(role) })).toBeChecked()
  await page.getByRole("checkbox", { name: /I accept the Terms/ }).check()
  await page.getByRole("button", { name: "Continue" }).click()
  await expect(page).toHaveURL(/\/welcome\/created$/)
  await expect(page.getByText("Your account is created.")).toBeVisible()
  await page.getByRole("link", { name: /not now, take me back/i }).click()
}

/** A date `days` before today as `YYYY-MM-DD`, the value a date input takes. */
export function daysAgo(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() - days)
  const pad = (n: number) => String(n).padStart(2, "0")
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function escape(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
}
