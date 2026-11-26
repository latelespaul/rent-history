import { expect, test, type Page } from "@playwright/test"
import { apiBaseUrl, devLogin } from "./support"

/**
 * D-168 (captain 2026-11-25): no reviewer is shown by name to anyone but themselves and admins.
 * D-169 (captain 2026-11-26): nor by anything that tells the landlord which tenant wrote it.
 * D-171 (captain 2026-11-26): the stay shows only as a coarse bucket, and a current stay's review
 * shows the year it was published, never the month.
 * A signed-out visitor and another signed-in user read a seeded home with reviews: the page and
 * the API JSON the browser received carry no short name ("Demo T.") or seed name, no stay dates
 * and no exact times; the author line is "Verified tenant" or "Tenant" with no year, the owner is
 * "Owner", and each review shows exactly the badges its API status holds ("Google verified", "ID
 * verified", "Agreement verified"). The name fields no longer say "Shown publicly as". Runs in
 * both projects (desktop and 390 px).
 */

/** A seed short name ("Demo T.") or full seed name ("Demo Tenant BLR-1"). */
const SEED_NAME = /Demo [A-Z]\.|Demo (Tenant|Landlord) [A-Z]{3}-\d/

/** A full date ("2024-03-01") or an exact time ("2024-03-01T10:00:00Z"); a "2024-03" month is fine. */
const DAY_OR_TIME = /\d{4}-\d{2}-\d{2}/

/** Fields only the author and admins get (D-169): the stay and the exact times. */
const PRIVATE_FIELDS =
  /"(period|submittedAt|publishedAt|editedAt|editUntil|createdAt|lastReviewAt)"/

interface ReviewJson {
  author?: {
    label?: string
    stayLength?: string
    shortName?: string
    badges?: Record<string, boolean | undefined>
  }
  publishedYear?: number
}

/** The stay buckets anyone may see (D-171), by API value. */
const STAY_LENGTH: Record<string, string> = {
  UNDER_ONE_YEAR: "Stayed < 1 yr",
  ONE_TO_TWO_YEARS: "Stayed 1-2 yrs",
  TWO_PLUS_YEARS: "Stayed 2+ yrs",
}

/** A published BLR seed home that lists reviews, from the public search API. */
async function homeWithReviews(page: Page): Promise<string> {
  await page.goto("/")
  const api = await apiBaseUrl(page)
  const res = await page.request.get(`${api}/properties?city=BLR&size=50`)
  expect(res.ok()).toBe(true)
  const body = (await res.json()) as { items: { id: string; listedReviews?: number }[] }
  const home = body.items.find((item) => (item.listedReviews ?? 0) > 0)
  expect(home, "a seeded BLR home with reviews").toBeTruthy()
  return home!.id
}

/** Opens the home and returns the review and detail JSON exactly as the browser received them. */
async function openHome(page: Page, id: string) {
  const reviewsResponse = page.waitForResponse(
    (r) => new URL(r.url()).pathname.endsWith(`/properties/${id}/reviews`) && r.ok(),
  )
  const detailResponse = page.waitForResponse(
    (r) => new URL(r.url()).pathname.endsWith(`/properties/${id}`) && r.ok(),
  )
  await page.goto(`/p/${id}`)
  const reviewsText = await (await reviewsResponse).text()
  const detailText = await (await detailResponse).text()
  return {
    reviewsText,
    detailText,
    reviews: (JSON.parse(reviewsText) as { items: ReviewJson[] }).items,
  }
}

