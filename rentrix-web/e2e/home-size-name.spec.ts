import { expect, test } from "@playwright/test"
import { daysAgo, freshLogin, verifyPhone, testPhone } from "./support"

/**
 * Captain 2026-11-23 17:18, at 390 px: (1) the home size is a chip (1 RK … 5+ BHK, Studio, Other
 * with words), shown on the home's page and offered as a search filter; (2) a society the list
 * does not have is typed by name and saved at once, tagged "Pending review".
 */
test("a typed society name and an 'Other' home size save, show and filter", async ({
  page,
}, testInfo) => {
  test.setTimeout(testInfo.timeout * 2)
  const tag = `${Date.now().toString(36)}${testInfo.project.name[0]}`
  const society = `Typed Greens ${tag}`
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

  await test.step("type a society the list does not have", async () => {
    await page.locator("#ah-city").selectOption({ label: "Pune" })
    await page.locator("#pf-area").fill("Baner")
    await page
      .getByRole("list", { name: "Areas" })
      .getByRole("button", { name: /^Baner/ })
      .click()
    await page.locator("#pf-society-find").fill(society)
    const matches = page.getByTestId("pf-society-matches")
    await expect(matches).toContainText(/No society in this area matches/)
    await shot("society-typed")
    await matches.getByTestId("pf-society-add-typed").click()
    await expect(page.getByLabel("New society name")).toHaveValue(society)
    await expect(page.locator("#ah-building")).toHaveCount(0)
  })

  await test.step("pick 'Other' and say the size in words", async () => {
    const sizes = page.getByTestId("ah-home-size")
    for (const label of ["1 RK", "1 BHK", "2 BHK", "3 BHK", "4 BHK", "5+ BHK", "Studio", "Other"])
      await expect(sizes.getByRole("button", { name: label, exact: true })).toBeVisible()
    await sizes.getByRole("button", { name: "Other", exact: true }).click()
    await page.getByLabel("Flat / house no.").fill(`T-${tag.slice(-3)}`)
    await page.getByTestId("ah-next").click()
    await expect(page.getByText("Type the size, e.g. 2.5 BHK.")).toBeVisible()
    await page.locator("#ah-size-other").fill("2.5 BHK")
    await shot("home-size-other")
    await page.getByTestId("ah-next").click()
    const step2 = page.getByTestId("add-home-step").filter({ hasText: /step 2 of 4/i })
    await expect(step2.or(page.getByTestId("home-matches"))).toBeVisible()
    if (await page.getByTestId("home-matches").isVisible())
      await page.getByTestId("home-match-none").click()
    await page.getByTestId("ah-relation-rent").click()
    await page.getByTestId("ah-next").click()
    await page.getByLabel("Moved in").fill(daysAgo(200))
    await page.getByTestId("ah-next").click()
    await page.getByTestId("ah-save").click()
    await page.getByTestId("add-home-done").getByTestId("add-home-view").click()
  })

  await test.step("the home's page shows the society, pending, and the size", async () => {
    await expect(page).toHaveURL(/\/p\//)
    const heading = page.getByRole("heading", { level: 1 })
    await expect(heading).toContainText(society)
    await expect(heading.getByTestId("location-pending")).toBeVisible()
    await expect(page.getByText("2.5 BHK").first()).toBeVisible()
    await shot("home-page")
    const noScroll = await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    )
    expect(noScroll, "no horizontal scroll").toBe(true)
  })

  await test.step("search filters by home size", async () => {
    await page.goto("/search?city=PNQ")
    const filters = page.getByRole("region", { name: "Filters" })
    await filters.getByRole("button", { name: "2 BHK", exact: true }).click()
    await expect(page).toHaveURL(/homeSize=BHK2/)
    await expect(page.locator("article").filter({ hasText: society })).toHaveCount(0)
    await shot("search-home-size")
    await page.goto("/search?city=PNQ&homeSize=OTHER")
    await expect(page.locator("article").filter({ hasText: society })).toBeVisible()
  })
})
