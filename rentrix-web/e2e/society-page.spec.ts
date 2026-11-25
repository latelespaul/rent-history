import { mkdirSync } from "node:fs"
import path from "node:path"
import { expect, request, test, type Locator, type Page, type TestInfo } from "@playwright/test"
import { apiBaseUrl, daysAgo } from "./support"

/**
 * Society card and society page (captain 2026-11-25, D-166): two tenants add flats in one new
 * society (Tower A flat 101, a 3 BHK; Tower B flat 201). Search shows ONE society card with
 * "2 flats" instead of two home cards; it opens the society page, which lists the flats by tower,
 * filters by tower and by BHK, and opens a flat's own page.
 *
 * Screenshots go to the test output, and to `SOCIETY_PAGE_SHOTS` when set.
 */

async function shot(page: Page, testInfo: TestInfo, name: string, part?: Locator) {
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })
  const out = process.env.SOCIETY_PAGE_SHOTS
  if (out) {
    mkdirSync(out, { recursive: true })
    const file = path.join(out, `${name}-${testInfo.project.name}.png`)
    if (part) {
      await part.scrollIntoViewIfNeeded()
      await part.screenshot({ path: file })
    } else {
      await page.screenshot({ path: file, fullPage: true })
    }
  }
}

async function noSidewaysScroll(page: Page) {
  const fits = await page.evaluate(
    () => document.documentElement.scrollWidth <= window.innerWidth,
  )
  expect(fits, "no horizontal scroll").toBe(true)
}

/** A new tenant adds `unit` in `tower` of the society named `society` (joined when it exists). */
async function addFlat(
  page: Page,
  key: string,
  society: string,
  tower: string,
  unit: string,
  homeSize?: string,
) {
  const base = await apiBaseUrl(page)
  const api = await request.newContext()
  try {
    const login = await api.post(`${base}/auth/dev-login/new`, { data: { key } })
    expect(login.ok(), `dev login: HTTP ${login.status()}`).toBe(true)
    let { accessToken } = (await login.json()) as { accessToken: string }
    const onboarded = await api.post(`${base}/me/onboarding`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: { roles: ["TENANT"], homeCity: "BLR", acceptTerms: true },
    })
    expect(onboarded.ok(), `onboarding: HTTP ${onboarded.status()}`).toBe(true)
    accessToken = ((await onboarded.json()) as { accessToken: string }).accessToken
    const areas = (await (await api.get(`${base}/areas?city=BLR&q=koramangala`)).json()) as {
      items: Array<{ id: string }>
    }
    const added = await api.post(`${base}/me/homes`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: {
        relation: "RENT",
        startDate: daysAgo(30),
        home: {
          city: "BLR",
          pincode: "560034",
          propertyType: "APARTMENT",
          unitLabel: unit,
          homeSize,
          location: {
            areaId: areas.items[0].id,
            newSociety: { name: society, kind: "APARTMENT_COMPLEX" },
            newTower: { name: tower },
          },
        },
      },
    })
    expect(added.ok(), `add home: HTTP ${added.status()} ${await added.text()}`).toBe(true)
  } finally {
    await api.dispose()
  }
}

test("a society with 2 matching flats is one card that opens its page by tower", async ({
  page,
}, testInfo) => {
  test.setTimeout(testInfo.timeout * 2)
  const tag = `${testInfo.project.name.slice(0, 1)}${Date.now().toString(36)}`
  const society = `Lakeview ${tag}`
  await page.goto("/")
  await addFlat(page, `sa${tag}`, society, "Tower A", "101", "BHK3")
  await addFlat(page, `sb${tag}`, society, "Tower B", "201")

  await test.step("search shows one society card with 2 flats", async () => {
    await page.goto(`/search?city=BLR&q=${encodeURIComponent(society)}`)
    const card = page.getByTestId("society-result").filter({ hasText: society })
    await expect(card).toHaveCount(1)
    await expect(card.getByTestId("society-flats")).toHaveText("2 flats")
    await expect(card).toContainText("Koramangala, Bangalore")
    await expect(card.getByTestId("card-reviews")).toHaveText("No reviews yet")
    await expect(page.locator("article").filter({ hasText: "Flat 101" })).toHaveCount(0)
    await expect(page.getByRole("heading", { name: /^2 properties/ })).toBeVisible()
    await noSidewaysScroll(page)
    await shot(page, testInfo, "03-search-society-card", card)
    await card.getByRole("link", { name: society }).click()
  })

  await test.step("the society page lists the flats by tower", async () => {
    await expect(page).toHaveURL(/\/s\//)
    await expect(page.getByRole("heading", { level: 1 })).toContainText(society)
    await expect(page.getByTestId("society-map")).toHaveAttribute("href", /google\.com\/maps/)
    await expect(page.getByTestId("society-summary")).toContainText("2 flats on")
    const groups = page.getByTestId("tower-group")
    await expect(groups).toHaveCount(2)
    await expect(groups.nth(0)).toContainText("Tower A")
    await expect(groups.nth(0).getByTestId("card-title")).toHaveText(
      `${society} · Tower A · Flat 101`,
    )
    await expect(groups.nth(1).getByTestId("card-title")).toHaveText(
      `${society} · Tower B · Flat 201`,
    )
    await noSidewaysScroll(page)
    await shot(page, testInfo, "04-society-page")
  })

  await test.step("tower and BHK filters narrow the flats", async () => {
    const filters = page.getByRole("region", { name: "Filters" })
    await filters.getByRole("button", { name: "Tower B" }).click()
    await expect(page).toHaveURL(/[?&]tower=/)
    await expect(page.getByTestId("card-title")).toHaveText([`${society} · Tower B · Flat 201`])
    await filters.getByRole("button", { name: "All" }).click()
    await filters.getByRole("button", { name: "3 BHK" }).click()
    await expect(page).toHaveURL(/[?&]homeSize=BHK3/)
    await expect(page.getByTestId("card-title")).toHaveText([`${society} · Tower A · Flat 101`])
    await noSidewaysScroll(page)
  })

  await test.step("a flat opens its own page", async () => {
    await page.getByRole("link", { name: `${society} · Tower A · Flat 101` }).click()
    await expect(page).toHaveURL(/\/p\//)
  })
})
