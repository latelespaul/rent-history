// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { PropertySearch } from "@/api/properties"
import type { CurrentUser } from "@/types/api"
import { LAUNCH_CITIES, SEED_STATS } from "@/test/cities"
import { card, detail, page } from "@/test/properties"
import { makeUser } from "@/test/users"
import TenancyRequestPage from "./TenancyRequestPage"

const api = vi.hoisted(() => ({
  user: null as unknown as CurrentUser,
  calls: [] as PropertySearch[],
}))

vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({ state: { status: "signedIn", user: api.user } }),
}))
vi.mock("@/api/cities", () => ({
  cityKeys: { all: ["cities"] },
  fetchCities: vi.fn(async () => LAUNCH_CITIES),
}))
vi.mock("@/api/stats", () => ({
  statsKeys: { all: ["stats"] },
  fetchStats: vi.fn(async () => SEED_STATS),
}))
vi.mock("@/api/properties", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/properties")>()),
  searchProperties: vi.fn(async (search: PropertySearch) => {
    api.calls.push(search)
    return page([card({ id: "home-1", buildingName: "Demo Residency A" })])
  }),
  fetchProperty: vi.fn(async (id: string) => detail({ id, buildingName: "Demo Residency A" })),
  fetchMyProperties: vi.fn(async () => page([])),
}))

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: "/tenancies/new", element: <TenancyRequestPage /> }], {
    initialEntries: [path],
  })
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

beforeEach(() => {
  configure({ asyncUtilTimeout: 3000 })
  window.sessionStorage.clear()
  api.user = makeUser({ roles: ["TENANT"] })
  api.calls = []
})
afterEach(cleanup)

describe("TenancyRequestPage: Which home did you rent? (captain 2026-11-28)", () => {
  it("asks a tenant for the home first, then opens the tenancy form for the picked one", async () => {
    const router = renderAt("/tenancies/new")
    expect(await screen.findByRole("heading", { name: "Which home did you rent?" })).toBeTruthy()
    // nothing is searched until the tenant asks
    expect(api.calls).toHaveLength(0)
    expect(screen.getByTestId("pick-add-home").getAttribute("href")).toBe("/homes/new")

    await userEvent.type(screen.getByLabelText("Area, society or building"), "Koramangala")
    await userEvent.click(screen.getByRole("button", { name: "Search" }))
    const result = await screen.findByTestId("pick-home-result")
    expect(api.calls.at(-1)).toMatchObject({ q: "Koramangala" })

    await userEvent.click(within(result).getByRole("link", { name: /Pick Demo Residency A/ }))
    expect(router.state.location.search).toBe("?property=home-1")
    expect(await screen.findByRole("heading", { name: "Record your tenancy" })).toBeTruthy()
    expect(screen.getByRole("form", { name: "Tenancy request" })).toBeTruthy()
  })

  it("records as the tenant for a landlord in Tenant mode (`?as=tenant`)", async () => {
    api.user = makeUser({ roles: ["TENANT", "LANDLORD"] })
    renderAt("/tenancies/new?as=tenant")
    expect(await screen.findByRole("heading", { name: "Which home did you rent?" })).toBeTruthy()
    expect(screen.queryByRole("heading", { name: "Invite a tenant" })).toBeNull()
  })
})
