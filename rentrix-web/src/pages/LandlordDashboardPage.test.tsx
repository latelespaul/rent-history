// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, render, screen, within } from "@testing-library/react"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { CurrentUser, PropertyReview } from "@/types/api"
import { detail, page } from "@/test/properties"
import LandlordDashboardPage from "./LandlordDashboardPage"

const api = vi.hoisted(() => ({
  user: null as unknown as CurrentUser,
  received: [] as PropertyReview[],
}))

vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({ state: { status: "signedIn", user: api.user } }),
}))
vi.mock("@/api/tenancies", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/tenancies")>()),
  fetchMyTenancies: vi.fn(async () => page([])),
}))
vi.mock("@/api/properties", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/properties")>()),
  fetchMyProperties: vi.fn(async () => page([detail({ status: "PUBLISHED" })])),
}))
vi.mock("@/api/reviews", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/reviews")>()),
  fetchMyWrittenReviews: vi.fn(async () => page([])),
  fetchMyReceivedReviews: vi.fn(async () => page(api.received)),
}))

const { fetchMyProperties } = await import("@/api/properties")
const { fetchMyReceivedReviews, fetchMyWrittenReviews } = await import("@/api/reviews")

const USER: CurrentUser = {
  id: "u-1",
  email: "demo@example.test",
  emailVerified: true,
  displayName: "Demo Person",
  shortName: "Demo P.",
  roles: ["TENANT"],
  status: "ACTIVE",
  demo: true,
  needsOnboarding: false,
  verification: {},
  createdAt: "2026-11-21T00:00:00Z",
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [{ path: "/me/dashboard", element: <LandlordDashboardPage /> }],
    {
      initialEntries: ["/me/dashboard"],
    },
  )
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  api.received = []
})
afterEach(cleanup)

describe("LandlordDashboardPage in Owner mode (PLAN R10)", () => {
  it("shows a tenant-only account how to add a home they own, without landlord calls", () => {
    api.user = USER
    renderPage()
    expect(screen.getByText("No homes you own yet")).toBeTruthy()
    expect(screen.getByTestId("owner-add-home").getAttribute("href")).toBe("/homes/new?own=1")
    expect(fetchMyProperties).not.toHaveBeenCalled()
  })

  it("lists a landlord's homes, the tenancies on them and both review lists", async () => {
    api.user = { ...USER, roles: ["TENANT", "LANDLORD"] }
    api.received = [
      {
        id: "r1",
        direction: "TENANT_TO_LANDLORD",
        status: "PUBLISHED",
        ratingOverall: 5,
        body: "Fixed the geyser the same day.",
        author: { label: "Tenant, 2024" },
        subject: { label: "Owner", shortName: "Demo P." },
        property: {
          id: "p1",
          buildingName: "Demo Towers E",
          locality: "Baner",
          city: { code: "PNQ", name: "Pune" },
        },
        verification: "UNVERIFIED",
      },
      { id: "r2", status: "AWAITING_REVEAL" },
    ]
    renderPage()
    expect(await screen.findByText("Tenancies on your homes (0)")).toBeTruthy()
    const received = await screen.findByTestId("owner-reviews-received")
    expect(await within(received).findByText("Fixed the geyser the same day.")).toBeTruthy()
    expect(within(received).getByText("By Tenant, 2024")).toBeTruthy()
    expect(within(received).getByTestId("my-review-pending")).toBeTruthy()
    expect(fetchMyReceivedReviews).toHaveBeenCalledWith("LANDLORD", expect.anything())
    expect(fetchMyWrittenReviews).toHaveBeenCalledWith("LANDLORD", expect.anything())
    expect(await screen.findByText("Reviews you wrote of tenants (0)")).toBeTruthy()
  })
})
