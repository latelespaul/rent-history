import { expect, test, type Page } from "@playwright/test"
import { adminApi, daysAgo, freshLogin, pickRole, shot, testPhone, verifyPhone } from "./support"

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

// A fake Aadhaar number, printed in the test file and typed into a field: it must come out masked.
const FAKE_AADHAAR = "2345 6789 0123"
const fakeAgreement = () => ({
  name: "rental-agreement.pdf",
  mimeType: "application/pdf",
  buffer: Buffer.from(
    `%PDF-1.4\nBT (Leave and Licence. Licensee Aadhaar ${FAKE_AADHAAR}) Tj ET\n%%EOF\n`,
    "latin1",
  ),
})

async function signUpTenant(page: Page, phone: string) {
  await freshLogin(page)
  await shot(page, "01-signup-role")
  await page.locator("label").filter({ hasText: "I rent a home" }).click()
  await page.getByRole("checkbox", { name: /I accept the Terms/ }).check()
  await page.getByRole("button", { name: "Continue" }).click()
  await expect(page.getByText("Your account is created.")).toBeVisible()
  await shot(page, "02-account-created")
  await page.getByTestId("welcome-verify").click()
  await shot(page, "03-verify-phone")
  await verifyPhone(page, phone)
  await expect(page).toHaveURL(/\/homes\/new$/)
}

/** The four "Add your home" steps, up to "Save my home" and the next-steps screen. */
async function addHome(page: Page, society: string, flat: string, ownerPhone: string) {
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
  await shot(page, "04-add-home-address")
  await page.getByTestId("ah-next").click()

  // A near-duplicate is offered first: "Is it this one?"
  const step2 = page.getByTestId("add-home-step").filter({ hasText: /step 2 of 4/i })
  await expect(step2.or(page.getByTestId("home-matches"))).toBeVisible()
  if (await page.getByTestId("home-matches").isVisible())
    await page.getByTestId("home-match-none").click()
  await expect(step2).toBeVisible()

  await page.getByTestId("ah-relation-rent").click()
  await shot(page, "05-add-home-role")
  await page.getByTestId("ah-next").click()
  await page.getByLabel("Moved in").fill(daysAgo(90))
  await page.getByLabel(/monthly rent/i).fill("24000")
  await shot(page, "06-add-home-tenancy")
  await page.getByTestId("ah-next").click()
  await page.getByLabel(/owner’s phone/i).fill(ownerPhone)
  await shot(page, "07-add-home-owner")
  await page.getByTestId("ah-save").click()
  const done = page.getByTestId("add-home-done")
  await expect(done.getByText(/added by a tenant · not verified yet/i)).toBeVisible()
  // The flow goes on: two clear, skippable steps (captain 2026-11-23 14:53).
  await expect(done.getByTestId("steps-left")).toHaveText("Home added. 2 optional steps left")
  await expect(done.getByTestId("next-step-agreement")).toBeVisible()
  await expect(done.getByTestId("next-step-review")).toBeVisible()
  await expect(done.getByTestId("add-home-skip")).toBeVisible()
  await shot(page, "08-home-added-next-steps")
  return done
}

