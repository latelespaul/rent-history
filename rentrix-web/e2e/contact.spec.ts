import { expect, test, type Page } from "@playwright/test"
import { devLogin } from "./support"

/**
 * The Contact / About page (`/contact`), at 390 px and desktop:
 * - open signed out, and linked as "Contact us" from the footer and the account menu;
 * - the email is a mailto link and LinkedIn opens the company page; social entries with no URL
 *   yet (Instagram, X) are not drawn; the short message form is on the page;
 * - the page never scrolls sideways. It is attached as a screenshot for a visual check.
 */

async function expectContactPage(page: Page) {
  await expect(page).toHaveURL(/\/contact$/)
  const main = page.getByRole("main")
  await expect(main.getByRole("heading", { level: 1, name: "Contact us" })).toBeVisible()
  await expect(main.getByRole("link", { name: "hello@doorwayliving.in" })).toHaveAttribute(
    "href",
    "mailto:hello@doorwayliving.in",
  )
  const linkedin = main.getByRole("link", { name: /on LinkedIn/ })
  await expect(linkedin).toHaveAttribute("href", "https://www.linkedin.com/company/doorwayliving/")
  await expect(linkedin).toHaveAttribute("target", "_blank")
  await expect(main.getByTestId("contact-social")).toHaveCount(1)
  await expect(main.getByText(/Instagram|Twitter/)).toHaveCount(0)
  await expect(main.getByTestId("feedback-form")).toBeVisible()
  await expect(main.getByTestId("contact-cities")).toContainText("Bangalore")
  await expect(main.getByText("Not verified yet")).toBeVisible()
}

test("a signed-out visitor opens /contact from the footer", async ({ page }, testInfo) => {
  await page.goto("/search")
  await page.getByRole("contentinfo").getByRole("link", { name: "Contact us" }).click()
  await expectContactPage(page)

  // Reached directly too, with no sign-in redirect.
  await page.goto("/contact")
  await expectContactPage(page)
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  )
  expect(overflow, "no sideways scroll").toBeLessThanOrEqual(0)
  await testInfo.attach("contact-signed-out", {
    body: await page.screenshot({ fullPage: true }),
    contentType: "image/png",
  })
})

test("a signed-in user opens /contact from the account menu", async ({ page }) => {
  await devLogin(page, "Demo Tenant BLR-1")
  await page.goto("/search")
  await page
    .getByRole("banner")
    .getByRole("button", { name: /^Account menu for/ })
    .click()
  await page.getByRole("menuitem", { name: "Contact us" }).click()
  await expectContactPage(page)
})
