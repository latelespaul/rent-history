import { expect, test } from "@playwright/test"
import { devLogin } from "./support"

// Click-test bug: the property page showed blank reviews (no text, no stars); fixed in #40.
test("a tenant searches, opens a home and reads its reviews with stars", async ({ page }) => {
  await devLogin(page, "Demo Tenant BLR-1")

  await page.goto("/search")
  const search = page.getByRole("search")
  await search.getByRole("textbox", { name: "Locality" }).fill("Koramangala")
  await search.getByRole("button", { name: "Search" }).click()
  await page.getByRole("link", { name: "Demo Greens · Tower 1 · Wing A", exact: true }).click()
  await expect(page).toHaveURL(/\/p\//)

  const reviews = page.getByRole("region", { name: /what tenants say about the home/i })
  const items = reviews.getByRole("listitem")
  await expect(items.first()).toBeVisible()
  expect(await items.count()).toBeGreaterThan(0)

  for (const item of await items.all()) {
    await expect(item.getByRole("img", { name: /^Rated [1-5] out of 5$/ })).toBeVisible()
    // The review text: a sentence of its own, not the author line, the date or a landlord reply.
    const sentence = item.locator(":scope > p").filter({ hasText: /\w+(\s+\w+){4,}/ })
    await expect(sentence.first()).toBeVisible()
  }
})