async function expectAnonymous(page: Page, id: string) {
  const { reviewsText, detailText, reviews } = await openHome(page, id)

  // The name never leaves the server: not in the JSON, not in the page.
  for (const json of [reviewsText, detailText]) {
    expect(json).not.toMatch(SEED_NAME)
    expect(json).not.toContain("shortName")
  }
  // Nor the stay or an exact time (D-169): the review JSON has months only.
  expect(reviewsText).not.toMatch(PRIVATE_FIELDS)
  expect(reviewsText).not.toMatch(DAY_OR_TIME)
  const main = page.locator("main")
  const items = main.getByTestId("property-review")
  await expect(items.first()).toBeVisible()
  await expect(main.getByTestId("landlord-label").first()).toHaveText("Owner")
  await expect(main).not.toContainText(SEED_NAME)
  await expect(main).not.toContainText("Landlord not verified yet")
  await expect(main.getByTestId("review-stay")).toHaveCount(0)

  // Each review: the label, and exactly the badges its status holds.
  expect(reviews.length).toBeGreaterThan(0)
  await expect(items).toHaveCount(reviews.length)
  for (const [i, review] of reviews.entries()) {
    const item = items.nth(i)
    expect(review.author?.label).toMatch(/^(Verified tenant|Tenant)$/)
    await expect(item.getByTestId("review-author")).toHaveText(review.author!.label!)
    // No year on the card (D-169) but the year it was published, "Published 2026", for every
    // stay (D-171); the stay only as a bucket.
    const stay = review.author?.stayLength
    if (stay) expect(Object.keys(STAY_LENGTH)).toContain(stay)
    if (stay) await expect(item.getByTestId("review-stay-length")).toHaveText(STAY_LENGTH[stay])
    else await expect(item.getByTestId("review-stay-length")).toHaveCount(0)
    await expect(item.getByTestId("review-author").locator("..")).not.toContainText(/\d{4}/)
    expect(review, "no month in the JSON").not.toHaveProperty("publishedMonth")
    if (review.publishedYear) {
      await expect(item.getByTestId("review-dates")).toHaveText(/^Published \d{4}( · edited)?$/)
    }
    const text = (await item.innerText()).replace(/Published \d{4}/, "")
    expect(text, "no year on the card").not.toMatch(/\b(19|20)\d{2}\b/)
    const badges = review.author?.badges ?? {}
    await expect(item.getByTestId("badge-google")).toHaveCount(badges.google ? 1 : 0)
    await expect(item.getByTestId("badge-id")).toHaveCount(badges.id ? 1 : 0)
    await expect(item.getByTestId("badge-agreement")).toHaveCount(badges.agreement ? 1 : 0)
  }
  // The seed has no agreement-backed tenancy, so no review renders that slot.
  await expect(main.getByTestId("badge-agreement")).toHaveCount(0)
  // Seed tenants verified a phone or Aadhaar, so some review shows "ID verified".
  expect(reviews.some((r) => r.author?.badges?.id)).toBe(true)

  const fits = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
  expect(fits, "no horizontal scroll").toBe(true)
}

test("a signed-out visitor sees reviewer labels and badges, never a name [D-168]", async ({
  page,
}, testInfo) => {
  const id = await homeWithReviews(page)
  await expectAnonymous(page, id)
  await page.screenshot({ path: testInfo.outputPath("signed-out-home.png"), fullPage: true })
})

test("another signed-in user sees no reviewer name either; their own name stays theirs [D-168]", async ({
  page,
}, testInfo) => {
  await devLogin(page, "Demo Tenant PNQ-1")
  // The viewer still sees their own short name in the account menu.
  await expect(page.getByRole("button", { name: /^Account menu for Demo T\./ })).toBeVisible()
  const id = await homeWithReviews(page)
  await expectAnonymous(page, id)
  await page.screenshot({ path: testInfo.outputPath("signed-in-home.png"), fullPage: true })

  // The name field says the name stays private instead of "Shown publicly as X".
  await page.goto("/settings")
  const main = page.locator("main")
  await expect(main.getByText(/Never shown to other people/)).toBeVisible()
  await expect(main).not.toContainText(/shown publicly as/i)
  await page.screenshot({ path: testInfo.outputPath("settings-name.png"), fullPage: true })
})
