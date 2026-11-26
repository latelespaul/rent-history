import { expect, request, test, type Page, type TestInfo } from "@playwright/test"
import { apiBaseUrl, daysAgo, devLogin } from "./support"

/**
 * The owner reviews their tenant (landlord-adds-tenant S1, SYSTEM_DESIGN D-170) at 390 px.
 *
 * A new tenant records an ended stay on a Demo Orchard flat (owned by Demo Landlord BLR-2, seeded
 * without tenancies), through the API. The owner, in Owner mode, opens My tenancies: the stay's
 * card has "Review your tenant", and the form has one part, "The tenant", with its five questions
 * and no pros or cons. The review waits for the reveal (D-46). The tenant sees "A review about
 * you is waiting", reviews the home and the owner, and both person reviews publish: the owner's
 * review then shows under "Reviews about you". Each run makes its own tenant and stay, on a random
 * month, so reruns on one stack do not meet each other.
 */
const OWNER = "Demo Landlord BLR-2"
const TENANT = {
  RENT_ON_TIME: 5,
  CARE_OF_HOME: 4,
  COMMUNICATION: 4,
  HOUSE_RULES: 5,
  MOVE_OUT: 4,
}

async function shot(page: Page, testInfo: TestInfo, name: string) {
  await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })
}

async function noSidewaysScroll(page: Page) {
  const fits = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
  expect(fits, "no horizontal scroll").toBe(true)
}

/** A new tenant and their ended stay on a Demo Orchard flat; returns the tenant's user id. */
async function tenantRecordsStay(page: Page, key: string): Promise<string> {
  const base = await apiBaseUrl(page)
  const api = await request.newContext()
  try {
    const login = await api.post(`${base}/auth/dev-login/new`, { data: { key } })
    expect(login.ok(), `dev login: HTTP ${login.status()}`).toBe(true)
    const session = (await login.json()) as { accessToken: string; user: { id: string } }
    const onboarded = await api.post(`${base}/me/onboarding`, {
      headers: { Authorization: `Bearer ${session.accessToken}` },
      data: { roles: ["TENANT"], homeCity: "BLR", acceptTerms: true },
    })
    expect(onboarded.ok(), `onboarding: HTTP ${onboarded.status()}`).toBe(true)
    const { accessToken } = (await onboarded.json()) as { accessToken: string }

    const homes = (await (await api.get(`${base}/properties?city=BLR&size=50`)).json()) as {
      items: Array<{ id: string; buildingName: string | null }>
    }
    const flat = homes.items.find((h) => h.buildingName === "Demo Orchard")
    expect(flat, "a seeded Demo Orchard flat").toBeTruthy()
    // A two-month stay on a random month 1 to 7 years ago; it has ended, so it can be reviewed now.
    const ended = 400 + Math.floor(Math.random() * 2200)
    const added = await api.post(`${base}/me/homes`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: {
        propertyId: flat!.id,
        relation: "RENT",
        startDate: daysAgo(ended + 60),
        endDate: daysAgo(ended),
        monthlyRentInr: 24000,
      },
    })
    expect(added.ok(), `record the stay: HTTP ${added.status()} ${await added.text()}`).toBe(true)
    return session.user.id
  } finally {
    await api.dispose()
  }
}

/** Signs the page in as a user the dev login made: the refresh cookie lands in the page's jar. */
async function signInAs(page: Page, userId: string, next: string) {
  await page.context().clearCookies()
  const base = await apiBaseUrl(page)
  const login = await page.request.post(`${base}/auth/dev-login`, { data: { userId } })
  expect(login.ok(), `dev login: HTTP ${login.status()}`).toBe(true)
  await page.goto(next)
}

