import { expect, test, type Page } from "@playwright/test"
import { devLogin } from "./support"

/**
 * The "Contact us" section (landing, above the footer) and the same form on /contact:
 * - LinkedIn and email cards, a type chip, a message, Send → the thank-you state;
 * - a signed-in user who opens /contact directly gets name and email prefilled once the
 *   session loads;
 * - the admin console lists what was sent, newest first.
 */

const stamp = `${Date.now()}`

async function adminBase(page: Page) {
  const main = new URL(page.url())
  main.hostname = `admin.${main.hostname}`
  return main.origin
}

test("a visitor sends a bug report from the landing's Contact us", async ({ page }, testInfo) => {
  await page.goto("/")
  const section = page.locator("section#contact")
  await section.scrollIntoViewIfNeeded()
  await expect(section.getByText("Contact us")).toBeVisible()
  // The section sits right above the footer.
  const below = await section.evaluate((el) => el.parentElement?.nextElementSibling?.tagName)
  expect(below).toBe("FOOTER")
  await expect(section.getByTestId("contact-linkedin")).toHaveAttribute(
    "href",
    "https://www.linkedin.com/company/doorwayliving/",
  )
  await expect(section.getByTestId("contact-mail")).toHaveAttribute(
    "href",
    "mailto:hello@doorwayliving.in",
  )
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  expect(overflow, "no sideways scroll").toBeLessThanOrEqual(0)
  await testInfo.attach("landing-contact", { body: await section.screenshot(), contentType: "image/png" })

  const form = section.getByTestId("feedback-form")
  await expect(form.getByTestId("feedback-send")).toBeDisabled()
  await form.getByRole("button", { name: "Bug", exact: true }).click()
  await expect(form.getByRole("button", { name: "Bug", exact: true })).toHaveAttribute("aria-pressed", "true")
  await form.getByLabel(/^Name/).fill("E2E Visitor")
  await form.getByLabel(/^Email/).fill("visitor@example.com")
  await form.getByLabel("Message").fill(`Landing bug ${testInfo.project.name} ${stamp}`)
  await expect(form.getByText(/\/2000$/)).toBeVisible()
  await form.getByTestId("feedback-send").click()
  const thanks = section.getByTestId("feedback-thanks")
  await expect(thanks).toContainText("Thanks, we got it.")
  await expect(thanks).toContainText("We reply by email.")
  await testInfo.attach("landing-thanks", { body: await section.screenshot(), contentType: "image/png" })
})

test("a signed-in user opening /contact directly gets name and email prefilled", async ({
  page,
}, testInfo) => {
  await devLogin(page, "Demo Tenant BLR-1")
  await page.goto("/contact")
  await page.reload()
  const form = page.getByRole("main").getByTestId("feedback-form")
  await expect(form.getByLabel(/^Name/)).not.toHaveValue("")
  await expect(form.getByLabel(/^Email/)).toHaveValue(/@/)
  await form.getByRole("button", { name: "Idea", exact: true }).click()
  await form.getByLabel("Message").fill(`Contact idea ${testInfo.project.name} ${stamp}`)
  await testInfo.attach("contact-signed-in", {
    body: await page.screenshot({ fullPage: true }),
    contentType: "image/png",
  })
  await form.getByTestId("feedback-send").click()
  await expect(page.getByTestId("feedback-thanks")).toBeVisible()
})

test("the admin console lists the messages", async ({ page }, testInfo) => {
  await page.goto("/")
  const admin = await adminBase(page)
  await page.goto(`${admin}/login`)
  await page
    .getByRole("region", { name: /dev login · seed admins/i })
    .getByRole("button", { name: "Sign in as Demo Admin" })
    .click()
  await expect(page).toHaveURL(/\/approvals/, { timeout: 30_000 })
  await expect(page.getByRole("link", { name: "Feedback" }).first()).toHaveAttribute("href", "/feedback")
  // Open it directly: in dev the query devtools button sits over the last phone tab.
  await page.goto(`${admin}/feedback`)
  await expect(page.getByRole("heading", { name: "Feedback" })).toBeVisible()
  const rows = page.getByTestId("feedback-row")
  await expect(rows.filter({ hasText: new RegExp(`Contact idea ${testInfo.project.name} \\d+`) }).first()).toContainText("Idea")
  const landing = rows.filter({ hasText: new RegExp(`Landing bug ${testInfo.project.name} \\d+`) }).first()
  await expect(landing).toContainText("Bug")
  await expect(landing).toContainText("visitor@example.com")
  await testInfo.attach("admin-feedback", {
    body: await page.screenshot({ fullPage: true }),
    contentType: "image/png",
  })
})
