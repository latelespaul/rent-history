// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen } from "@testing-library/react"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { ApiError } from "@/api/errors"
import type { CurrentUser, PropertyDetail, PropertyReview, RentHistoryRow } from "@/types/api"
import { detail } from "@/test/properties"
import PropertyPage from "./PropertyPage"

const api = vi.hoisted(() => ({
  user: null as CurrentUser | null,
  property: null as unknown as PropertyDetail | Error,
  rent: null as RentHistoryRow[] | null,
  reviews: null as PropertyReview[] | null,
}))

// The restored session: anonymous (null) or the signed-in user.
vi.mock("@/lib/session", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/session")>()),
  useSession: () => ({ isPending: false, data: api.user }),
}))
vi.mock("@/api/properties", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/properties")>()),
  fetchProperty: vi.fn(async () => {
    if (api.property instanceof Error) throw api.property
    return api.property
  }),
  fetchRentHistory: vi.fn(async () => api.rent),
  fetchPropertyReviews: vi.fn(async () => api.reviews),
}))

const { fetchPropertyReviews, fetchRentHistory } = await import("@/api/properties")

const TENANT: CurrentUser = {
  id: "u1",
  email: "demo-tenant-blr-1@example.test",
  displayName: "Demo Tenant BLR-1",
  roles: ["TENANT"],
  demo: true,
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: "/p/:id", element: <PropertyPage /> }], {
    initialEntries: ["/p/00000000-0000-0000-0000-000000000001"],
  })
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  api.user = null
  api.property = detail()
  api.rent = null
  api.reviews = null
})
afterEach(cleanup)
// Generous waits: the page resolves several queries before it settles.
configure({ asyncUtilTimeout: 10000 })

describe("PropertyPage", () => {
  it("shows an anonymous visitor facts and ratings but no review text or rent history", async () => {
    renderPage()

    expect(await screen.findByRole("heading", { level: 1, name: /Demo Residency A/ })).toBeTruthy()
    expect(screen.getByText("Semi-furnished")).toBeTruthy()
    expect(screen.getAllByLabelText("Rated 4.3 out of 5 from 4 reviews").length).toBeGreaterThan(0)
    expect(screen.getByText("Sign in to read 4 reviews from verified tenants.")).toBeTruthy()
    expect(screen.getByText(/Sign in to see the full rent history/)).toBeTruthy()
    expect(fetchPropertyReviews).not.toHaveBeenCalled()
    expect(fetchRentHistory).not.toHaveBeenCalled()
    expect(screen.getByText(/Flat number visible only to the landlord and tenants/)).toBeTruthy()
  })

  it("shows a signed-in user review text and rent history when the API has them", async () => {
    api.user = TENANT
    api.rent = [
      { from: "2024-05", to: null, monthlyRent: 36500 },
      { from: "2023-04", to: "2024-03", monthlyRent: 34000 },
    ]
    api.reviews = [{ id: "r1", rating: 4, text: "Water supply was steady.", authorName: "Demo T." }]
    renderPage()

    expect(await screen.findByText("Water supply was steady.")).toBeTruthy()
    expect(screen.getByText("₹36,500")).toBeTruthy()
    expect(screen.getByText("+7.4%")).toBeTruthy()
    expect(screen.queryByText(/Sign in to read/)).toBeNull()
  })

  it("shows clear empty states while the API does not serve rent history or reviews yet", async () => {
    api.user = TENANT
    renderPage()

    expect(await screen.findByText("Rent history is not available yet")).toBeTruthy()
    expect(await screen.findByText("Reviews are not available yet")).toBeTruthy()
  })

  it("shows the flat number only when the API sends it", async () => {
    api.user = TENANT
    api.property = detail({ unitLabel: "3B" })
    renderPage()

    expect(await screen.findByText(/Flat 3B/)).toBeTruthy()
  })

  it("renders not-found for an unknown home", async () => {
    api.property = new ApiError({ status: 404, code: "not_found" })
    renderPage()

    expect(await screen.findByText("This home is not on Rentrix")).toBeTruthy()
  })
})
