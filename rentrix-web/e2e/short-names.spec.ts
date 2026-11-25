import { expect, test } from "@playwright/test"
import { devLogin } from "./support"

// Click-test bug (c): every demo user showed as "Demo B." because the server took the initial of
// the last word ("BLR-1"). One rule on the server now skips code-like words: "Demo Tenant BLR-1"
// is "Demo T.". Since D-168 only the user sees their own short name; other people see "Owner" or
// "Tenant, <year>", so search cards and reviews show no short name at all.
test("a user's own short name uses the last real name word; others see labels [click-test (c): short names]", async ({
  page,
}) => {
  const codeInitial = page.getByText(/^Demo B\.$/)

  await devLogin(page, "Demo Tenant BLR-1")
  await expect(page.getByRole("button", { name: /^Account menu for Demo T\./ })).toBeVisible()

  await page.goto("/search")
  const search = page.getByRole("search")
  await search.getByRole("combobox", { name: "Locality" }).fill("Koramangala")
  await search.getByRole("button", { name: "Search" }).click()
  const main = page.locator("main")
  await expect(main.locator("article").first()).toBeVisible()
  await expect(main.getByText("Owner", { exact: true }).first()).toBeVisible()
  await expect(main).not.toContainText(/Demo [A-Z]\./)
  await expect(codeInitial).toHaveCount(0)
})
