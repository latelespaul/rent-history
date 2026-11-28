import { expect, test } from "@playwright/test"
import { daysAgo, freshLogin, verifyPhone, testPhone } from "./support"

/**
 * The DEV click-test of "Add your home" (#53, 2026-11-23 05:25) and its ruling (open by default,
 * rules R2 / R8), one spec per bug:
 * 1) a one-letter wing ("B") saves, and an API field error shows by its field on its step;
 * 2) new names show at once, tagged "Pending review", and the step 1 hint says so;
 * 3) Hyderabad has areas to pick, and no "No area matches" before typing;
 * 4) the property page invites any verified user to review, not only confirmed tenants;
 * minor) tower and wing show on the summary and the property page.
 */

test("a new tenant adds a Hyderabad flat in Tower 4 · Wing B, tagged pending review", async ({
  page,
}, testInfo) => {
  // Phone verification and five checks: more than one default timeout on a busy runner.
  test.setTimeout(testInfo.timeout * 2)
  const tag = `${Date.now().toString(36)}${testInfo.project.name[0]}`
  const society = `E2E Lake View ${tag}`
  const flat = `12${Date.now().toString().slice(-2)}`
  // Both projects run against the same database: testPhone() gives each its own number.
  const phone = testPhone()
  const shot = (name: string) =>
    page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true })

  await freshLogin(page)
  await page.locator("label").filter({ hasText: "I rent a home" }).click()
  await page.getByRole("checkbox", { name: /I accept the Terms/ }).check()
  await page.getByRole("button", { name: "Continue" }).click()
  await page.getByTestId("welcome-verify").click()
  await verifyPhone(page, phone)
  await expect(page).toHaveURL(/\/homes\/new$/)

  await test.step("bug 3: Hyderabad lists its areas, with no empty 'No area matches'", async () => {
    await page.locator("#ah-city").selectOption({ label: "Hyderabad" })
    // the list opens under the area box once it has focus
    await page.locator("#pf-area").click()
    const areas = page.getByRole("listbox", { name: "Areas" })
    await expect(areas.getByRole("option", { name: /^Gachibowli/ })).toBeVisible()
    await expect(page.getByText(/No area matches/)).toHaveCount(0)
    // Bug 2: the hint no longer promises an admin check before names show.
    await expect(page.getByText(/before they show publicly/)).toHaveCount(0)
    await expect(page.getByText(/new names show at once, tagged “Pending review”/)).toBeVisible()
    await shot("step1-hyderabad-areas")
    await page.locator("#pf-area").fill("Gachi")
    await areas.getByRole("option", { name: /^Gachibowli/ }).click()
  })

  await test.step("bug 1: a new society, tower '4' and an empty wing name stop on step 1", async () => {
    await page.locator("#pf-society").selectOption({ label: "+ Add a society…" })
    await page.getByLabel("New society name").fill(society)
    await page.locator("#pf-tower").selectOption({ label: "+ Add a tower…" })
    await page.getByLabel("New tower name").fill("4")
    await page.locator("#pf-wing").selectOption({ label: "+ Add a wing…" })
    await page.getByLabel("Flat / house no.").fill(flat)
    await page.getByLabel("PIN code").fill("500032")
    await page.getByTestId("ah-next").click()
    await expect(page.getByTestId("pf-wing-error")).toHaveText(/wing name/i)
    await expect(page.getByTestId("add-home-step")).toHaveText(/step 1 of 4/i)
    await page.getByLabel("New wing name").fill("B")
    await page.getByTestId("ah-next").click()
    const step2 = page.getByTestId("add-home-step").filter({ hasText: /step 2 of 4/i })
    await expect(step2.or(page.getByTestId("home-matches"))).toBeVisible()
    if (await page.getByTestId("home-matches").isVisible())
      await page.getByTestId("home-match-none").click()
    await expect(step2).toBeVisible()
  })

  await test.step("steps 2-4, then the API's field error goes back to step 1 by the wing", async () => {
    await page.getByTestId("ah-relation-rent").click()
    await page.getByTestId("ah-next").click()
    await page.getByLabel("Moved in").fill(daysAgo(90))
    await page.getByLabel(/monthly rent/i).fill("32000")
    await page.getByTestId("ah-next").click()
    // One rejected save, as the API answered on DEV: the error must name the wing, not
    // "The request has invalid fields." on step 4.
    let rejected = false
    await page.route("**/me/homes", async (route) => {
      if (rejected || route.request().method() !== "POST") return route.fallback()
      rejected = true
      await route.fulfill({
        status: 422,
        contentType: "application/problem+json",
        body: JSON.stringify({
          status: 422,
          code: "validation_failed",
          title: "Invalid request",
          detail: "The request has invalid fields.",
          errors: [
            { field: "location.newWing.name", code: "size", message: "Wing name is too long." },
          ],
        }),
      })
    })
    await page.getByTestId("ah-save").click()
    await expect(page.getByTestId("add-home-step")).toHaveText(/step 1 of 4/i)
    await expect(page.getByTestId("pf-wing-error")).toHaveText("Wing name is too long.")
    await expect(page.getByText("The request has invalid fields.")).toHaveCount(0)
    await shot("step1-wing-field-error")
    await page.unroute("**/me/homes")
    for (const next of [2, 3, 4]) {
      await page.getByTestId("ah-next").click()
      await expect(page.getByTestId("add-home-step")).toHaveText(
        new RegExp(`step ${next} of 4`, "i"),
      )
    }
    await page.getByTestId("ah-save").click()
  })

  await test.step("minor: the summary names the tower and the wing", async () => {
    const done = page.getByTestId("add-home-done")
    await expect(done.getByTestId("added-unit-line")).toHaveText(`Tower 4 · Wing B · Flat ${flat}`)
    await shot("added-summary")
    await done.getByTestId("add-home-view").click()
  })

  await test.step("bugs 2 and 4: the page tags the new names and invites a review", async () => {
    await expect(page).toHaveURL(/\/p\//)
    await expect(
      page.getByRole("heading", { level: 1 }).getByTestId("location-pending"),
    ).toBeVisible()
    await expect(page.getByTestId("unit-line")).toHaveText(`Tower 4 · Wing B · Flat ${flat}`)
    await expect(page.getByText(/only tenants with a confirmed tenancy/i)).toHaveCount(0)
    await expect(page.getByText(/anyone signed in can review this home/i)).toBeVisible()
    await shot("property-page")
    const noScroll = await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    )
    expect(noScroll, "no horizontal scroll").toBe(true)
  })

  await test.step("bug 2: the search card shows the home at once, tagged pending review", async () => {
    await page.goto("/search?city=HYD")
    const card = page.locator("article").filter({ hasText: society })
    await expect(card.getByTestId("location-pending")).toBeVisible()
    await shot("search-card")
  })
})
