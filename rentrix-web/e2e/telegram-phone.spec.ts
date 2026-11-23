import { expect, test } from "@playwright/test"
import { freshLogin, pickRole, verifyPhone, testPhone } from "./support"

/**
 * The phone step offers Telegram first (D-146). The number is a test one (+91 99999 0xxxx), so the
 * code never goes to Telegram or SMS, even through the fake gateway: it is held for the admin relay,
 * where `verifyPhone` reads it. The Telegram send itself is covered by `TelegramPhoneCodeTest`.
 */
test("a new user gets the phone code on Telegram by default and the phone counts as verified", async ({
  page,
}) => {
  await freshLogin(page)
  await pickRole(page, "I rent a home")
  await page.goto("/verify/phone")

  await expect(page.getByText("We send the code to your Telegram.")).toBeVisible()
  await expect(page.getByRole("button", { name: "Send code to Telegram" })).toBeVisible()
  // SMS stays the fallback for a number without Telegram.
  await expect(
    page.getByRole("button", { name: /no telegram\? get the code by sms/i }),
  ).toBeVisible()

  await verifyPhone(page, testPhone())

  await expect(page).toHaveURL(/\/verify\/id/)
  await expect(page.getByText(/phone verified · \+91 xxxxxx\d{4}/i)).toBeVisible()
  await page.goto("/me/tenancies")
  await expect(page.getByRole("region", { name: "Verification" })).toHaveCount(0)
})
