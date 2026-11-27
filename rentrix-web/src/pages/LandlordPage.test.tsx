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

const TENANT_PROFILE: UserProfile = {
  id: "t7",
  label: "Tenant",
  memberSince: "2023-06",
  tenantRating: {
    reviewCount: 3,
    average: 4.7,
    criteria: [
      { criterion: "RENT_ON_TIME", ratingCount: 3, average: 4.7 },
      { criterion: "CARE_OF_HOME", ratingCount: 3, average: 4.3 },
      { criterion: "COMMUNICATION", ratingCount: 3, average: 4.7 },
      { criterion: "HOUSE_RULES", ratingCount: 2 },
      { criterion: "MOVE_OUT", ratingCount: 0 },
    ],
    recommend: { answered: 3, yes: 3 },
  },
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: "/u/:id", element: <LandlordPage /> }], {
    initialEntries: [path],
  })
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

describe("tenant profile (landlord-adds-tenant S2, D-175)", () => {
  it("shows an owner the rating as tenant, the topics and what owners say", async () => {
    const api = await import("@/api/reviews")
    vi.mocked(api.fetchUserProfile).mockResolvedValueOnce(TENANT_PROFILE)
    vi.mocked(api.fetchUserReviews).mockImplementation(async (_id, as) => ({
      items:
        as === "TENANT"
          ? [
              {
                id: "r9",
                direction: "LANDLORD_TO_TENANT",
                status: "PUBLISHED",
                ratingOverall: 5,
                criteria: { RENT_ON_TIME: 5, MOVE_OUT: 4 },
                wouldRecommend: true,
                body: "Rent always came on time.",
                author: { label: "Owner" },
                verification: "UNVERIFIED",
              },
            ]
          : [],
      page: { number: 0, size: 50, totalItems: as === "TENANT" ? 1 : 0, totalPages: 1 },
    }))
    renderAt("/u/t7")

    const breakdown = await screen.findByTestId("tenant-breakdown")
    expect(within(breakdown).getByText("The tenant by topic")).toBeTruthy()
    expect(within(breakdown).getByTestId("tenant-breakdown-RENT_ON_TIME").textContent).toMatch(
      /paid rent on time.*4\.7/i,
    )
    expect(within(breakdown).getByTestId("tenant-breakdown-recommend").textContent).toBe(
      "100% would rent to them again",
    )
    expect(screen.getByText("Rating as tenant")).toBeTruthy()
    expect(screen.queryByTestId("landlord-rating")).toBeNull()
    const reviews = screen.getByTestId("tenant-reviews")
    expect(within(reviews).getByText("What owners say")).toBeTruthy()
    const review = await within(reviews).findByTestId("property-review")
    expect(within(review).getByText("Would rent to them again")).toBeTruthy()
    expect(within(review).getByText("Rent always came on time.")).toBeTruthy()
    expect(api.fetchUserReviews).toHaveBeenCalledWith("t7", "TENANT", expect.anything())
  })

  it("shows the tenant their own profile with no owner reviews and no rating", async () => {
    const api = await import("@/api/reviews")
    vi.mocked(api.fetchUserProfile).mockResolvedValueOnce({
      id: "t7",
      label: "Tenant",
      shortName: "Tara T.",
      memberSince: "2023-06",
    })
    vi.mocked(api.fetchUserReviews).mockClear()
    renderAt("/u/t7")

    expect(await screen.findByText("Tara T. (you)")).toBeTruthy()
    expect(screen.getByTestId("own-tenant-reviews-note")).toBeTruthy()
    expect(screen.queryByTestId("tenant-rating")).toBeNull()
    expect(screen.queryByTestId("tenant-reviews")).toBeNull()
    expect(api.fetchUserReviews).not.toHaveBeenCalled()
  })
})