test("an owner reviews the tenant of a tenant-recorded stay, and the tenant sees it", async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "the 390 px phone layout only")
  test.setTimeout(testInfo.timeout * 3)
  const tag = Date.now().toString(36)
  const comment = `Paid on the 5th every month and left the flat spotless ${tag}.`
  const step = page.getByTestId("review-step")

  await page.goto("/")
  const tenantId = await tenantRecordsStay(page, `ort${tag}`)

  await test.step("the owner, in Owner mode, finds Review your tenant on the stay", async () => {
    await devLogin(page, OWNER, "/me/tenancies")
    const owner = page.getByRole("banner").getByRole("group", { name: "Mode" })
    const ownerButton = owner.getByRole("button", { name: "Owner" })
    if (await ownerButton.isVisible()) await ownerButton.click()
    await page.goto("/me/tenancies")
    await page.getByRole("tab", { name: /Ended/ }).click()
    const card = page
      .getByRole("tabpanel")
      .getByRole("article")
      .filter({ hasText: "Demo Orchard" })
      .filter({ has: page.getByTestId("tenancy-review-tenant") })
      .first()
    await expect(card).toBeVisible()
    await noSidewaysScroll(page)
    await shot(page, testInfo, "01-owner-tenancies")
    await card.getByRole("link", { name: "Open" }).click()
    await expect(page.getByTestId("tenancy-review-tenant")).toBeVisible()
    await shot(page, testInfo, "02-owner-tenancy")
    await page.getByTestId("tenancy-review-tenant").click()
  })

  await test.step("the form has one part, The tenant, with owner copy", async () => {
    await expect(page).toHaveURL(/\/tenancies\/[^/]+\/review$/)
    await expect(page.getByText("Review your tenant", { exact: true })).toBeVisible()
    await expect(step).toHaveAttribute("data-section", "stay")
    await expect(page.getByTestId("review-progress")).toContainText("Step 1 of 4")
    await page.getByTestId("review-next").click()
    await expect(step).toHaveAttribute("data-section", "tenant")
    await expect(page.getByTestId("review-progress")).toContainText("The tenant")
    for (const [criterion, stars] of Object.entries(TENANT))
      await page.getByTestId(`rate-${criterion}-${stars}`).click()
    await page.getByLabel(/anything else about the tenant/i).fill(comment)
    await expect(page.getByTestId("review-reveal")).toContainText(/hidden until your tenant/i)
    await noSidewaysScroll(page)
    await shot(page, testInfo, "03-tenant-part")
    await page.getByTestId("review-save-draft").click()
    await expect(page.getByTestId("review-draft-saved")).toBeVisible()
    await page.getByTestId("review-next").click()
  })

  await test.step("overall: worked out, would rent again, no pros or cons", async () => {
    await expect(step).toHaveAttribute("data-section", "overall")
    // 22 / 5 = 4.4 → 4 stars
    await expect(page.getByTestId("overall-tenant-4")).toHaveAttribute("aria-checked", "true")
    await page.getByTestId("recommend-tenant-yes").click()
    await expect(page.getByLabel(/^pros/i)).toHaveCount(0)
    await expect(page.getByLabel(/^cons/i)).toHaveCount(0)
    await shot(page, testInfo, "04-overall")
    await page.getByTestId("review-next").click()
  })

  await test.step("check and submit: it waits for the tenant's review", async () => {
    await expect(step).toHaveAttribute("data-section", "check")
    const summary = page.getByTestId("review-summary-tenant")
    await expect(summary).toContainText("Paid rent on time")
    await expect(summary).toContainText("Would rent to them again")
    await expect(summary).not.toContainText("Skipped")
    await shot(page, testInfo, "05-check")
    const answer = page.waitForResponse(
      (r) => r.request().method() === "POST" && /\/review-form$/.test(new URL(r.url()).pathname),
    )
    await page.getByTestId("review-submit").click()
    const written = (await (await answer).json()) as { tenant?: { status: string } }
    expect(written.tenant?.status).toBe("AWAITING_REVEAL")
    const submitted = page.getByTestId("review-submitted")
    await expect(submitted.getByTestId("review-tenant-state")).toContainText(/hidden until/i)
    await noSidewaysScroll(page)
    await shot(page, testInfo, "06-submitted")
    await submitted.getByTestId("review-view-tenancy").click()
    await expect(page.getByTestId("tenancy-review-tenant")).toHaveCount(0)
  })

  await test.step("the tenant sees that a review waits, then reviews the owner", async () => {
    await signInAs(page, tenantId, "/me/tenancies")
    const received = page.getByTestId("tenant-reviews-received")
    await expect(received.getByTestId("my-review-pending")).toBeVisible()
    await expect(received).not.toContainText(comment)
    await shot(page, testInfo, "07-tenant-waiting")

    await page.getByRole("tab", { name: /Ended/ }).click()
    await page
      .getByRole("tabpanel")
      .getByRole("article")
      .filter({ hasText: "Demo Orchard" })
      .getByRole("link", { name: "Open" })
      .click()
    await page.getByTestId("tenancy-write-review").click()
    await expect(step).toHaveAttribute("data-section", "stay")
    await page.getByTestId("review-next").click()
    await expect(step).toHaveAttribute("data-section", "home")
    for (const criterion of ["WATER_SUPPLY", "MAINTENANCE", "NOISE"])
      await page.getByTestId(`rate-${criterion}-4`).click()
    await page.getByTestId("review-next").click()
    await expect(step).toHaveAttribute("data-section", "landlord")
    for (const criterion of ["RESPONSIVENESS", "DEPOSIT_RETURN", "BEHAVIOUR"])
      await page.getByTestId(`rate-${criterion}-4`).click()
    await page.getByTestId("review-next").click()
    await expect(step).toHaveAttribute("data-section", "overall")
    await page.getByTestId("review-next").click()
    await expect(step).toHaveAttribute("data-section", "check")
    await page.getByTestId("review-submit").click()
    await expect(page.getByTestId("review-landlord-state")).toContainText(/published together/i)
  })

  await test.step("the owner's review now shows on the tenant's side", async () => {
    await page.goto("/me/tenancies")
    const received = page.getByTestId("tenant-reviews-received")
    const review = received.getByTestId("my-review").filter({ hasText: comment })
    await expect(review).toBeVisible()
    await expect(review).toContainText("By Owner")
    // No stay dates or years on a review about the tenant: they would tell who wrote it.
    await expect(review).not.toContainText(/\b20\d\d\b/)
    await expect(received.getByTestId("my-review-pending")).toHaveCount(0)
    await review.scrollIntoViewIfNeeded()
    await noSidewaysScroll(page)
    await shot(page, testInfo, "08-tenant-received")
  })
})
