import { expect, request, test, type Page } from "@playwright/test"
import { apiBaseUrl, daysAgo, devLogin } from "./support"

/**
 * Owners edit their own entries (captain 2026-11-23 21:30, D-156), at 390 px: a tenancy the team
 * has not verified yet is saved at once; a verified one warns first that the tick goes, then saves
 * as "Not verified yet" and is back in the admin approvals queue, flagged as edited. A seed tenant
 * (verified phone) adds the home, so no phone code is sent.
 */

/** A seed user's API session, to set up data the steps under test then click through. */
async function userSession(page: Page, name: string) {
  const base = await apiBaseUrl(page)
  const api = await request.newContext()
  const users = (await (await api.get(`${base}/auth/dev-users`)).json()) as Array<{
    id: string
    displayName: string
  }>
  const user = users.find((u) => u.displayName === name)
  expect(user, `the demo seed has ${name}`).toBeTruthy()
  const session = (await (
    await api.post(`${base}/auth/dev-login`, { data: { userId: user!.id } })
  ).json()) as { accessToken: string }
  return { api, base, headers: { Authorization: `Bearer ${session.accessToken}` } }
}

/** "Add your home" through the UI, as a tenant who still lives there since `movedIn`. */
async function addHome(page: Page, building: string, unit: string, movedIn: string) {
  await page.goto("/homes/new")
  await page.locator("#ah-city").selectOption({ label: "Pune" })
  await page.locator("#pf-area").fill("Baner")
  await page
    .getByRole("list", { name: "Areas" })
    .getByRole("button", { name: /^Baner/ })
    .click()
  await page.locator("#ah-building").fill(building)
  await page.getByLabel("Flat / house no.").fill(unit)
  await page.getByTestId("ah-next").click()
  const step2 = page.getByTestId("add-home-step").filter({ hasText: /step 2 of 4/i })
  await expect(step2.or(page.getByTestId("home-matches"))).toBeVisible()
  if (await page.getByTestId("home-matches").isVisible())
    await page.getByTestId("home-match-none").click()
  await page.getByTestId("ah-relation-rent").click()
  await page.getByTestId("ah-next").click()
  await page.getByLabel("Moved in").fill(movedIn)
  await page.locator("#ah-rent").fill("25000")
  await page.getByTestId("ah-next").click()
  await page.getByTestId("ah-save").click()
  await page.getByTestId("add-home-done").getByTestId("add-home-skip").click()
  await expect(page).toHaveURL(/\/me\/tenancies$/)
}

/** The ids of the caller's tenancy on the home named `building`, and of the home. */
async function ownTenancy(session: Awaited<ReturnType<typeof userSession>>, building: string) {
  const mine = (await (
    await session.api.get(`${session.base}/me/tenancies?size=50`, { headers: session.headers })
  ).json()) as { items: Array<{ id: string; property: { id: string; buildingName: string } }> }
  const tenancy = mine.items.find((t) => t.property.buildingName === building)
  expect(tenancy, "the new tenancy is listed").toBeTruthy()
  return { tenancyId: tenancy!.id, homeId: tenancy!.property.id }
}

/** The demo admin's API session on the admin host (SYSTEM_DESIGN §13.3), like `verifyPhone`. */
async function adminSession(page: Page) {
  const apiBase = await apiBaseUrl(page)
  const adminBase = process.env.ADMIN_BASE_URL
  const base = adminBase ? new URL("/api/v1", adminBase).href.replace(/\/+$/, "") : apiBase
  const api = await request.newContext(
    adminBase
      ? {}
      : { extraHTTPHeaders: { "X-Forwarded-Host": `admin.${new URL(apiBase).hostname}` } },
  )
  const users = (await (await api.get(`${base}/auth/admin/dev-users`)).json()) as Array<{
    id: string
    displayName: string
  }>
  const admin = users.find((u) => u.displayName === "Demo Admin")
  expect(admin, "the demo seed has its admin").toBeTruthy()
  const session = (await (
    await api.post(`${base}/auth/admin/dev-login`, { data: { userId: admin!.id } })
  ).json()) as { accessToken: string }
  const headers = { Authorization: `Bearer ${session.accessToken}` }
  return { api, base, headers }
}

