import { expect, test } from "@playwright/test"
import { daysAgo, freshLogin, pickRole, verifyPhone } from "./support"

/**
 * The new-user journey, from a brand-new account (dev-only fresh-user login) to something on a
 * property page.
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

// The "+ Add your home" journey (captain 2026-11-22 21:38 / 22:22; D-138–D-141): a brand-new
// tenant verifies the phone, adds the flat through the hierarchy with a society typed in, records
// the tenancy and the owner's contact, reviews the home and finds it, "not verified yet".
test("a new tenant adds their home, records the tenancy, reviews it and finds it in search", async ({
  page,
}) => {
  const tag = Date.now().toString(36)
  const society = `E2E Society ${tag}`
  const flat = `F-${tag}`
  const phone = `9${Date.now().toString().slice(-9)}`
  const ownerPhone = "+919800012345"
  const review =
    "Bright rooms and steady water supply. The landlord fixed every repair within a week."
  await freshLogin(page)

  await test.step("role pick, then the account-created screen leads to the phone and back", async () => {
    await page.locator("label").filter({ hasText: "I rent a home" }).click()
    await page.getByRole("checkbox", { name: /I accept the Terms/ }).check()
    await page.getByRole("button", { name: "Continue" }).click()
    await expect(page.getByText("Your account is created.")).toBeVisible()
    await page.getByTestId("welcome-verify").click()
    await verifyPhone(page, phone)
    await expect(page).toHaveURL(/\/homes\/new$/)
  })

  await test.step("add the home: city > area > a society typed in > flat and PIN", async () => {
    await expect(page.getByTestId("add-home-step")).toHaveText(/step 1 of 4/i)
    await page.locator("#ah-city").selectOption({ label: "Bangalore" })
    await page.locator("#pf-area").fill("Koramangala")
    await page
      .getByRole("list", { name: "Areas" })
      .getByRole("button", { name: /^Koramangala/ })
      .click()
    await page.locator("#pf-society").selectOption({ label: "+ Add a society…" })
    await page.getByLabel("New society name").fill(society)
    await page.getByLabel("Flat / house no.").fill(flat)
    await page.getByLabel("PIN code").fill("560034")
    await page.getByTestId("ah-next").click()
  })

  await test.step("a near-duplicate is offered first: 'Is it this one?'", async () => {
    const step2 = page.getByTestId("add-home-step").filter({ hasText: /step 2 of 4/i })
    await expect(step2.or(page.getByTestId("home-matches"))).toBeVisible()
    if (await page.getByTestId("home-matches").isVisible())
      await page.getByTestId("home-match-none").click()
    await expect(step2).toBeVisible()
  })

  await test.step("I rent it: dates and rent, then the owner's contact", async () => {
    await page.getByTestId("ah-relation-rent").click()
    await page.getByTestId("ah-next").click()
    await page.getByLabel("Moved in").fill(daysAgo(90))
    await page.getByLabel(/monthly rent/i).fill("24000")
    await page.getByTestId("ah-next").click()
    await page.getByLabel(/owner’s phone/i).fill(ownerPhone)
    await page.getByTestId("ah-save").click()
    const done = page.getByTestId("add-home-done")
    await expect(done.getByText(/added by a tenant · not verified yet/i)).toBeVisible()
    await done.getByTestId("add-home-write-review").click()
  })

  await test.step("the review, then 'You submitted this review'", async () => {
    await page.getByTestId("review-star-4").click()
    await page.getByLabel(/your review/i).fill(review)
    await page.getByTestId("review-submit").click()
    const submitted = page.getByTestId("review-submitted")
    await expect(submitted.getByText(/you submitted this review/i)).toBeVisible()
    await expect(submitted.getByTestId("tick-unverified")).toHaveText("Not verified yet")
    await submitted.getByTestId("review-view-home").click()
  })

  await test.step("the home is on its property page and in search, owner contact never shown", async () => {
    await expect(page).toHaveURL(/\/p\//)
    await expect(page.getByTestId("added-by-tenant")).toBeVisible()
    const mine = page.getByTestId("property-review").filter({ hasText: review })
    await expect(mine).toBeVisible()
    await expect(mine.getByTestId("tick-unverified")).toHaveText("Not verified yet")
    await expect(page.getByText(/9800012345/)).toHaveCount(0)

    await page.goto("/search?city=BLR&locality=Koramangala")
    await expect(page.getByRole("link", { name: new RegExp(society) }).first()).toBeVisible()
    await expect(page.getByText(/9800012345/)).toHaveCount(0)
  })
})
