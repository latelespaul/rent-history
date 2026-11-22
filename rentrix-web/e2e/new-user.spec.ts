import { expect, test } from "@playwright/test"
import { daysAgo, freshLogin, pickRole } from "./support"

/**
 * The new-user journey, from a brand-new account (dev-only fresh-user login) to something on a
 * property page. The tenant half waits for the "+ Add your home" flow being built now; flip it
 * to `test` when that PR merges.
 */

test("a new landlord picks the role, adds a property and sees its page", async ({ page }) => {
  const building = `E2E New Landlord ${Date.now().toString(36)}`
  await freshLogin(page)
  await pickRole(page, "I rent out a property")
  await expect(page).toHaveURL(/\/me\/dashboard$/)

  await page.getByRole("link", { name: "Add property" }).first().click()
  const form = page.getByRole("form", { name: "Property" })
  await form.getByLabel("City").selectOption({ label: "Bangalore" })
  await form.getByLabel("PIN code").fill("560034")
  await form
    .getByRole("list", { name: "Areas" })
    .getByRole("button", { name: /^Koramangala/ })
    .click()
  await form.getByLabel(/Building name/).fill(building)
  await form.getByRole("button", { name: "Save as draft" }).click()
  await expect(page).toHaveURL(/\/me\/properties\/[^/]+\/edit\?saved=1$/)

  const id = /\/me\/properties\/([^/]+)\/edit/.exec(page.url())?.[1]
  await page.goto(`/p/${id}`)
  await expect(page.getByRole("heading", { level: 1, name: new RegExp(building) })).toBeVisible()
  await expect(page.getByRole("link", { name: "Edit listing" })).toBeVisible()
})

// The "+ Add your home" stepped flow (captain 2026-11-22 22:22), built on fm/rxm-journey. Written
// against the flow as specified; align the selectors with its data-testids and flip to `test`
// when that PR merges.
test.fixme("a new tenant adds their home, records the tenancy, reviews it and finds it in search", async ({
  page,
}) => {
  const tag = Date.now().toString(36)
  const society = `E2E Society ${tag}`
  const flat = `F-${tag}`
  const ownerPhone = "+919800012345"
  const review =
    "Bright rooms and steady water supply. The landlord fixed every repair within a week."
  await freshLogin(page)
  await pickRole(page, "I rent a home")

  await test.step("add the home: hierarchy, or a free-text society", async () => {
    await page.getByRole("link", { name: /add your home/i }).click()
    await page.getByLabel("City").selectOption({ label: "Bangalore" })
    await page.getByRole("button", { name: /^Koramangala/ }).click()
    await page.getByLabel(/society/i).fill(society)
    await page.getByLabel(/flat/i).fill(flat)
    await page.getByRole("button", { name: /continue|next/i }).click()
  })

  await test.step("a near-duplicate is offered first: 'Is it this one?'", async () => {
    const duplicate = page.getByRole("region", { name: /is it this one/i })
    if (await duplicate.isVisible()) {
      await duplicate
        .getByRole("button", { name: /no, (it is )?a different home|none of these/i })
        .click()
    }
  })

  await test.step("I rent it: dates and rent, then the owner's contact", async () => {
    await page.getByRole("button", { name: /I rent it/i }).click()
    await page.getByLabel("Start date").fill(daysAgo(60))
    await page.getByLabel(/monthly rent/i).fill("24000")
    await page.getByRole("button", { name: /continue|next/i }).click()
    await page.getByLabel(/owner.*(phone|contact)/i).fill(ownerPhone)
    await page.getByRole("button", { name: /continue|next/i }).click()
  })

  await test.step("an optional review, then saved as added by a tenant", async () => {
    await page.getByRole("radio", { name: /4 stars?/i }).check()
    await page.getByLabel(/your review/i).fill(review)
    await page.getByRole("button", { name: /submit|save/i }).click()
    await expect(page.getByText(/you submitted this review/i)).toBeVisible()
  })

  await test.step("the home is on its property page and in search, owner contact never shown", async () => {
    await page.getByRole("link", { name: /view (the )?(home|property)|see it/i }).click()
    await expect(page).toHaveURL(/\/p\//)
    await expect(page.getByText(/added by a tenant · not verified yet/i)).toBeVisible()
    const reviews = page.getByRole("region", { name: /what tenants say about the home/i })
    const mine = reviews.getByRole("listitem").filter({ hasText: review })
    await expect(mine).toBeVisible()
    await expect(mine.getByText(/^(verified|unverified)/i)).toBeVisible()
    await expect(page.getByText(ownerPhone)).toHaveCount(0)
    await expect(page.getByText(/9800012345/)).toHaveCount(0)

    await page.goto("/search?city=BLR&locality=Koramangala")
    await expect(page.getByRole("link", { name: new RegExp(society) })).toBeVisible()
    await expect(page.getByText(/9800012345/)).toHaveCount(0)
  })
})