test("edit an unverified tenancy, then a verified one: warning, saved, back in the queue", async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "the 390 px phone layout only")
  test.setTimeout(testInfo.timeout * 2)
  const tag = Date.now().toString(36)
  const building = `Edit Court ${tag}`
  const shot = (name: string) =>
    page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })

  await devLogin(page, "Demo Tenant PNQ-1", "/homes/new")
  await expect(page).toHaveURL(/\/homes\/new$/)

  await test.step("add a home and its tenancy", async () => {
    await page.locator("#ah-city").selectOption({ label: "Pune" })
    await page.locator("#pf-area").fill("Baner")
    await page
      .getByRole("list", { name: "Areas" })
      .getByRole("button", { name: /^Baner/ })
      .click()
    await page.locator("#ah-building").fill(building)
    await page.getByLabel("Flat / house no.").fill(`E-${tag.slice(-3)}`)
    await page.getByTestId("ah-next").click()
    const step2 = page.getByTestId("add-home-step").filter({ hasText: /step 2 of 4/i })
    await expect(step2.or(page.getByTestId("home-matches"))).toBeVisible()
    if (await page.getByTestId("home-matches").isVisible())
      await page.getByTestId("home-match-none").click()
    await page.getByTestId("ah-relation-rent").click()
    await page.getByTestId("ah-next").click()
    await page.getByLabel("Moved in").fill(daysAgo(200))
    await page.locator("#ah-rent").fill("25000")
    await page.getByTestId("ah-next").click()
    await page.getByTestId("ah-save").click()
    await page.getByTestId("add-home-done").getByTestId("add-home-skip").click()
    await expect(page).toHaveURL(/\/me\/tenancies$/)
  })

  let tenancyId = ""
  await test.step("edit the unverified tenancy from its card: saved at once", async () => {
    const card = page.locator("article").filter({ hasText: building })
    await card.getByTestId("tenancy-edit").click()
    await expect(page).toHaveURL(/\/tenancies\/[^/]+\/edit$/)
    tenancyId = page.url().split("/tenancies/")[1].split("/")[0]
    await expect(
      page.getByText(/not verified yet, so you can change or delete it freely/i),
    ).toBeVisible()
    await page.locator("#et-rent").fill("26000")
    await page.locator("#et-deposit").fill("52000")
    await shot("01-edit-unverified")
    await page.getByTestId("et-save").click()
    await expect(page).toHaveURL(new RegExp(`/tenancies/${tenancyId}$`))
    await expect(page.getByTestId("confirm-dialog")).toHaveCount(0)
    await expect(page.getByText(/26,000/).first()).toBeVisible()
    await expect(page.getByTestId("tenancy-not-verified")).toBeVisible()
    await shot("02-unverified-saved")
  })

  const admin = await adminSession(page)
  await test.step("the team verifies the tenancy", async () => {
    const approved = await admin.api.post(`${admin.base}/admin/tenancies/${tenancyId}/approve`, {
      headers: admin.headers,
    })
    expect(approved.ok()).toBeTruthy()
    await page.reload()
    await expect(page.getByTestId("tenancy-verified")).toBeVisible()
    await shot("03-verified")
  })

  await test.step("edit the verified tenancy: warning first, then saved as not verified", async () => {
    await page.getByTestId("tenancy-edit").click()
    await expect(page.getByTestId("edit-verified-note")).toBeVisible()
    await page.locator("#et-rent").fill("27000")
    await shot("04-edit-verified")
    await page.getByTestId("et-save").click()
    const dialog = page.getByTestId("confirm-dialog")
    await expect(dialog).toContainText(
      "Saving will remove the Verified by Doorway Living tick until our team checks it again.",
    )
    await shot("05-warning")
    await dialog.getByTestId("confirm-dialog-ok").click()
    await expect(page).toHaveURL(new RegExp(`/tenancies/${tenancyId}$`))
    await expect(page.getByText(/27,000/).first()).toBeVisible()
    await expect(page.getByTestId("tenancy-not-verified")).toBeVisible()
    await shot("06-saved-not-verified")
  })

  await test.step("it is back in the admin queue, flagged as edited, with its history", async () => {
    const queue = (await (
      await admin.api.get(`${admin.base}/admin/queue?type=TENANCY`, { headers: admin.headers })
    ).json()) as { items: Array<{ id: string; flags: string[] }> }
    const card = queue.items.find((item) => item.id === tenancyId)
    expect(card, "the edited tenancy is in the queue").toBeTruthy()
    expect(card!.flags).toContain("EDITED")
    const history = (await (
      await admin.api.get(`${admin.base}/admin/edit-history?type=TENANCY&id=${tenancyId}`, {
        headers: admin.headers,
      })
    ).json()) as Array<{ field: string; before: string; after: string; wasVerified: boolean }>
    expect(history[0]).toMatchObject({
      field: "initialRentInr",
      before: "26000",
      after: "27000",
      wasVerified: true,
    })
    expect(history.some((row) => row.field === "depositInr" && !row.wasVerified)).toBeTruthy()
  })
  await admin.api.dispose()
})

