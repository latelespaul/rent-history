// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen } from "@testing-library/react"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { ApiError } from "@/api/errors"
import type {
  CurrentUser,
  PropertyDetail,
  PropertyReview,
  RentHistoryRow,
  Tenancy,
} from "@/types/api"
import { detail, page } from "@/test/properties"
import PropertyPage from "./PropertyPage"

const api = vi.hoisted(() => ({
  user: null as CurrentUser | null,
  property: null as unknown as PropertyDetail | Error,
  rent: null as RentHistoryRow[] | null,
  reviews: null as PropertyReview[] | null,
  tenancies: [] as Tenancy[],
}))

// The restored session: anonymous (null) or the signed-in user.
vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({
    state: api.user ? { status: "signedIn", user: api.user } : { status: "anonymous", user: null },
  }),
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

vi.mock("@/api/tenancies", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/tenancies")>()),
  fetchMyTenancies: vi.fn(async () => page(api.tenancies)),
}))

const { fetchPropertyReviews, fetchRentHistory } = await import("@/api/properties")

const HOME_ID = "00000000-0000-0000-0000-000000000001"

/** The signed-in tenant's own tenancy on the page's home. */
function myTenancy(overrides: Partial<Tenancy> = {}): Tenancy {
  return {
    id: "t1",
    property: {
      id: HOME_ID,
      buildingName: "Demo Residency A",
      locality: "Koramangala",
      city: { code: "BLR", name: "Bangalore" },
      unitLabel: "C202",
      propertyType: "APARTMENT",
    },
    myRole: "TENANT",
    landlord: { id: "u-landlord", shortName: "Demo L." },
    tenant: { id: "u1", shortName: "Demo T." },
    inviteEmail: null,
    startDate: "2026-07-01",
    endDate: null,
    initialRentInr: 27000,
    currentRentInr: null,
    currentRentFrom: null,
    depositInr: null,
    depositRefundedInr: null,
    status: "ACTIVE",
    initiatedBy: "TENANT",
    declineReason: null,
    evidenceLevel: null,
    createdAt: "2026-11-20T00:00:00Z",
    expiresAt: null,
    confirmedAt: null,
    endedAt: null,
    actions: { confirm: false, decline: false, cancel: false, end: true, addRentRecord: true },
    reviewed: true,
    ...overrides,
  }
}

const TENANT: CurrentUser = {
  id: "u1",
  email: "demo-tenant-blr-1@example.test",
  emailVerified: true,
  displayName: "Demo Tenant BLR-1",
  shortName: "Demo T.",
  roles: ["TENANT"],
  status: "ACTIVE",
  demo: true,
  needsOnboarding: false,
  verification: { TENANT: "APPROVED" },
  createdAt: "2026-11-21T00:00:00Z",
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
  api.tenancies = []
})
afterEach(cleanup)
// Generous waits: the page resolves several queries before it settles.
configure({ asyncUtilTimeout: 10000 })

