import { mkdirSync } from "node:fs"
import path from "node:path"
import { expect, test, type Page, type TestInfo } from "@playwright/test"
import { freshLogin, verifyPhone } from "./support"

/**
 * Release gate for "Add your home" before LIVE (captain 2026-11-23 11:35): every step 1-4 and
 * each branch at 390 px. Society vs standalone (apartment and house), every type, bedrooms blank
 * and filled, a PIN code outside the city, the owner full / name only / blank, the rent filled /
 * blank, Back and Next keeping what was typed, the final save, and the home's page showing what
 * was entered. The generic "The request has invalid fields." banner must never appear.
 *
 * Screenshots of each step go to the test output, and also to `ADDHOME_GATE_SHOTS` when set
 * (`rentrix-docs/screenshots/addhome-gate/` for the PR).
 */

const GENERIC = "The request has invalid fields."

type HomeType = "Apartment" | "Independent house" | "Villa" | "Studio" | "Room" | "PG"

interface Scenario {
  name: string
  city: string
  area: string
  pincode: string
  /** A PIN code of another city, typed first: the save must point at the PIN field on step 1. */
  wrongPincode?: string
  /** A new society; without it the home is standalone. */
  society?: string
  buildingName?: string
  type: HomeType
  bedrooms?: string
  /** Each scenario lives in its own past year, so one tenant's periods never overlap. */
  year: number
  rent?: string
  owner?: { name?: string; email?: string; phone?: string }
  /** Go back from step 4 to step 1 and forward again, checking every value is kept. */
  backAndForth?: boolean
}

const tag = Date.now().toString(36)

const SCENARIOS: Scenario[] = [
  {
    name: "01-society-apartment-full",
    city: "Bangalore",
    area: "Koramangala",
    pincode: "560034",
    wrongPincode: "400001",
    society: `Gate Greens ${tag}`,
    type: "Apartment",
    bedrooms: "2",
    year: 2016,
    rent: "32000",
    owner: { name: "Ravi Owner", email: "ravi.owner@example.test", phone: "+919800012345" },
    backAndForth: true,
  },
  {
    name: "02-standalone-apartment-name-only-no-rent",
    city: "Hyderabad",
    area: "Gachibowli",
    pincode: "500032",
    buildingName: `Sai Residency ${tag}`,
    type: "Apartment",
    year: 2017,
    owner: { name: "Lakshmi Owner" },
  },
  {
    name: "03-standalone-house-blank-owner",
    city: "Gurugram",
    area: "DLF Phase 1",
    pincode: "122002",
    type: "Independent house",
    bedrooms: "3",
    year: 2018,
    rent: "45000",
  },
  {
    name: "04-villa",
    city: "Hyderabad",
    area: "Jubilee Hills",
    pincode: "500033",
    buildingName: `Gate Villas ${tag}`,
    type: "Villa",
    bedrooms: "4",
    year: 2019,
    rent: "90000",
  },
  {
    name: "05-studio",
    city: "Mumbai",
    area: "Powai",
    pincode: "400076",
    type: "Studio",
    year: 2020,
  },
  {
    name: "06-room",
    city: "Delhi",
    area: "Saket",
    pincode: "110017",
    type: "Room",
    year: 2021,
    rent: "12000",
  },
  {
    name: "07-pg",
    city: "Noida",
    area: "Sector 62",
    pincode: "201309",
    type: "PG",
    bedrooms: "1",
    year: 2022,
    rent: "9000",
  },
]

async function shot(page: Page, testInfo: TestInfo, name: string) {
  await expect(page.getByText(GENERIC)).toHaveCount(0)
  const file = `${name}.png`
  await page.screenshot({ path: testInfo.outputPath(file), fullPage: true })
  const out = process.env.ADDHOME_GATE_SHOTS
  if (out) {
    mkdirSync(out, { recursive: true })
    await page.screenshot({ path: path.join(out, file), fullPage: true })
  }
}

const step = (page: Page, n: number) =>
  expect(page.getByTestId("add-home-step")).toHaveText(new RegExp(`step ${n} of 4`, "i"))

const flat = (s: Scenario) => `${s.year % 100}0${s.name.slice(0, 2)}`

async function fillAddress(page: Page, s: Scenario, pincode: string) {
  await page.locator("#ah-city").selectOption({ label: s.city })
  await expect(page.getByText(/No area matches/)).toHaveCount(0)
  await page.locator("#pf-area").fill(s.area)
  await page
    .getByRole("list", { name: "Areas" })
    .getByRole("button", { name: new RegExp(`^${s.area}`) })
    .click()
  if (s.society) {
    await page.locator("#pf-society").selectOption({ label: "+ Add a society…" })
    await page.getByLabel("New society name").fill(s.society)
    await expect(page.locator("#ah-building")).toHaveCount(0)
  } else {
    await expect(page.locator("#pf-society")).toHaveValue("")
  }
  await page.locator("#ah-type").selectOption({ label: s.type })
  if (!s.society) {
    await expect(page.locator("#ah-building")).toBeVisible()
    if (s.buildingName) await page.locator("#ah-building").fill(s.buildingName)
  }
  await page.getByLabel("Flat / house no.").fill(flat(s))
  await page.getByLabel("PIN code").fill(pincode)
  if (s.bedrooms) await page.locator("#ah-bedrooms").fill(s.bedrooms)
}

async function nextFromAddress(page: Page) {
  await page.getByTestId("ah-next").click()
  const step2 = page.getByTestId("add-home-step").filter({ hasText: /step 2 of 4/i })
  await expect(step2.or(page.getByTestId("home-matches"))).toBeVisible()
  if (await page.getByTestId("home-matches").isVisible())
    await page.getByTestId("home-match-none").click()
  await expect(step2).toBeVisible()
}