test("edit a published review: saved after the warning, shows edited and not verified yet", async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "the 390 px phone layout only")
  test.setTimeout(testInfo.timeout * 2)
  const tag = Date.now().toString(36)
  const building = `Review Court ${tag}`
  const shot = (name: string) =>
    page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })

  await devLogin(page, "Demo Tenant PNQ-1")
  await addHome(page, building, `R-${tag.slice(-3)}`, daysAgo(200))
  const tenant = await userSession(page, "Demo Tenant PNQ-1")
  const { tenancyId, homeId } = await ownTenancy(tenant, building)
  const admin = await adminSession(page)

  await test.step("the review is published and the team verifies it", async () => {
    const written = await tenant.api.post(`${tenant.base}/tenancies/${tenancyId}/reviews`, {
      headers: tenant.headers,
      data: {
        direction: "TENANT_TO_PROPERTY",
        ratingOverall: 4,
        body: "Bright flat with quick repairs and a fair deposit return; the lifts work well.",
      },
    })
    expect(written.ok()).toBeTruthy()
    const review = (await written.json()) as { id: string; status: string }
    expect(review.status).toBe("PUBLISHED")
    const approved = await admin.api.post(`${admin.base}/admin/reviews/${review.id}/approve`, {
      headers: admin.headers,
      data: {},
    })
    expect(approved.ok()).toBeTruthy()
    await page.goto(`/p/${homeId}`)
    const mine = page.getByTestId("property-review").filter({ hasText: "Bright flat" })
    await expect(mine.getByTestId("tick-verified")).toBeVisible()
    await shot("07-review-verified")
  })

  await test.step("edit it: warning first, then saved as edited and not verified yet", async () => {
    const mine = page.getByTestId("property-review").filter({ hasText: "Bright flat" })
    await mine.getByRole("button", { name: "Edit my review" }).click()
    await mine
      .getByLabel("Your review")
      .fill("Bright flat with quick repairs; the deposit came back late but in full this time.")
    await shot("08-review-edit")
    await mine.getByRole("button", { name: "Save" }).click()
    const dialog = page.getByTestId("confirm-dialog")
    await expect(dialog).toContainText("Saving will remove the Verified by Doorway Living tick")
    await shot("09-review-warning")
    await dialog.getByTestId("confirm-dialog-ok").click()
    const edited = page.getByTestId("property-review").filter({ hasText: "came back late" })
    await expect(edited.getByTestId("tick-unverified")).toBeVisible()
    await expect(edited.getByTestId("review-dates")).toContainText(/edited \d/)
    await shot("10-review-edited")
  })
  await tenant.api.dispose()
  await admin.api.dispose()
})

test("a blocked delete explains why and files an Ask our team request", async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "the 390 px phone layout only")
  test.setTimeout(testInfo.timeout * 2)
  const tag = Date.now().toString(36)
  const building = `Shared Court ${tag}`
  const shot = (name: string) =>
    page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })

  await devLogin(page, "Demo Tenant PNQ-1")
  await addHome(page, building, `S-${tag.slice(-3)}`, daysAgo(200))
  const first = await userSession(page, "Demo Tenant PNQ-1")
  const { homeId } = await ownTenancy(first, building)

  await test.step("another tenant records an earlier tenancy on the same home", async () => {
    const other = await userSession(page, "Demo Tenant BLR-1")
    const added = await other.api.post(`${other.base}/me/homes`, {
      headers: other.headers,
      data: {
        propertyId: homeId,
        relation: "RENT",
        startDate: daysAgo(1100),
        endDate: daysAgo(900),
        monthlyRentInr: 21000,
      },
    })
    expect(added.ok()).toBeTruthy()
    await other.api.dispose()
  })

  const note = `Please remove my home ${tag}; I added it by mistake.`
  await test.step("delete the home: refused with the reason and Ask our team", async () => {
    await page.goto(`/homes/${homeId}/edit`)
    await page.getByTestId("eh-delete").click()
    await page.getByTestId("confirm-dialog").getByTestId("confirm-dialog-ok").click()
    const blocked = page.getByTestId("ask-team")
    await expect(blocked).toContainText("Other people have tenancies on this home")
    await shot("11-delete-blocked")
    await blocked.getByTestId("ask-team-open").click()
    await blocked.getByLabel("What should our team change?").fill(note)
    await shot("12-ask-team")
    await blocked.getByTestId("ask-team-send").click()
    await expect(page.getByTestId("ask-team-sent")).toBeVisible()
    await shot("13-request-sent")
  })

  await test.step("the request is in the admin report queue as a change request", async () => {
    const admin = await adminSession(page)
    const queue = (await (
      await admin.api.get(`${admin.base}/admin/queue?type=REPORT`, { headers: admin.headers })
    ).json()) as { items: Array<{ title: string; details: string[]; actions: string[] }> }
    const card = queue.items.find((item) => item.details.includes(note))
    expect(card, "the change request is in the queue").toBeTruthy()
    expect(card!.title).toBe("Change request")
    expect(card!.actions).toEqual(["APPROVE"])
    await admin.api.dispose()
  })
  await first.api.dispose()
})
