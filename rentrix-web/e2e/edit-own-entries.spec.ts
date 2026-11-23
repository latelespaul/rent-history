import { expect, request, test, type Page } from "@playwright/test"
import { apiBaseUrl, daysAgo, devLogin } from "./support"

/**
 * Owners edit their own entries (captain 2026-11-23 21:30, D-156), at 390 px: a tenancy the team
 * has not verified yet is saved at once; a verified one warns first that the tick goes, then saves
 * as "Not verified yet" and is back in the admin approvals queue, flagged as edited. A seed tenant
 * (verified phone) adds the home, so no phone code is sent.
 */

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
