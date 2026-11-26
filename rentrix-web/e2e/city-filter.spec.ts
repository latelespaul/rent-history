import { expect, test } from "@playwright/test"
import { apiBaseUrl } from "./support"

/**
 * Search by several cities (captain 2026-11-27, D-174): the search box's city field ticks any
 * number of cities, the results are homes in any of them, and the pick lives in the URL
 * (`?city=BLR,PNQ`), so it survives a reload. Signed out: search is public (D-162).
 */

test("ticks two cities, finds homes in either and keeps them in the URL", async ({ page }) => {
  await page.goto("/search?city=BLR")
  const main = page.getByRole("main")
  await expect(main.getByRole("heading", { name: /properties? in Bangalore$/ })).toBeVisible()

  await main.getByRole("button", { name: /city/i, expanded: false }).click()
  await page.getByRole("option", { name: /Pune/ }).click()
  // the list stays open for another city until Done
  await expect(page.getByRole("listbox", { name: "Cities" })).toBeVisible()
  await expect(page).toHaveURL(/[?&]city=BLR,PNQ(&|$)/)
  await page.getByRole("button", { name: "Done" }).click()

  await expect(main.getByRole("heading", { name: /in Bangalore and Pune$/ })).toBeVisible()
  await expect(main.getByRole("button", { name: /Bangalore, Pune/ })).toBeVisible()

  await page.reload()
  await expect(main.getByRole("heading", { name: /in Bangalore and Pune$/ })).toBeVisible()
})

test("the API counts homes in any of the named cities and ignores unknown codes", async ({
  page,
}) => {
  await page.goto("/search")
  const base = await apiBaseUrl(page)
  const total = async (city: string) => {
    const res = await page.request.get(`${base}/properties?city=${city}&size=1`)
    expect(res.ok()).toBe(true)
    return ((await res.json()) as { page: { totalItems: number } }).page.totalItems
  }
  const blr = await total("BLR")
  const pnq = await total("PNQ")
  expect(await total("BLR,PNQ")).toBe(blr + pnq)
  expect(await total("PNQ,NOPE")).toBe(pnq)
  const none = await page.request.get(`${base}/properties?city=NOPE`)
  expect(none.status()).toBe(422)
})