describe("PropertyPage", () => {
  it("shows an anonymous visitor facts, ratings and the review text, but no rent history", async () => {
    api.reviews = [
      {
        id: "r1",
        ratingOverall: 4,
        body: "Water supply was steady.",
        author: { shortName: "Demo T." },
        actions: { edit: false, withdraw: false, respond: false, editResponse: false },
      },
    ]
    renderPage()

    expect(await screen.findByRole("heading", { level: 1, name: /Demo Residency A/ })).toBeTruthy()
    expect(screen.getByText("Semi-furnished")).toBeTruthy()
    expect(screen.getAllByLabelText("Rated 4.3 out of 5 from 4 reviews").length).toBeGreaterThan(0)
    // Home reviews are public with full text (captain 2026-11-24 00:31, D-161).
    expect(await screen.findByText("Water supply was steady.")).toBeTruthy()
    expect(screen.queryByText(/Sign in to read/)).toBeNull()
    expect(screen.queryByTestId("my-review")).toBeNull()
    expect(screen.queryByRole("button", { name: /Edit my review/ })).toBeNull()
    expect(screen.queryByTestId("i-rent-this-home")).toBeNull()
    expect(screen.getByText(/Sign in to see the full rent history/)).toBeTruthy()
    expect(fetchPropertyReviews).toHaveBeenCalled()
    expect(fetchRentHistory).not.toHaveBeenCalled()
    expect(screen.getByText(/Flat number visible only to the landlord and tenants/)).toBeTruthy()
  })

  it("shows a signed-in user review text and rent history when the API has them", async () => {
    api.user = TENANT
    api.rent = [
      { fromMonth: "2024-05", toMonth: null, monthlyRentInr: 36500, share: false },
      { fromMonth: "2023-04", toMonth: "2024-03", monthlyRentInr: 34000, share: false },
    ]
    api.reviews = [
      {
        id: "r1",
        ratingOverall: 4,
        body: "Water supply was steady.",
        author: { shortName: "Demo T." },
      },
    ]
    renderPage()

    expect(await screen.findByText("Water supply was steady.")).toBeTruthy()
    expect(screen.getByText("₹36,500")).toBeTruthy()
    expect(screen.getByText("+7.4%")).toBeTruthy()
    expect(screen.queryByText(/Sign in to read/)).toBeNull()
  })

  it("offers 'I rent or rented this home' only to a signed-in user with no tenancy here", async () => {
    api.user = TENANT
    renderPage()
    expect(await screen.findByTestId("i-rent-this-home")).toBeTruthy()
    cleanup()

    // Captain 2026-11-24 00:31: a tenant of this home gets their tenancy and review instead.
    api.tenancies = [myTenancy()]
    api.reviews = [
      {
        id: "r1",
        ratingOverall: 4,
        body: "Water supply was steady.",
        author: { shortName: "Demo T." },
        actions: { edit: true, withdraw: true, respond: false, editResponse: false },
      },
    ]
    renderPage()
    expect(await screen.findByTestId("home-my-tenancy")).toBeTruthy()
    expect(screen.getByTestId("home-my-review")).toBeTruthy()
    expect(await screen.findByTestId("my-review")).toBeTruthy()
    expect(screen.getByRole("button", { name: /Edit my review/ })).toBeTruthy()
    expect(screen.queryByTestId("i-rent-this-home")).toBeNull()
    cleanup()

    // A declined request does not count as living here.
    api.tenancies = [myTenancy({ status: "DECLINED", reviewed: false })]
    renderPage()
    expect(await screen.findByTestId("i-rent-this-home")).toBeTruthy()
  })

  it("shows the landlord's green tick on the landlord card, or that they are not verified yet", async () => {
    api.property = detail({
      landlord: { shortName: "Demo L.", rating: { reviewCount: 2, average: null }, verified: true },
    })
    renderPage()

    // The rating card renders twice: once for small screens, once in the wide-screen aside.
    expect(await screen.findAllByText("Verified landlord")).toHaveLength(2)
    cleanup()

    api.property = detail()
    renderPage()

    expect(await screen.findAllByText("Landlord not verified yet")).toHaveLength(2)
    expect(screen.queryByText("Verified landlord")).toBeNull()
  })

  it("keeps a tenant's one-sided rent row out of the confirmed history's change", async () => {
    api.user = TENANT
    api.rent = [
      { fromMonth: "2026-01", toMonth: null, monthlyRentInr: 22000, share: false, confirmed: true },
      {
        fromMonth: "2025-06",
        toMonth: null,
        monthlyRentInr: 50000,
        share: false,
        confirmed: false,
      },
      {
        fromMonth: "2025-01",
        toMonth: "2025-12",
        monthlyRentInr: 20000,
        share: false,
        confirmed: true,
      },
    ]
    renderPage()

    expect(await screen.findByText("+10.0%")).toBeTruthy()
    expect(screen.getByText("–")).toBeTruthy()
    expect(screen.getByText("first record")).toBeTruthy()
    expect(screen.getByText("Confirmed tenancies")).toBeTruthy()
    expect(screen.getByText("Some rows added by a tenant, not verified yet")).toBeTruthy()
    expect(screen.getByText("added by the tenant · not verified yet")).toBeTruthy()
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

    expect(await screen.findByText("This home is not on Doorway Living")).toBeTruthy()
  })
})
