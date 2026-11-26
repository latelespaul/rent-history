import { mkdirSync } from "node:fs"
import path from "node:path"
import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test"
import { devLogin } from "./support"

/**
 * Search from the home page (captain 2026-11-25): `/` shows the search box (city + locality or
 * society, and a Search button) in the first screen at 390 px and on desktop, signed out; signed
 * in, `/` opens `/search`, which starts with the same box, in Tenant and in Owner mode. A search
 * from the box opens the results. The footer's "Search" is a 44 px tap target on the landing too.
 *
 * Screenshots go to the test output, and to `HOME_SEARCH_SHOTS` when set.
 */

async function shot(page: Page, testInfo: TestInfo, name: string, target?: Locator) {
  const file = `${name}-${testInfo.project.name === "mobile" ? "390" : "desktop"}.png`
  const body = target ? await target.screenshot() : await page.screenshot()
  await testInfo.attach(file, { body, contentType: "image/png" })
  const out = process.env.HOME_SEARCH_SHOTS
  if (out) {
    mkdirSync(out, { recursive: true })
    if (target) await target.screenshot({ path: path.join(out, file) })
    else await page.screenshot({ path: path.join(out, file) })
  }
}

/** The search box, its input and its Search button are all in the first screen, unscrolled. */
async function searchInFirstScreen(page: Page) {
  const form = page.getByRole("search").first()
  await expect(form).toBeVisible()
  expect(await page.evaluate(() => window.scrollY)).toBe(0)
  await expect(form.getByRole("combobox")).toBeInViewport({ ratio: 1 })
  await expect(form.getByRole("button", { name: "Search", exact: true })).toBeInViewport({
    ratio: 1,
  })
  return form
}

test("the first phone screen shows the headline, the search box and the Google button", async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "the 390 px phone layout only")
  await page.goto("/")
  await expect(page.getByRole("heading", { level: 1 })).toBeInViewport({ ratio: 1 })
  await searchInFirstScreen(page)
  await expect(
    page.getByRole("link", { name: "Sign in / Sign up with Google" }).first(),
  ).toBeInViewport({ ratio: 1 })
})

test("the signed-out home page has the search box in the first screen", async ({
  page,
}, testInfo) => {
  await page.goto("/")
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible()
  const form = await searchInFirstScreen(page)
  // the box is the real one: it is in the hero, beside the sign-in call to action
  const hero = page.locator('section[aria-labelledby="hero-title"]')
  await expect(hero.getByRole("search")).toHaveCount(1)
  const fits = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
  expect(fits, "no horizontal scroll").toBe(true)
  await shot(page, testInfo, "home-signed-out")

  // step 1 says search needs no account
  await expect(page.locator("#how")).toContainText("needs no account")
  await expect(page.locator("#how")).not.toContainText("That is all it takes to search")

  await form.getByRole("combobox").fill("Koramangala")
  await form.getByRole("button", { name: "Search", exact: true }).click()
  await expect(page).toHaveURL(/\/search\?q=Koramangala$/)
  await expect(page.getByRole("heading", { name: /\d+ propert(y|ies)/ })).toBeVisible()
})

test("signed in, the home page opens search with the box in the first screen, in both modes", async ({
  page,
}, testInfo) => {
  await devLogin(
    page,
    testInfo.project.name === "mobile" ? "Demo Landlord BLR-2" : "Demo Landlord BLR-1",
  )
  // a landlord starts in Owner mode; the header switch changes mode without a page reload
  for (const mode of ["Owner", "Tenant"] as const) {
    const option = page
      .getByRole("banner")
      .getByRole("group", { name: "Mode" })
      .getByRole("button", { name: mode })
    if ((await option.getAttribute("aria-pressed")) !== "true") await option.click()
    await expect(option).toHaveAttribute("aria-pressed", "true")
    // the brand opens `/`, which sends a signed-in visitor to search
    await page.getByRole("banner").getByRole("link", { name: "Doorway Living" }).click()
    await expect(page).toHaveURL(/\/search$/)
    const form = await searchInFirstScreen(page)
    await shot(page, testInfo, `home-signed-in-${mode.toLowerCase()}`)
    await form.getByRole("combobox").fill("Koramangala")
    await form.getByRole("button", { name: "Search", exact: true }).click()
    await expect(page).toHaveURL(/\/search\?q=Koramangala$/)
  }
})

test("the landing footer Search is a 44 px tap target at 390 px", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "the 390 px phone footer")
  await page.goto("/")
  const footer = page.getByRole("contentinfo")
  const search = footer.getByRole("link", { name: "Search" })
  await search.scrollIntoViewIfNeeded()
  await expect(search).toBeVisible()
  const box = (await search.boundingBox())!
  expect(box.height).toBeGreaterThanOrEqual(44)
  expect(box.width).toBeGreaterThanOrEqual(44)
  expect(box.x).toBeGreaterThanOrEqual(0)
  expect(box.x + box.width).toBeLessThanOrEqual(390)
  await shot(page, testInfo, "footer-landing", footer)
  await search.tap()
  await expect(page).toHaveURL(/\/search$/)
  // and the app footer's Search, on the results page
  await expect(page.getByRole("heading", { name: /\d+ propert(y|ies)/ })).toBeVisible()
  const appSearch = page.getByRole("contentinfo").getByRole("link", { name: "Search" })
  await appSearch.scrollIntoViewIfNeeded()
  expect((await appSearch.boundingBox())!.height).toBeGreaterThanOrEqual(44)
  await shot(page, testInfo, "footer-app", page.getByRole("contentinfo"))
})
