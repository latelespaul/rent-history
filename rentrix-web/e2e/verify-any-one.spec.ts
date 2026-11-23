import { expect, test } from "@playwright/test"
import { freshLogin, pickRole } from "./support"

/**
 * Verify with any one (captain 2026-11-22 23:25, D-144): to post, a user needs any one verified
 * method: phone, masked Aadhaar or LinkedIn. The stepper offers all three on its "Verify (any
 * one)" step, and a LinkedIn link counts once our team has checked it. The API tests cover
 * posting with only Aadhaar or only LinkedIn verified; the admin check of a LinkedIn link has no
 * web screen yet, so this spec stops at "Waiting for a check".
 */
test("a new user is asked to verify with any one method and can pick LinkedIn instead of the phone", async ({
  page,
}) => {
  await freshLogin(page)
  await pickRole(page, "I rent a home")

  const banner = page.getByRole("region", { name: "Verification" })
  await expect(banner).toContainText("Verify to post: any one of phone, Aadhaar or LinkedIn.")
  await banner.getByRole("link", { name: "Verify now" }).click()

  await expect(page).toHaveURL(/\/verify\/phone\?next=/)
  await expect(page.getByRole("heading", { name: /verify \(any one\)/i })).toBeVisible()
  await expect(page.getByText(/pick any one: phone, aadhaar or linkedin/i)).toBeVisible()
  await expect(page.getByLabel(/your mobile number/i)).toBeVisible()

  await page.getByRole("link", { name: /use aadhaar or linkedin/i }).click()
  await expect(page.getByRole("heading", { name: /aadhaar, linkedin, pan/i })).toBeVisible()
  await expect(page.getByText(/any one is enough to post/i)).toBeVisible()

  const tag = Date.now().toString(36)
  await page.getByLabel("LinkedIn profile URL").fill(`linkedin.com/in/e2e-any-one-${tag}`)
  await page.getByRole("button", { name: "Save link" }).click()
  await expect(page.getByText("Waiting for a check")).toBeVisible()

  // Until the team checks the link, posting stays closed: the banner is still there.
  await page.goto("/me/tenancies")
  await expect(page.getByRole("region", { name: "Verification" })).toContainText("Verify to post")
})

test("at 390 px the ID step's option cards stay inside the step card", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await freshLogin(page)
  await pickRole(page, "I rent a home")
  await page.goto("/verify/id")
  await expect(page.getByRole("heading", { name: /aadhaar, linkedin, pan/i })).toBeVisible()

  // Each option card's right edge must sit inside its grid, and the page must not scroll sideways.
  const overflow = await page.getByText("PAN card", { exact: true }).evaluate((title) => {
    const grid = title.closest(".grid")
    if (!grid) return ["no grid"]
    const limit = grid.getBoundingClientRect().right + 0.5
    const cards = Array.from(grid.children).filter(
      (card) => card.getBoundingClientRect().right > limit,
    )
    const root = document.scrollingElement
    const sideways = root && root.scrollWidth > window.innerWidth ? ["page scrolls sideways"] : []
    return [...cards.map((card) => card.textContent?.slice(0, 20) ?? "card"), ...sideways]
  })
  expect(overflow).toEqual([])
})
