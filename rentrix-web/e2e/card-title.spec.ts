import { mkdirSync } from "node:fs"
import path from "node:path"
import { expect, request, test, type Locator, type Page, type TestInfo } from "@playwright/test"
import { apiBaseUrl, daysAgo, uniqueTag } from "./support"

/**
 * Home card title line (captain 2026-11-25, D-165): every home card, in search results and on the
 * signed-out home feed, starts with "Society · Tower · Flat", then the area and city, then the
 * number of reviews (plus the verified average when there is one). A tenant-added home's flat
 * number is open to everyone (D-140) and shows; a seed listing's masked one (Demo Residency A,
 * flat D-101, D-57) never does.
 *
 * Screenshots go to the test output, and to `CARD_TITLE_SHOTS` when set.
 */

/** The full page to the test output; the card itself to `CARD_TITLE_SHOTS` (it sits below the filters on a phone). */
async function shot(page: Page, card: Locator, testInfo: TestInfo, name: string) {
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })
  const out = process.env.CARD_TITLE_SHOTS
  if (out) {
    mkdirSync(out, { recursive: true })
    await card.scrollIntoViewIfNeeded()
    await card.screenshot({ path: path.join(out, `${name}-${testInfo.project.name}.png`) })
  }
}

async function noSidewaysScroll(page: Page) {
  const fits = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
  expect(fits, "no horizontal scroll").toBe(true)
}

/** A new tenant adds flat 1204 in Tower B of a new society in Koramangala, through the API. */
async function addOpenFlat(page: Page, society: string, key: string) {
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
          unitLabel: "1204",
          location: {
            areaId: areas.items[0].id,
            newSociety: { name: society, kind: "APARTMENT_COMPLEX" },
            newTower: { name: "Tower B" },
          },
        },
      },
    })
    expect(added.ok(), `add home: HTTP ${added.status()} ${await added.text()}`).toBe(true)
  } finally {
    await api.dispose()
  }
}

test("home cards lead with society, tower and an open flat number", async ({ page }, testInfo) => {
  const tag = uniqueTag(testInfo.project.name)
  const society = `Lakeview ${tag}`
  await page.goto("/")
  await addOpenFlat(page, society, `card${tag}`)

  await test.step("search shows the open flat as Society · Tower B · Flat 1204", async () => {
    await page.goto(`/search?city=BLR&q=${encodeURIComponent(society)}&sort=newest`)
    const card = page.locator("article").filter({ hasText: society })
    await expect(card.getByTestId("card-title")).toHaveText(`${society} · Tower B · Flat 1204`)
    await expect(card).toContainText("Koramangala, Bangalore")
    await expect(card.getByTestId("card-reviews")).toHaveText("No reviews yet")
    await noSidewaysScroll(page)
    await shot(page, card, testInfo, "01-search-open-flat")
  })

  await test.step("a masked flat number never shows on a card", async () => {
    await page.goto("/search?city=BLR&q=Demo%20Greens")
    const card = page.locator("article").filter({ hasText: "Demo Greens" }).first()
    await expect(card.getByTestId("card-title")).toHaveText("Demo Greens · Tower 1 · Wing A")
    await expect(card).not.toContainText("D-101")
    await expect(card.getByTestId("card-reviews")).toBeVisible()
    await noSidewaysScroll(page)
    await shot(page, card, testInfo, "02-search-masked-flat")
  })

  await test.step("the signed-out home feed uses the same title line", async () => {
    await page.goto("/")
    const titles = page.getByTestId("card-title")
    await expect(titles.first()).toBeVisible()
    const feed = await titles.allTextContents()
    // a society home of the feed leads with its society and tower, never a masked flat number
    for (const title of feed.filter((t) => t.startsWith("Demo Greens"))) {
      expect(title).toBe("Demo Greens · Tower 1 · Wing A")
    }
    expect(feed.join(" | ")).not.toContain("D-101")
    await noSidewaysScroll(page)
  })
})
