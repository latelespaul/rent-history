import { mkdirSync } from "node:fs"
import path from "node:path"
import { expect, request, test, type Page, type TestInfo } from "@playwright/test"
import { apiBaseUrl, daysAgo } from "./support"

/**
 * Two-step search box (captain 2026-11-25, D-167): typing a society's name, with a typo, suggests
 * it; picking it turns the box into "[Society ×] Flat no…", which suggests that society's flat
 * numbers and towers. A picked flat opens its home page; Search with a flat number finds that
 * flat in that society. "Society + flat number" typed in one go finds the flat too.
 *
 * Screenshots go to the test output, and to `SOCIETY_PICKER_SHOTS` when set.
 */

async function shot(page: Page, testInfo: TestInfo, name: string) {
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })
  const out = process.env.SOCIETY_PICKER_SHOTS
  if (out) {
    mkdirSync(out, { recursive: true })
    // the viewport: the box and its open list of suggestions at the top of the page
    await page.screenshot({ path: path.join(out, `${name}-${testInfo.project.name}.png`) })
  }
}

async function noSidewaysScroll(page: Page) {
  const fits = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
  expect(fits, "no horizontal scroll").toBe(true)
}

/** A new tenant adds flat `unit` in `tower` of the society named `society` (joined when it exists). */
async function addFlat(page: Page, key: string, society: string, tower: string, unit: string) {
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

test("pick a society, then its flat number", async ({ page }, testInfo) => {
  test.setTimeout(testInfo.timeout * 2)
  const tag = `${testInfo.project.name.slice(0, 1)}${Date.now().toString(36)}`
  const society = `Lakeview ${tag}`
  await page.goto("/")
  await addFlat(page, `pa${tag}`, society, "Tower B", "1204")
  await addFlat(page, `pb${tag}`, society, "Tower A", "1205")
  const box = () => page.getByRole("search").getByRole("combobox", { name: /Locality|Flat number/ })
  const options = page.getByRole("listbox", { name: "Suggestions" }).getByRole("option")

  await test.step("typing the society with a typo suggests it", async () => {
    await page.goto("/search?city=BLR")
    await box().fill(`Lakevew ${tag}`)
    await expect(options.filter({ hasText: society }).first()).toBeVisible()
    await noSidewaysScroll(page)
    await shot(page, testInfo, "05-picker-societies")
  })

  await test.step("picking it turns the box into [society ×] Flat no…", async () => {
    await options.filter({ hasText: society }).first().click()
    await expect(page.getByTestId("society-chip")).toContainText(society)
    await expect(box()).toHaveAttribute("placeholder", "Flat no…")
    await box().fill("12")
    await expect(options.filter({ hasText: `${society} · Tower B · Flat 1204` })).toBeVisible()
    await expect(options.filter({ hasText: `${society} · Tower A · Flat 1205` })).toBeVisible()
    await noSidewaysScroll(page)
    await shot(page, testInfo, "06-picker-flats")
  })

  await test.step("a picked flat opens its home page", async () => {
    await options.filter({ hasText: "Flat 1204" }).click()
    await expect(page).toHaveURL(/\/p\//)
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible()
  })

  await test.step("Search with a flat number finds that flat in the society", async () => {
    await page.goto("/search?city=BLR")
    await box().fill(society)
    await options.filter({ hasText: society }).first().click()
    await box().fill("1205")
    // the list is open: Search must still be reachable (on a phone it sits below the list)
    await expect(options.first()).toBeVisible()
    await page.getByRole("search").getByRole("button", { name: "Search" }).click()
    await expect(page).toHaveURL(/[?&]society=.*[?&]q=1205/)
    await expect(page.getByTestId("society-chip")).toContainText(society)
    await expect(page.getByTestId("card-title")).toHaveText([`${society} · Tower A · Flat 1205`])
  })

  await test.step("society + flat number in one go finds the flat", async () => {
    await page.goto("/search?city=BLR")
    await box().fill(`${society} B-1204`)
    await box().press("Escape")
    await page.getByRole("search").getByRole("button", { name: "Search" }).click()
    await expect(page.getByTestId("card-title")).toHaveText([`${society} · Tower B · Flat 1204`])
    await noSidewaysScroll(page)
  })
})
