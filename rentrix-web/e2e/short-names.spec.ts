import { expect, test } from "@playwright/test"
import { devLogin } from "./support"

// Click-test bug (c): every demo user showed as "Demo B." because the server took the initial of
// the last word ("BLR-1"). One rule on the server now skips code-like words, and the web shows the
// server's short name as-is: "Demo Landlord BLR-1" is "Demo L.", "Demo Tenant BLR-1" is "Demo T.".
test("public short names use the last real name word, not a code [click-test (c): short names]", async ({
  page,
}) => {
  const codeInitial = page.getByText(/^Demo B\.$/)

  await devLogin(page, "Demo Tenant BLR-1")
  await expect(page.getByRole("button", { name: /^Account menu for Demo T\./ })).toBeVisible()

  await page.goto("/search")
  const search = page.getByRole("search")
  await search.getByRole("combobox", { name: "Locality" }).fill("Koramangala")
  await search.getByRole("button", { name: "Search" }).click()
  await expect(page.getByText("Demo L.", { exact: true }).first()).toBeVisible()
  await expect(codeInitial).toHaveCount(0)

  await page.getByRole("link", { name: "Demo Greens · Tower 1 · Wing A", exact: true }).click()
  const reviews = page.getByRole("region", { name: /what tenants say about the home/i })
  await expect(reviews.getByRole("listitem").first()).toBeVisible()
  await expect(reviews.getByText("Demo T.", { exact: true }).first()).toBeVisible()
  await expect(codeInitial).toHaveCount(0)
})
