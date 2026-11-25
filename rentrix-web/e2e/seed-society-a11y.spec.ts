import { mkdirSync } from "node:fs"
import path from "node:path"
import { expect, test, type Page, type TestInfo } from "@playwright/test"

/**
 * Search suggestions in the accessibility tree, and the demo society with several flats
 * (captain 2026-11-25):
 * - the suggestions list is a listbox of labelled groups whose options carry their own names, so a
 *   screen reader (and `getByRole("option")`) finds "Demo Greens" in it;
 * - the DEV demo seed has one society with 3 flats in 2 towers (Demo Orchard, Jayanagar), so search
 *   shows it as one society card with "3 flats" and its page lists the flats by tower.
 *
 * Read-only: it changes no data. Screenshots go to the test output, and to `SEED_A11Y_SHOTS` when set.
 */

async function shot(page: Page, testInfo: TestInfo, name: string) {
  const file = `${name}-${testInfo.project.name === "mobile" ? "390" : "desktop"}.png`
  await testInfo.attach(file, { body: await page.screenshot(), contentType: "image/png" })
  const out = process.env.SEED_A11Y_SHOTS
  if (out) {
    mkdirSync(out, { recursive: true })
    await page.screenshot({ path: path.join(out, file) })
  }
}

test("search suggestions are named options in the accessibility tree", async ({
  page,
}, testInfo) => {
  await page.goto("/search?city=BLR")
  const box = page.getByRole("search").getByRole("combobox", { name: /Locality/ })
  await box.fill("Demo Gre")
  const list = page.getByRole("listbox", { name: "Suggestions" })
  const societies = list.getByRole("group", { name: "Societies" })
  const option = societies.getByRole("option", { name: /^Demo Greens, Koramangala, Bangalore/ })
  await expect(option).toBeVisible()
  await expect(box).toHaveAttribute("aria-controls", (await list.getAttribute("id"))!)
  // the accessibility tree itself has the option under the listbox
  await expect(list).toMatchAriaSnapshot(`
    - listbox "Suggestions":
      - group "Societies":
        - option /Demo Greens, Koramangala, Bangalore/
  `)
  await shot(page, testInfo, "suggestions")
  // the keyboard reaches it too
  await box.press("ArrowDown")
  await expect(option).toHaveAttribute("aria-selected", "true")
  await expect(box).toHaveAttribute("aria-activedescendant", (await option.getAttribute("id"))!)
})

test("the demo seed has a society with 3 flats in 2 towers, grouped in search", async ({
  page,
}, testInfo) => {
  await page.goto(`/search?city=BLR&q=${encodeURIComponent("Demo Orchard")}`)
  const card = page.getByTestId("society-result").filter({ hasText: "Demo Orchard" })
  await expect(card).toHaveCount(1)
  await expect(card.getByTestId("society-flats")).toHaveText("3 flats")
  await expect(card).toContainText("Jayanagar, Bangalore")
  await shot(page, testInfo, "orchard-search")

  await card.getByRole("link", { name: "Demo Orchard" }).click()
  await expect(page).toHaveURL(/\/s\//)
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Demo Orchard")
  const towers = page.getByTestId("tower-group")
  await expect(towers).toHaveCount(2)
  await expect(towers.nth(0)).toContainText("Tower 1")
  await expect(towers.nth(0).getByTestId("card-title")).toHaveCount(2)
  await expect(towers.nth(1)).toContainText("Tower 2")
  await expect(towers.nth(1).getByTestId("card-title")).toHaveCount(1)
  await shot(page, testInfo, "orchard-society-page")
})