async function addHome(page: Page, testInfo: TestInfo, s: Scenario) {
  await page.goto("/homes/new")
  await step(page, 1)

  await fillAddress(page, s, s.wrongPincode ?? s.pincode)
  // A standalone apartment cannot go on without its building name.
  if (!s.society && s.type === "Apartment" && s.buildingName) {
    await page.locator("#ah-building").fill("")
    await page.getByTestId("ah-next").click()
    await expect(page.getByTestId("ah-building-error")).toHaveText("Enter the building name.")
    await shot(page, testInfo, `${s.name}-1a-building-required`)
    await page.locator("#ah-building").fill(s.buildingName)
  }
  await shot(page, testInfo, `${s.name}-1-where`)
  await nextFromAddress(page)

  await page.getByTestId("ah-relation-rent").click()
  await shot(page, testInfo, `${s.name}-2-role`)
  await page.getByTestId("ah-next").click()

  await step(page, 3)
  await page.getByLabel("Moved in").fill(`${s.year}-01-01`)
  await page.getByLabel("I still live here").uncheck()
  await page.getByLabel("Moved out").fill(`${s.year}-06-30`)
  if (s.rent) await page.locator("#ah-rent").fill(s.rent)
  await shot(page, testInfo, `${s.name}-3-tenancy`)
  await page.getByTestId("ah-next").click()

  await step(page, 4)
  if (s.owner?.name) await page.locator("#ah-owner-name").fill(s.owner.name)
  if (s.owner?.email) await page.locator("#ah-owner-email").fill(s.owner.email)
  if (s.owner?.phone) await page.locator("#ah-owner-phone").fill(s.owner.phone)
  await shot(page, testInfo, `${s.name}-4-owner`)

  if (s.backAndForth) {
    for (const n of [3, 2, 1]) {
      await page.getByTestId("ah-back").click()
      await step(page, n)
    }
    await expect(page.getByLabel("Flat / house no.")).toHaveValue(flat(s))
    await expect(page.locator("#ah-bedrooms")).toHaveValue(s.bedrooms ?? "")
    if (s.society) await expect(page.getByLabel("New society name")).toHaveValue(s.society)
    await nextFromAddress(page)
    await page.getByTestId("ah-next").click()
    await expect(page.locator("#ah-rent")).toHaveValue(s.rent ?? "")
    await page.getByTestId("ah-next").click()
    await expect(page.locator("#ah-owner-name")).toHaveValue(s.owner?.name ?? "")
    await expect(page.locator("#ah-owner-phone")).toHaveValue(s.owner?.phone ?? "")
  }

  await page.getByTestId("ah-save").click()

  if (s.wrongPincode) {
    // The API's PIN error goes back to step 1, by the PIN field, never as the generic banner.
    await step(page, 1)
    await expect(page.getByTestId("ah-pincode-error")).toContainText(/not in/i)
    await shot(page, testInfo, `${s.name}-1b-pincode-error`)
    await page.getByLabel("PIN code").fill(s.pincode)
    await nextFromAddress(page)
    for (const n of [3, 4]) {
      await page.getByTestId("ah-next").click()
      await step(page, n)
    }
    await page.getByTestId("ah-save").click()
  }

  const done = page.getByTestId("add-home-done")
  await expect(done).toBeVisible()
  await expect(done.getByText(s.rent ? /\/mo/ : /Rent not shared/)).toBeVisible()
  await shot(page, testInfo, `${s.name}-5-saved`)
  await done.getByTestId("add-home-view").click()

  await expect(page).toHaveURL(/\/p\//)
  const title = s.society ?? s.buildingName ?? s.area
  await expect(page.getByRole("heading", { level: 1 })).toContainText(title)
  await expect(page.getByText(new RegExp(`${s.area}, ${s.city} · ${s.pincode}`))).toBeVisible()
  await expect(page.getByText(new RegExp(`Flat ${flat(s)}`)).first()).toBeVisible()
  await expect(page.getByText(s.type).first()).toBeVisible()
  if (s.bedrooms) await expect(page.getByText(`${s.bedrooms} BHK`).first()).toBeVisible()
  await expect(page.getByTestId("added-by-tenant")).toBeVisible()
  // The owner's contact stays private to the tenant who typed it: it is not on the page.
  if (s.owner?.phone) await expect(page.getByText(s.owner.phone)).toHaveCount(0)
  const noScroll = await page.evaluate(
    () => document.documentElement.scrollWidth <= window.innerWidth,
  )
  expect(noScroll, "no horizontal scroll at 390 px").toBe(true)
  await shot(page, testInfo, `${s.name}-6-home-page`)
}

test.describe("add your home release gate (390 px)", () => {
  test.skip(({ viewport }) => (viewport?.width ?? 0) > 390, "runs in the 390 px mobile project")

  test("every step and branch saves, and the home's page shows it", async ({ page }, testInfo) => {
    test.setTimeout(testInfo.timeout * SCENARIOS.length)
    await freshLogin(page)
    await page.locator("label").filter({ hasText: "I rent a home" }).click()
    await page.getByRole("checkbox", { name: /I accept the Terms/ }).check()
    await page.getByRole("button", { name: "Continue" }).click()
    await page.getByTestId("welcome-verify").click()
    await verifyPhone(page, `7${Date.now().toString().slice(-9)}`)
    await expect(page).toHaveURL(/\/homes\/new$/)

    for (const scenario of SCENARIOS) {
      await test.step(scenario.name, () => addHome(page, testInfo, scenario))
    }
  })
})
