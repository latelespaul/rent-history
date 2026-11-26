import { expect, test, type Locator } from "@playwright/test"
import { daysAgo, freshLogin, pickRole, writeHomeReview } from "./support"

/**
 * No line starts with a stray "·" (stabilization pass, captain 2026-11-24). Rows that wrap on a
 * phone used to put the separator at the start of the next item, so a 390 px line began with
 * "· stayed May 2026 – now", "· K-smo" or "· Landlord Not on Doorway Living yet". Those items are
 * now spaced by the row's gap or sit on their own line.
 */

/**
 * Texts in `scope` that can open a line with "·": a separator with nothing before it, or one that
 * is its own item in a wrapping flex row, where any item can start the wrapped line.
 */
async function leadingDots(scope: Locator): Promise<string[]> {
  return scope.evaluateAll((roots) =>
    roots.flatMap((root) => {
      const found: string[] = []
      const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
      for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        if (!(node.textContent ?? "").trim().startsWith("·")) continue
        const parent = node.parentElement
        const style = parent ? getComputedStyle(parent) : undefined
        const wrapsItems = !!style && style.display.includes("flex") && style.flexWrap === "wrap"
        let before = node.previousSibling
        while (before && !(before.textContent ?? "").trim()) before = before.previousSibling
        if (!before || wrapsItems) found.push((parent?.textContent ?? "").trim().slice(0, 60))
      }
      return found
    }),
  )
}

test("a tenant's tenancy card and review row never start a line with a separator", async ({
  page,
}, testInfo) => {
  test.setTimeout(testInfo.timeout * 2)
  const tag = `${Date.now().toString(36)}${testInfo.project.name[0]}`
  const building = `Separator Court ${tag}`
  await freshLogin(page)
  await pickRole(page, "I rent a home")

  await page.goto("/homes/new")
  await page.locator("#ah-city").selectOption({ label: "Pune" })
  await page.locator("#pf-area").fill("Baner")
  await page
    .getByRole("list", { name: "Areas" })
    .getByRole("button", { name: /^Baner/ })
    .click()
  await page.locator("#ah-building").fill(building)
  await page.getByLabel("Flat / house no.").fill(`S-${tag.slice(-3)}`)
  const pin = page.getByLabel("PIN code")
  if (!(await pin.inputValue())) await pin.fill("411045")
  await page.getByTestId("ah-next").click()
  const step2 = page.getByTestId("add-home-step").filter({ hasText: /step 2 of 4/i })
  await expect(step2.or(page.getByTestId("home-matches"))).toBeVisible()
  if (await page.getByTestId("home-matches").isVisible())
    await page.getByTestId("home-match-none").click()
  await page.getByTestId("ah-relation-rent").click()
  await page.getByTestId("ah-next").click()
  await page.getByLabel("Moved in").fill(daysAgo(150))
  await page.getByTestId("ah-next").click()
  await page.getByTestId("ah-save").click()
  await page.getByTestId("add-home-done").getByTestId("add-home-write-review").click()
  await writeHomeReview(page, 4, "Quiet lane, steady water and a landlord who fixed things fast.")
  await page.getByTestId("review-submit").click()
  await page.getByTestId("review-submitted").getByTestId("review-view-home").click()
  await expect(page).toHaveURL(/\/p\//)

  const review = page.getByTestId("property-review").first()
  // A current stay's review: the year only, never the month (D-171).
  await expect(review.getByTestId("review-dates")).toHaveText(/^Published \d{4}$/)
  expect(await leadingDots(review)).toEqual([])

  await page.goto("/me/tenancies")
  const card = page.getByRole("article").filter({ hasText: building })
  await expect(card.getByTestId("tenancy-unit")).toHaveText(`Flat S-${tag.slice(-3)}`)
  await expect(card.getByTestId("tenancy-counterparty")).toHaveText(
    "Landlord Not on Doorway Living yet",
  )
  expect(await leadingDots(card)).toEqual([])
})

test("the owner's property card shows the flat without a leading separator", async ({ page }) => {
  const building = `Separator Owner ${Date.now().toString(36)}`
  await freshLogin(page)
  await pickRole(page, "I rent out a property")
  await expect(page).toHaveURL(/\/me\/dashboard$/)
  await page.getByRole("link", { name: "Add property" }).first().click()
  const form = page.getByRole("form", { name: "Property" })
  await form.getByLabel("City").selectOption({ label: "Bangalore" })
  await form.getByLabel("PIN code").fill("560034")
  await form.locator("#pf-area").fill("Koramangala")
  await form
    .getByRole("list", { name: "Areas" })
    .getByRole("button", { name: /^Koramangala/ })
    .click()
  await form.getByLabel(/Building name/).fill(building)
  await form.getByLabel(/Flat or house number/).fill("K-12")
  await form.getByRole("button", { name: "Save as draft" }).click()
  await expect(page).toHaveURL(/\/me\/properties\/[^/]+\/edit\?saved=1$/)

  await page.goto("/me/dashboard")
  const card = page.getByTestId("owner-property").filter({ hasText: building })
  await expect(card.getByTestId("owner-property-unit")).toHaveText("Flat K-12")
  expect(await leadingDots(card)).toEqual([])
})
