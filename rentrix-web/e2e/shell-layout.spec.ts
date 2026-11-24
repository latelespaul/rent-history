import { expect, test, type Page } from "@playwright/test"
import { freshLogin, pickRole } from "./support"

/**
 * The app shell at phone and desktop widths (stabilization pass, captain 2026-11-24):
 * - the "Doorway Living" wordmark stays on one line in Tenant and Owner modes, and the top bar
 *   never scrolls sideways (it wrapped onto two lines at 390 px, and in Owner mode on desktop);
 * - the footer's "Search" is a clear teal pill with at least a 44 px tap target (it was a 20 px
 *   tall text link at 390 px).
 * Each width's header and the 390 px footer are attached as screenshots for a visual check.
 */

/** The brand link's height and the top bar row's overflow, at the current viewport. */
async function headerFit(page: Page) {
  return page.evaluate(() => {
    const link = document.querySelector("header a[href='/']")!
    const brand = link.getBoundingClientRect()
    const wordmark = link.querySelector("span:last-child")!.getBoundingClientRect()
    const row = document.querySelector("header > div")!
    return {
      brandHeight: brand.height,
      wordmarkWidth: wordmark.width,
      rowOverflow: row.scrollWidth - row.clientWidth,
      pageOverflow: document.documentElement.scrollWidth - window.innerWidth,
    }
  })
}

test("the wordmark stays on one line in both modes and the header never overflows", async ({
  page,
}, testInfo) => {
  const widths = testInfo.project.name === "mobile" ? [390, 375, 360] : [1920, 1280, 1024, 860]
  await freshLogin(page)
  await pickRole(page, "I rent a home")
  await page.goto("/search")
  for (const mode of ["tenant", "owner"] as const) {
    await page.getByTestId(`mode-${mode}`).click()
    await expect(page.getByTestId("mode-switch")).toHaveAttribute("data-mode", mode.toUpperCase())
    for (const width of widths) {
      await page.setViewportSize({ width, height: 800 })
      const fit = await headerFit(page)
      // One line of the 20 px display face sits inside the 30 px mark; two lines are ~56 px.
      expect(fit.brandHeight, `${mode} at ${width} px: brand on one line`).toBeLessThanOrEqual(32)
      // The visible "Doorway Living" text is well over 100 px wide; a hidden or clipped name is ~1 px.
      expect(fit.wordmarkWidth, `${mode} at ${width} px: wordmark visible`).toBeGreaterThan(100)
      expect(fit.rowOverflow, `${mode} at ${width} px: top bar fits`).toBeLessThanOrEqual(0)
      expect(fit.pageOverflow, `${mode} at ${width} px: no sideways scroll`).toBeLessThanOrEqual(0)
      await expect(
        page.getByRole("banner").getByRole("link", { name: "Doorway Living" }),
      ).toBeVisible()
      await testInfo.attach(`header-${mode}-${width}`, {
        body: await page.getByRole("banner").screenshot(),
        contentType: "image/png",
      })
    }
  }
})

test("the footer Search is a teal pill with a 44 px tap target at 390 px", async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "the 390 px phone footer")
  await freshLogin(page)
  await pickRole(page, "I rent a home")
  await page.goto("/settings")
  const footer = page.getByRole("contentinfo")
  const search = footer.getByRole("link", { name: "Search" })
  await search.scrollIntoViewIfNeeded()
  await expect(search).toBeVisible()
  const box = (await search.boundingBox())!
  expect(box.height).toBeGreaterThanOrEqual(44)
  expect(box.width).toBeGreaterThanOrEqual(44)
  // Inside the 390 px column, readable, and in the brand teal.
  expect(box.x).toBeGreaterThanOrEqual(0)
  expect(box.x + box.width).toBeLessThanOrEqual(390)
  const style = await search.evaluate((el) => {
    const css = getComputedStyle(el)
    return { fontSize: parseFloat(css.fontSize), color: css.color, border: css.borderTopColor }
  })
  expect(style.fontSize).toBeGreaterThanOrEqual(14)
  const teal = await page.evaluate(() => {
    const probe = document.createElement("span")
    probe.style.color = "var(--rx-teal-600)"
    document.body.append(probe)
    const color = getComputedStyle(probe).color
    probe.remove()
    return color
  })
  expect(style.border).toBe(teal)
  // The whole footer fits the phone width.
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  )
  expect(overflow).toBeLessThanOrEqual(0)
  await testInfo.attach("footer-390", { body: await footer.screenshot(), contentType: "image/png" })
  await search.tap()
  await expect(page).toHaveURL(/\/search/)
})
