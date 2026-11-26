import AxeBuilder from "@axe-core/playwright"
import { expect, test, type Page } from "@playwright/test"
import { devLogin, freshLogin } from "./support"

/**
 * Known violations, found by the first axe run (2026-11-22) and waiting for their fix:
 * - `color-contrast`: the warn, success, demo and landlord badges and the search placeholder text
 *   are below 4.5:1 (design tokens).
 * - `label-content-name-mismatch`: the city picker button's accessible name does not contain its
 *   visible text (Radix select labelled by "City" + value).
 * The page tests skip only these rules, so any other violation fails at once. The `fixme` test
 * below runs every rule; flip it to `test` (and empty this list) when the fix merges.
 */
const KNOWN_RULES = ["color-contrast", "label-content-name-mismatch"]

/** WCAG 2.1 A and AA rules; every violation fails the page, listed by rule and element. */
async function expectNoViolations(page: Page, { all = false } = {}) {
  let axe = new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
  if (!all) axe = axe.disableRules(KNOWN_RULES)
  const { violations } = await axe.analyze()
  const found = violations.map(
    (v) =>
      `${v.id} (${v.impact}): ${v.help}\n  ${v.nodes.map((n) => n.target.join(" ")).join("\n  ")}`,
  )
  expect(found, found.join("\n")).toEqual([])
}

async function openHome(page: Page) {
  await page.goto("/")
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible()
}

/**
 * The hero (bar the search box and the sample screen, which hold KNOWN_RULES elements), the
 * problem, how it works, the two side cards, the dark trust band and the city tiles pass every
 * rule, colour contrast included (landing visual redesign, PRs 1 to 3).
 */
test("home hero, problem, how it works, audience, trust and coverage pass every WCAG 2.1 AA rule, contrast included", async ({
  page,
}) => {
  await openHome(page)
  await expect(page.getByRole("list", { name: "Live cities" })).toBeVisible()
  await expect(page.locator("#coverage li a").first()).toBeVisible()
  const hero = 'section[aria-labelledby="hero-title"]'
  const { violations } = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .include(hero)
    .include("#problem")
    .include("#how")
    .include("#audience")
    .include("#trust")
    .include("#coverage")
    .exclude(`${hero} form[role="search"]`)
    .exclude(`${hero} figure`)
    .analyze()
  const found = violations.map(
    (v) => `${v.id}: ${v.help}\n  ${v.nodes.map((n) => n.target.join(" ")).join("\n  ")}`,
  )
  expect(found, found.join("\n")).toEqual([])
})

async function openSearchAndProperty(page: Page, check: () => Promise<void>) {
  await devLogin(page, "Demo Tenant BLR-2")
  await page.goto("/search?city=BLR")
  await expect(
    page.getByRole("link", { name: "Demo Greens · Tower 1 · Wing A", exact: true }),
  ).toBeVisible()
  await check()
  await page.getByRole("link", { name: "Demo Greens · Tower 1 · Wing A", exact: true }).click()
  await expect(
    page
      .getByRole("region", { name: /what tenants say about the home/i })
      .getByRole("listitem")
      .first(),
  ).toBeVisible()
  await check()
}

async function openOnboarding(page: Page) {
  await freshLogin(page)
  await expect(page.getByRole("heading", { name: /welcome to doorway living/i })).toBeVisible()
}

test("home page has no new accessibility violations", async ({ page }) => {
  await openHome(page)
  await expectNoViolations(page)
})

test("search and property pages have no new accessibility violations", async ({ page }) => {
  await openSearchAndProperty(page, () => expectNoViolations(page))
})

test("onboarding page has no new accessibility violations", async ({ page }) => {
  await openOnboarding(page)
  await expectNoViolations(page)
})

// Fails today on KNOWN_RULES; flip to `test` with their fix.
test.fixme("home, search, property and onboarding pass every WCAG 2.1 AA rule [axe: color-contrast + label-content-name-mismatch baseline]", async ({
  page,
}) => {
  const check = () => expectNoViolations(page, { all: true })
  await openHome(page)
  await check()
  await openSearchAndProperty(page, check)
  await page.context().clearCookies()
  await openOnboarding(page)
  await check()
})
