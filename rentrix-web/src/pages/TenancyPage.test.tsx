// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { Tenancy } from "@/types/api"
import TenancyPage from "./TenancyPage"

const api = vi.hoisted(() => ({ tenancy: null as unknown as Tenancy }))

vi.mock("@/features/tenancy/useTenancies", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/tenancy/useTenancies")>()),
  useTenancy: () => ({ isPending: false, isError: false, data: api.tenancy }),
}))
vi.mock("@/features/tenancy/RentRecords", () => ({ RentRecords: () => null }))
vi.mock("@/features/tenancy/TenancyActions", () => ({ TenancyActions: () => null }))

/** The signed-in tenant's own tenancy, moved in long enough ago that the D-39 window is open. */
function myTenancy(overrides: Partial<Tenancy> = {}): Tenancy {
  return {
    id: "t1",
    property: {
      id: "00000000-0000-0000-0000-000000000001",
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

function renderPage(tenancy: Tenancy) {
  api.tenancy = tenancy
  const router = createMemoryRouter([{ path: "/tenancies/:id", element: <TenancyPage /> }], {
    initialEntries: ["/tenancies/t1"],
  })
  render(<RouterProvider router={router} />)
}

afterEach(cleanup)

describe("TenancyPage review call to action", () => {
  it("offers 'Write a review' before the tenant reviews the home", () => {
    renderPage(myTenancy({ reviewed: false, homeReviewStatus: null }))
    expect(screen.getByTestId("tenancy-write-review")).toBeTruthy()
    expect(screen.queryByTestId("tenancy-my-review")).toBeNull()
  })

  it("links to the published review on the home's page", () => {
    renderPage(myTenancy({ homeReviewStatus: "PUBLISHED" }))
    expect(screen.getByTestId("tenancy-my-review").getAttribute("href")).toBe(
      "/p/00000000-0000-0000-0000-000000000001#reviews",
    )
    expect(screen.queryByTestId("tenancy-write-review")).toBeNull()
  })

  it("shows neither once the home review is withdrawn or hidden, as it cannot be written again", () => {
    for (const status of ["WITHDRAWN", "HIDDEN"] as const) {
      renderPage(myTenancy({ homeReviewStatus: status }))
      expect(screen.queryByTestId("tenancy-my-review")).toBeNull()
      expect(screen.queryByTestId("tenancy-write-review")).toBeNull()
      cleanup()
    }
  })
})
