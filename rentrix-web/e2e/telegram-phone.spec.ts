import { expect, test } from "@playwright/test"
import { freshLogin, pickRole, verifyPhone } from "./support"

/**
 * Phone code through the Telegram Gateway (D-146). The e2e stack runs the fake gateway
 * (`TELEGRAM_GATEWAY_FAKE=true`, dev profile), which hands each code to the admin relay instead of
 * Telegram, so `verifyPhone` reads it at `GET /admin/sms-relay` as it does an SMS code.
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

  await verifyPhone(page, `9${Date.now().toString().slice(-8)}1`)

  await expect(page).toHaveURL(/\/verify\/id/)
  await expect(page.getByText(/phone verified · \+91 xxxxxx\d{4}/i)).toBeVisible()
  await page.goto("/me/tenancies")
  await expect(page.getByRole("region", { name: "Verification" })).toHaveCount(0)
})
