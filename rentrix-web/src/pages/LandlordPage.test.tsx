// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, render, screen, within } from "@testing-library/react"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { UserProfile } from "@/types/api"
import { makeUser } from "@/test/users"

const PROFILE: UserProfile = {
  id: "l5",
  label: "Owner",
  memberSince: "2024-01",
  listedHomes: 2,
  landlordRating: {
    reviewCount: 4,
    average: 4.3,
    criteria: [
      { criterion: "RESPONSIVENESS", ratingCount: 4, average: 4.5 },
      { criterion: "DEPOSIT_RETURN", ratingCount: 2 },
      { criterion: "TRANSPARENCY", ratingCount: 0 },
      { criterion: "PRIVACY", ratingCount: 3, average: 3.7 },
      { criterion: "BEHAVIOUR", ratingCount: 4, average: 4.0 },
    ],
    recommend: { answered: 4, yes: 3 },
  },
}

vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({ state: { status: "signedIn", user: makeUser() }, acceptSession: vi.fn() }),
}))
vi.mock("@/api/reviews", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/reviews")>()),
  fetchUserProfile: vi.fn(async () => PROFILE),
  fetchUserReviews: vi.fn(async () => ({
    items: [
      {
        id: "r1",
        direction: "TENANT_TO_LANDLORD",
        status: "PUBLISHED",
        ratingOverall: 4,
        criteria: { RESPONSIVENESS: 5, PRIVACY: 4 },
        wouldRecommend: true,
        author: { label: "Verified tenant" },
        verification: "UNVERIFIED",
      },
    ],
    page: { number: 0, size: 50, totalItems: 1, totalPages: 1 },
  })),
}))

const { default: LandlordPage } = await import("./LandlordPage")

afterEach(() => cleanup())

describe("landlord page (D-158)", () => {
  it("shows the landlord's per-parameter averages from 3 ratings and their reviews", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const router = createMemoryRouter([{ path: "/u/:id", element: <LandlordPage /> }], {
      initialEntries: ["/u/l5"],
    })
    render(
      <QueryClientProvider client={client}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    )

    const breakdown = await screen.findByTestId("landlord-breakdown")
    expect(within(breakdown).getByTestId("landlord-breakdown-RESPONSIVENESS").textContent).toMatch(
      /responsiveness.*4\.5/i,
    )
    expect(within(breakdown).getByTestId("landlord-breakdown-DEPOSIT_RETURN").textContent).toMatch(
      /new · 2 ratings/i,
    )
    expect(within(breakdown).getByTestId("landlord-breakdown-TRANSPARENCY").textContent).toMatch(
      /no ratings yet/i,
    )
    expect(within(breakdown).getByTestId("landlord-breakdown-recommend").textContent).toBe(
      "75% would recommend",
    )
    const review = await screen.findByTestId("property-review")
    expect(within(review).getByText("Would recommend")).toBeTruthy()
    expect(within(review).getByTestId("tick-unverified")).toBeTruthy()
    expect(within(review).getByTestId("review-criteria").textContent).toMatch(
      /ratings by topic \(2\)/i,
    )
  })
})
