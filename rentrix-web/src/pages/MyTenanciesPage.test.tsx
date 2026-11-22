// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { ApiError } from "@/api/errors"
import type { CurrentUser, Tenancy } from "@/types/api"
import { page } from "@/test/properties"
import MyTenanciesPage from "./MyTenanciesPage"

const api = vi.hoisted(() => ({
  user: null as unknown as CurrentUser,
  tenancies: [] as Tenancy[],
  confirm: null as unknown as Error | null,
}))

vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({ state: { status: "signedIn", user: api.user } }),
}))
vi.mock("@/api/tenancies", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/tenancies")>()),
  fetchMyTenancies: vi.fn(async () => page(api.tenancies)),
  confirmTenancy: vi.fn(async () => {
    if (api.confirm) throw api.confirm
    return api.tenancies[0]
  }),
  declineTenancy: vi.fn(async () => api.tenancies[0]),
}))

const { declineTenancy } = await import("@/api/tenancies")

const LANDLORD: CurrentUser = {
  id: "u-landlord",
  email: "demo-landlord-pnq-1@example.test",
  emailVerified: true,
  displayName: "Demo Landlord PNQ-1",
  roles: ["LANDLORD"],
  status: "ACTIVE",
  demo: true,
  needsOnboarding: false,
  verification: { LANDLORD: "PENDING" },
  createdAt: "2026-11-21T00:00:00Z",
}

function tenancy(overrides: Partial<Tenancy> = {}): Tenancy {
  return {
    id: "t1",
    property: {
      id: "p1",
      buildingName: "Demo Towers E",
      locality: "Baner",
      city: { code: "PNQ", name: "Pune" },
      unitLabel: "D-504",
      propertyType: "APARTMENT",
    },
    myRole: "LANDLORD",
    landlord: { id: "u-landlord", shortName: "Demo L." },
    tenant: { id: "u-tenant", shortName: "Demo T." },
    inviteEmail: null,
    startDate: "2026-10-01",
    endDate: null,
    initialRentInr: 27000,
    currentRentInr: null,
    depositInr: 100000,
    depositRefundedInr: null,
    status: "PENDING_CONFIRMATION",
    initiatedBy: "TENANT",
    declineReason: null,
    evidenceLevel: null,
    createdAt: "2026-11-20T00:00:00Z",
    expiresAt: "2026-12-04T00:00:00Z",
    confirmedAt: null,
    endedAt: null,
    actions: { confirm: true, decline: true, cancel: false, end: false, addRentRecord: false },
    ...overrides,
  }
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: "/me/tenancies", element: <MyTenanciesPage /> }], {
    initialEntries: ["/me/tenancies"],
  })
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  configure({ asyncUtilTimeout: 3000 })
  api.user = LANDLORD
  api.tenancies = [
    tenancy(),
    tenancy({
      id: "t2",
      status: "ACTIVE",
      property: { ...tenancy().property, id: "p2", buildingName: "Demo Heights F" },
      actions: { confirm: false, decline: false, cancel: false, end: true, addRentRecord: true },
    }),
  ]
  api.confirm = null
})
afterEach(cleanup)

describe("MyTenanciesPage", () => {
  it("lists requests that need the caller first, then tenancies by state", async () => {
    renderPage()
    expect(await screen.findByText("Needs your action (1)")).toBeTruthy()
    expect(screen.getByRole("tab", { name: "Current (1)" }).getAttribute("aria-selected")).toBe(
      "true",
    )
    expect(screen.getByText("Demo Heights F")).toBeTruthy()
    expect(screen.getByRole("tab", { name: "Pending (1)" })).toBeTruthy()
  })

  it("sends a user without a verified phone to the phone step and back when a confirm is refused", async () => {
    api.confirm = new ApiError({
      status: 403,
      code: "id_verification_required",
      detail: "Verify your phone number to continue.",
      missing: ["phone"],
    })
    renderPage()
    await userEvent.click(await screen.findByRole("button", { name: /Confirm tenancy/ }))
    expect(await screen.findByText("Verify your phone to post")).toBeTruthy()
    expect(
      screen.getByRole("link", { name: "Verify your phone number" }).getAttribute("href"),
    ).toBe("/verify/phone?next=%2Fme%2Ftenancies")
  })

  it("lets a pending account decline with a reason, never gated", async () => {
    renderPage()
    await userEvent.click(await screen.findByRole("button", { name: /^Decline$/ }))
    await userEvent.type(screen.getByLabelText(/Reason/), "I never rented this home.")
    await userEvent.click(screen.getByRole("button", { name: "Decline request" }))
    expect(declineTenancy).toHaveBeenCalledWith("t1", "I never rented this home.")
  })
})
