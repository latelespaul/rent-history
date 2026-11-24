import { expect, request, test, type APIRequestContext } from "@playwright/test"
import { apiBaseUrl, daysAgo } from "./support"

/**
 * Society-aware search, API slice (captain 2026-11-25, D-164): a tenant adds a home in a new
 * society, Tower B, flat 1204 (its flat number is open to everyone, D-140). A signed-out visitor
 * then gets the society as a suggestion while typing with a typo, the flat as a suggestion after
 * "society 12", and the flat alone for "society B-1204". A seed listing's flat number is masked
 * (D-57): it is never suggested, and "Demo Greens D-101" gives the same homes as "Demo Greens".
 */

interface Suggestions {
  societies: Array<{ id: string; name: string; tower?: { name: string }; pending: boolean }>
  areas: Array<{ name: string }>
  homes: Array<{
    id: string
    unitLabel: string
    society: { name: string }
    tower?: { name: string }
  }>
}

interface Page {
  items: Array<{ id: string; buildingName: string | null }>
  page: { totalItems: number }
}

async function json<T>(api: APIRequestContext, url: string): Promise<T> {
  const answer = await api.get(url)
  expect(answer.ok(), `GET ${url}: HTTP ${answer.status()}`).toBe(true)
  return (await answer.json()) as T
}

test("a society is suggested with a typo and a flat is found by society + flat number", async ({
  page,
}, testInfo) => {
  await page.goto("/")
  const base = await apiBaseUrl(page)
  // One made-up society per project and run, so desktop and mobile and reruns never meet.
  const tag = `${testInfo.project.name.slice(0, 1)}${Date.now().toString(36)}`
  const society = `Lakeview ${tag}`

  const user = await request.newContext()
  const anonymous = await request.newContext()
  try {
    await test.step("a new tenant adds flat 1204 in Tower B of a new society", async () => {
      const login = await user.post(`${base}/auth/dev-login/new`, { data: { key: `soc${tag}` } })
      expect(login.ok()).toBe(true)
      let { accessToken } = (await login.json()) as { accessToken: string }
      const onboarded = await user.post(`${base}/me/onboarding`, {
        headers: { Authorization: `Bearer ${accessToken}` },
        data: { roles: ["TENANT"], homeCity: "BLR", acceptTerms: true },
      })
      expect(onboarded.ok(), `onboarding: HTTP ${onboarded.status()}`).toBe(true)
      accessToken = ((await onboarded.json()) as { accessToken: string }).accessToken
      const areas = await json<{ items: Array<{ id: string }> }>(
        user,
        `${base}/areas?city=BLR&q=koramangala`,
      )
      const added = await user.post(`${base}/me/homes`, {
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
    })

    await test.step("typing the society with a typo suggests it, signed out", async () => {
      const hints = await json<Suggestions>(
        anonymous,
        `${base}/search/suggestions?city=BLR&q=${encodeURIComponent(`Lakevew ${tag}`)}`,
      )
      expect(hints.societies.map((s) => s.name)).toContain(society)
      const tower = await json<Suggestions>(
        anonymous,
        `${base}/search/suggestions?city=BLR&q=${encodeURIComponent(`${society} tower b`)}`,
      )
      expect(tower.societies[0]).toMatchObject({ name: society, tower: { name: "Tower B" } })
    })

    let homeId = ""
    await test.step("society + the start of the flat number suggests the flat", async () => {
      const hints = await json<Suggestions>(
        anonymous,
        `${base}/search/suggestions?city=BLR&q=${encodeURIComponent(`${society} 12`)}`,
      )
      expect(hints.homes).toHaveLength(1)
      expect(hints.homes[0]).toMatchObject({
        unitLabel: "1204",
        society: { name: society },
        tower: { name: "Tower B" },
      })
      homeId = hints.homes[0].id
    })

    await test.step("society + flat number finds that flat directly", async () => {
      for (const q of [`${society} 1204`, `Lakeview ${tag} B-1204`, `${society} tower b 1204`]) {
        const found = await json<Page>(
          anonymous,
          `${base}/properties?city=BLR&q=${encodeURIComponent(q)}`,
        )
        expect(
          found.items.map((p) => p.id),
          q,
        ).toEqual([homeId])
      }
    })

    await test.step("a masked flat number is never matched or suggested", async () => {
      const hints = await json<Suggestions>(
        anonymous,
        `${base}/search/suggestions?city=BLR&q=${encodeURIComponent("Demo Greens D-1")}`,
      )
      expect(hints.homes).toEqual([])
      const plain = await json<Page>(anonymous, `${base}/properties?city=BLR&q=Demo%20Greens`)
      const probed = await json<Page>(
        anonymous,
        `${base}/properties?city=BLR&q=${encodeURIComponent("Demo Greens D-101")}`,
      )
      expect(plain.page.totalItems).toBeGreaterThan(0)
      expect(probed.items.map((p) => p.id).sort()).toEqual(plain.items.map((p) => p.id).sort())
    })
  } finally {
    await user.dispose()
    await anonymous.dispose()
  }
})
