import { appendFileSync } from "node:fs"
import { expect, request, test, type Page } from "@playwright/test"

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

/**
 * Records the email of each account `POST /auth/dev-login/new` makes, to `E2E_NEW_USERS_LOG` when
 * set, so a run on a shared stack (DEV) can list the test users to clean up afterwards.
 */
export function recordNewUsers(page: Page) {
  const log = process.env.E2E_NEW_USERS_LOG
  if (!log) return
  page.on("response", async (response) => {
    if (!/\/auth\/dev-login\/new$/.test(new URL(response.url()).pathname)) return
    if (!response.ok()) return
    const body = (await response.json().catch(() => null)) as { user?: { email?: string } } | null
    if (body?.user?.email) appendFileSync(log, `${body.user.email}\n`)
  })
}

/** Signs in as a brand-new account that still has to pick a role (dev only). */
export async function freshLogin(page: Page, next?: string) {
  recordNewUsers(page)
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

/**
 * The review form (D-157) up to its check screen, for a home whose landlord is not on the
 * platform: the stay, the home (three parameters at `stars` and the comment), the overall rating
 * worked out from them. The caller then clicks "Submit review" (`review-submit`).
 */
export async function writeHomeReview(page: Page, stars: number, comment: string) {
  const step = page.getByTestId("review-step")
  await expect(step).toHaveAttribute("data-section", "stay")
  await page.getByTestId("review-next").click()
  await expect(step).toHaveAttribute("data-section", "home")
  for (const criterion of ["WATER_SUPPLY", "MAINTENANCE", "NOISE"])
    await page.getByTestId(`rate-${criterion}-${stars}`).click()
  await page.getByLabel(/anything else about the home/i).fill(comment)
  await page.getByTestId("review-next").click()
  await expect(step).toHaveAttribute("data-section", "overall")
  await expect(page.getByTestId(`overall-home-${stars}`)).toHaveAttribute("aria-checked", "true")
  await page.getByTestId("review-next").click()
  await expect(step).toHaveAttribute("data-section", "check")
}

/** A date `days` before today as `YYYY-MM-DD`, the value a date input takes. */
export function daysAgo(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() - days)
  const pad = (n: number) => String(n).padStart(2, "0")
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/**
 * A made-up name tag for one project and run, e.g. "mqhzkdwpa". The letters are random, not a
 * timestamp: search forgives typos, so two tags made seconds apart ("mmwxgthx7", "mmwxgtlcx")
 * matched each other's societies across specs.
 */
export function uniqueTag(projectName: string): string {
  const letters = Array.from({ length: 8 }, () =>
    String.fromCharCode(97 + Math.floor(Math.random() * 26)),
  ).join("")
  return `${projectName.slice(0, 1)}${letters}`
}

function escape(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
}

/** The browser calls the API at the URL `/config.js` names (D-04), not on the web origin. */
export async function apiBaseUrl(page: Page) {
  return page.evaluate(() => {
    const runtime = (window as { __RENTRIX_CONFIG__?: { apiBaseUrl?: string } }).__RENTRIX_CONFIG__
    return new URL(runtime?.apiBaseUrl || "/api/v1", location.origin).href.replace(/\/+$/, "")
  })
}

/**
 * The test phone numbers, +91 99999 0xxxx (`OTP_TEST_NUMBER_PREFIX`, firstmate 2026-11-23): their
 * codes are held for the admin relay and never sent by SMS or Telegram. Every e2e run uses only
 * these, so no run on a shared stack (DEV) pushes a code to anyone.
 */
const TEST_PHONE = /^999990\d{4}$/

let testPhoneCalls = 0

/**
 * A number in the test range, as the 10 digits the phone step takes. The last digit is the
 * project's index, so desktop and mobile never share one; the three before it come from the
 * clock, the worker and a per-worker counter, so each call gets its own number.
 */
export function testPhone(): string {
  const info = test.info()
  const project = info.config.projects.findIndex((p) => p.name === info.project.name)
  const slot = (Math.floor(Date.now() / 1000) + info.parallelIndex * 100 + testPhoneCalls++) % 1000
  return `999990${String(slot).padStart(3, "0")}${Math.max(project, 0) % 10}`
}

/**
 * Verifies the signed-in user's phone through the stepper. The e2e stack holds codes for admins
 * (`SMS_BACKEND=admin-relay`, D-137), so the code is read at `GET /admin/sms-relay` as the demo
 * admin, through its own API session (a separate cookie jar, so the page keeps its own sign-in),
 * the way an admin passes it on in a preview ({@link adminSession}).
 */
export async function verifyPhone(page: Page, digits: string) {
  expect(
    TEST_PHONE.test(digits),
    `e2e uses test numbers only (+91 99999 0xxxx), got …${digits.slice(-4)}`,
  ).toBe(true)
  await expect(page).toHaveURL(/\/verify\/phone/)
  await page.getByLabel(/your mobile number/i).fill(digits)
  await page.getByRole("button", { name: /send code/i }).click()
  // Under the relay the step says the team sends the code ("Code requested"), else "Code sent".
  await expect(page.getByText(/^Code (sent|requested)$/)).toBeVisible()
  const admin = await adminSession(page)
  const relayed = (await (await admin.get("/admin/sms-relay")).json()) as Array<{
    phone: string
    code: string
  }>
  const code = relayed.find((c) => c.phone.endsWith(digits))?.code
  await admin.dispose()
  expect(code, `a relayed code for …${digits.slice(-4)}`).toBeTruthy()
  await page.getByLabel(/enter the 6-digit code/i).fill(code!)
  await page.getByRole("button", { name: /verify and continue/i }).click()
}

/**
 * An API session as the seed's Demo Admin, apart from the page's own sign-in (a separate cookie
 * jar). Admin calls work only on the admin host (SYSTEM_DESIGN §13.3): on a deployed stack they go
 * to `ADMIN_BASE_URL`; on the local stack they carry `X-Forwarded-Host: admin.<host>`, which the
 * API honours from loopback and Docker peers only. The caller disposes it.
 */
export async function adminSession(page: Page) {
  const apiBase = await apiBaseUrl(page)
  // A deployed stack has a real admin host: `ADMIN_BASE_URL` names it, and the admin calls go
  // there. The local e2e stack has none, so they carry the admin host header the API trusts from
  // loopback and Docker-bridge peers only.
  const adminBase = process.env.ADMIN_BASE_URL
  const apiHost = new URL(apiBase).hostname
  if (!adminBase && !isLocalHost(apiHost)) {
    throw new Error(
      `ADMIN_BASE_URL is not set and the API host ${apiHost} is not local: set ADMIN_BASE_URL to ` +
        "the admin host that serves /api/v1 (e.g. https://admin.<dev host>)",
    )
  }
  const adminApi = adminBase ? new URL("/api/v1", adminBase).href.replace(/\/+$/, "") : apiBase
  const api = await request.newContext(
    adminBase ? {} : { extraHTTPHeaders: { "X-Forwarded-Host": `admin.${apiHost}` } },
  )
  const users = (await (await api.get(`${adminApi}/auth/admin/dev-users`)).json()) as Array<{
    id: string
    displayName: string
  }>
  const admin = users.find((u) => u.displayName === "Demo Admin")
  expect(admin, "the demo seed has its admin").toBeTruthy()
  const session = (await (
    await api.post(`${adminApi}/auth/admin/dev-login`, { data: { userId: admin!.id } })
  ).json()) as { accessToken: string }
  const headers = { Authorization: `Bearer ${session.accessToken}` }
  return {
    get: (path: string) => api.get(`${adminApi}${path}`, { headers }),
    post: (path: string, data?: unknown) => api.post(`${adminApi}${path}`, { headers, data }),
    dispose: () => api.dispose(),
  }
}

/**
 * The team's green tick on a review (D-134), as the demo admin approves it in the queue
 * (`POST /admin/reviews/{id}/approve`): only ticked reviews count in the headline ratings (D-163).
 */
export async function approveReview(page: Page, reviewId: string) {
  const admin = await adminSession(page)
  const answer = await admin.post(`/admin/reviews/${reviewId}/approve`, {})
  expect(answer.ok(), `approve review ${reviewId}: HTTP ${answer.status()}`).toBe(true)
  await admin.dispose()
}

/** Loopback names and single-label Docker service names (the compose and CI stacks). */
function isLocalHost(hostname: string): boolean {
  return (
    hostname === "localhost" ||
    hostname.endsWith(".localhost") ||
    hostname === "127.0.0.1" ||
    hostname === "[::1]" ||
    !hostname.includes(".")
  )
}