// The "+ Add your home" journey (captain 2026-11-22 21:38 / 22:22; D-139–D-142) and what follows
// "Save my home" (captain 2026-11-23 14:53): a brand-new tenant verifies the phone, adds the flat,
// uploads the rental agreement (a fake Aadhaar comes out masked), reviews the home, finds both on
// the home's page "not verified yet", and gets the green tick when the team verifies it.
test("a new tenant adds their home, uploads the agreement, reviews it and gets the green tick", async ({
  page,
}) => {
  const tag = Date.now().toString(36)
  const society = `E2E Society ${tag}`
  const flat = `F-${tag}`
  const phone = testPhone()
  const ownerPhone = "+919800012345"
  const review =
    "Bright rooms and steady water supply. The landlord fixed every repair within a week."

  await test.step("sign up, verify the phone, add the home", async () => {
    await signUpTenant(page, phone)
    const done = await addHome(page, society, flat, ownerPhone)
    await done.getByTestId("next-step-agreement").click()
  })

  let tenancyId = ""
  await test.step("upload the rental agreement: cover Aadhaar, consent, the file", async () => {
    await expect(page).toHaveURL(/\/tenancies\/[^/]+\/agreement/)
    tenancyId = /\/tenancies\/([^/]+)\/agreement/.exec(page.url())![1]
    await expect(page.getByTestId("agreement-step")).toHaveText(/step 1 of 3/i)
    await expect(page.getByTestId("cover-aadhaar")).toBeVisible()
    // Nothing chosen yet: each field says what is missing, no generic banner.
    await page.getByTestId("agreement-next").click()
    await expect(page.getByTestId("ag-file-error")).toBeVisible()
    await expect(page.getByTestId("ag-consent-error")).toBeVisible()
    await expect(page.getByText(/invalid fields/i)).toHaveCount(0)
    await page.getByLabel("Agreement file").setInputFiles(fakeAgreement())
    await page.getByTestId("agreement-consent").check()
    await shot(page, "09-agreement-file-consent")
    await page.getByTestId("agreement-next").click()
  })

  await test.step("type its details, an Aadhaar number among them", async () => {
    await expect(page.getByTestId("agreement-step")).toHaveText(/step 2 of 3/i)
    await page.getByLabel("Owner's name").fill("Ravi Owner")
    await page.getByLabel("Tenant's name").fill(`E2E Tenant ${FAKE_AADHAAR}`)
    await page.getByLabel(/address of the home/i).fill(`${flat}, ${society}, Koramangala`)
    await page.getByLabel(/pin code/i).fill("560034")
    await page.getByLabel(/monthly rent/i).fill("24000")
    await page.getByLabel("Start date").fill(daysAgo(90))
    await page.getByLabel(/stamp or registration number/i).fill("IN-KA12345678901234A")
    await shot(page, "10-agreement-details")
    await page.getByTestId("agreement-upload").click()
  })

  await test.step("check and confirm: the Aadhaar number is masked", async () => {
    const check = page.getByTestId("agreement-check")
    await expect(check).toBeVisible()
    await expect(page.getByTestId("agreement-step")).toHaveText(/step 3 of 3/i)
    await expect(check.getByTestId("check-tenant")).toHaveText("E2E Tenant XXXX XXXX 0123")
    await expect(check.getByTestId("aadhaar-masked")).toBeVisible()
    await expect(page.getByText(FAKE_AADHAAR)).toHaveCount(0)
    await shot(page, "11-agreement-check-masked")
    await check.getByTestId("agreement-confirm").click()
    const done = page.getByTestId("agreement-done")
    await expect(done.getByTestId("agreement-status")).toHaveText(
      "Agreement uploaded · awaiting team check",
    )
    await shot(page, "12-agreement-uploaded")
    await done.getByTestId("agreement-next-review").click()
  })

  await test.step("the review, then 'You submitted this review'", async () => {
    await page.getByTestId("review-star-4").click()
    await page.getByLabel(/your review/i).fill(review)
    await shot(page, "13-write-review")
    await page.getByTestId("review-submit").click()
    const submitted = page.getByTestId("review-submitted")
    await expect(submitted.getByText(/you submitted this review/i)).toBeVisible()
    await expect(submitted.getByTestId("tick-unverified")).toHaveText("Not verified yet")
    // Both steps are done: no agreement prompt any more.
    await expect(submitted.getByTestId("review-next-agreement")).toHaveCount(0)
    await shot(page, "14-review-submitted")
    await submitted.getByTestId("review-view-home").click()
  })

  await test.step("both show on the home's page, not verified yet; owner contact never shown", async () => {
    await expect(page).toHaveURL(/\/p\//)
    await expect(page.getByTestId("added-by-tenant")).toBeVisible()
    const mine = page.getByTestId("your-tenancy-here")
    await expect(mine.getByTestId("tenancy-unverified")).toHaveText("Not verified yet")
    await expect(mine.getByTestId("agreement-status")).toHaveText(
      "Agreement uploaded · awaiting team check",
    )
    await expect(mine.getByTestId("review-status")).toHaveText("You reviewed this home")
    await expect(mine.getByTestId("steps-left")).toHaveText("All optional steps done")
    const written = page.getByTestId("property-review").filter({ hasText: review })
    await expect(written).toBeVisible()
    await expect(written.getByTestId("tick-unverified")).toHaveText("Not verified yet")
    await expect(page.getByText(/9800012345/)).toHaveCount(0)
    await expect(page.getByText(FAKE_AADHAAR)).toHaveCount(0)
    await shot(page, "15-home-page-unverified")

    await page.goto("/search?city=BLR&locality=Koramangala")
    await expect(page.getByRole("link", { name: new RegExp(society) }).first()).toBeVisible()
    await expect(page.getByText(/9800012345/)).toHaveCount(0)
  })

  await test.step("the team verifies the tenancy from the agreement: the green tick", async () => {
    const admin = await adminApi(page)
    // The approvals queue's tenancy card carries the agreement: the typed lines, masked, and the file.
    const queue = (await (await admin.get("/admin/queue?type=TENANCY")).json()) as {
      items: Array<{ id: string; agreementId?: string | null; details: string[] }>
    }
    const card = queue.items.find((item) => item.id === tenancyId)
    expect(card, "the tenancy waits in the approvals queue").toBeTruthy()
    expect(card!.agreementId, "with its rental agreement").toBeTruthy()
    expect(card!.details.join(" ")).toContain("XXXX XXXX 0123")
    expect(card!.details.join(" ")).not.toContain(FAKE_AADHAAR)
    // The team opens the file through a 5-minute signed link (audited).
    const link = (await (await admin.get(`/admin/agreements/${card!.agreementId}/url`)).json()) as {
      url: string
    }
    expect(link.url).toContain("/documents/")
    const verified = await admin.post(`/admin/tenancies/${tenancyId}/approve`, {
      note: "Names, rent and dates match.",
    })
    expect(verified.status()).toBe(200)
    await admin.dispose()

    await page.goto(`/tenancies/${tenancyId}`)
    await expect(page.getByTestId("tenancy-verified")).toContainText("Verified by Doorway Living")
    await shot(page, "16-tenancy-verified")
    await page.goto("/me/tenancies")
    await expect(page.getByTestId("tenancy-verified").first()).toBeVisible()
    await shot(page, "17-my-tenancies-verified")
  })
})

// "Skip for now" on both steps (captain 2026-11-23 14:53): the prompts stay on My tenancies and on
// the home's page until they are done.
test("a tenant who skips both steps still sees both prompts on My tenancies and the home's page", async ({
  page,
}) => {
  const tag = Date.now().toString(36)
  const society = `E2E Skip Society ${tag}`
  await signUpTenant(page, `8${Date.now().toString().slice(-9)}`)
  const done = await addHome(page, society, `S-${tag}`, "+919800054321")
  await done.getByTestId("add-home-skip").click()

  await expect(page).toHaveURL(/\/me\/tenancies$/)
  const card = page.locator("article").filter({ hasText: society })
  await expect(card.getByTestId("steps-left")).toHaveText("2 optional steps left")
  await expect(card.getByTestId("next-step-agreement")).toBeVisible()
  await expect(card.getByTestId("next-step-review")).toBeVisible()
  await shot(page, "18-skipped-my-tenancies-prompts")

  await card.getByRole("link", { name: "Open" }).click()
  await expect(page.getByTestId("next-step-agreement")).toBeVisible()
  await page.getByRole("link", { name: new RegExp(`Koramangala, Bangalore`) }).click()
  const here = page.getByTestId("your-tenancy-here")
  await expect(here.getByTestId("next-step-agreement")).toBeVisible()
  await expect(here.getByTestId("next-step-review")).toBeVisible()
  await shot(page, "19-skipped-home-page-prompts")
})
