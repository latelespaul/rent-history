// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, fireEvent, render, screen, within } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import type { PropertyReview, RatingSummary } from "@/types/api"
import { RECORDED_REVIEWS } from "@/test/reviews"
import { PropertyReviews } from "./PropertyReviews"

const api = vi.hoisted(() => ({
  reviews: [] as PropertyReview[],
  total: undefined as number | undefined,
}))

vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({ state: { status: "signedIn", user: { id: "landlord-1" } } }),
}))
vi.mock("@/api/properties", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/properties")>()),
  fetchPropertyReviews: vi.fn(async () => ({
    items: api.reviews,
    total: api.total ?? api.reviews.length,
  })),
  respondToReview: vi.fn(async () => ({})),
}))

const { respondToReview } = await import("@/api/properties")

function renderReviews(rating: RatingSummary = { reviewCount: 3, average: 4.7 }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <PropertyReviews propertyId="01a20329-dcd1-7a5a-8ab0-1c35736d3aaa" rating={rating} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  api.reviews = RECORDED_REVIEWS
  api.total = undefined
})
afterEach(cleanup)
configure({ asyncUtilTimeout: 10000 })

describe("PropertyReviews", () => {
  it("renders the author, stars, text, month and landlord reply from the recorded API response", async () => {
    renderReviews()

    const items = await screen.findAllByRole("listitem")
    expect(items).toHaveLength(2)
    const [answered, open] = items
    expect(within(answered).getByTestId("review-author").textContent).toBe("Verified tenant")
    expect(within(answered).getByText("Reply from the owner")).toBeTruthy()
    expect(within(answered).getByRole("img", { name: "Rated 5 out of 5" })).toBeTruthy()
    expect(within(answered).getByText(/Well maintained building/)).toBeTruthy()
    expect(within(answered).getByTestId("review-dates").textContent).toBe("Published Jun 2024")
    expect(within(answered).queryByTestId("review-stay-length")).toBeNull()
    expect(within(answered).getByText(/The water pump was replaced/)).toBeTruthy()
    expect(within(answered).queryByRole("button", { name: "Respond" })).toBeNull()
    expect(within(open).getByTestId("review-stay-length").textContent).toBe("Stayed 2+ yrs")
    // D-171: a current stay's review shows the year only, never the month.
    expect(within(open).getByTestId("review-dates").textContent).toBe("Published 2024")
    expect(within(open).getByRole("button", { name: "Respond" })).toBeTruthy()
    // D-169: no stay dates and no year on any card the landlord sees.
    for (const item of items) {
      expect(item.textContent).not.toMatch(/Stayed \w{3} \d{4}|Tenant, \d{4}|\d{4} [–-] now/)
    }
  })

  it("shows each reviewer by label with the badges they hold, never by name (D-168)", async () => {
    const [first, second] = RECORDED_REVIEWS
    api.reviews = [
      {
        ...first,
        author: {
          label: "Verified tenant",
          badges: { google: true, id: true, agreement: true },
        },
      },
      { ...second, author: { label: "Tenant", badges: { google: true, id: false } } },
    ]
    renderReviews()

    const [all, some] = await screen.findAllByRole("listitem")
    expect(within(all).getByTestId("review-author").textContent).toBe("Verified tenant")
    expect(within(all).getByText("Google verified")).toBeTruthy()
    expect(within(all).getByText("ID verified")).toBeTruthy()
    expect(within(all).getByText("Agreement verified")).toBeTruthy()
    expect(within(some).getByTestId("review-author").textContent).toBe("Tenant")
    expect(within(some).getByText("Google verified")).toBeTruthy()
    expect(within(some).queryByText("ID verified")).toBeNull()
    // No agreement status in the data: no badge slot at all.
    expect(within(some).queryByText("Agreement verified")).toBeNull()
    // The person tick is gone: the three badges replace it.
    expect(screen.queryByText("Tenant not verified yet")).toBeNull()
  })

  it("counts every listed review and says how many are verified, never 0 above a listed one", async () => {
    // Two reviews listed, neither verified yet: the rating counts verified ones only (D-163).
    renderReviews({ reviewCount: 0, average: null })
    await screen.findAllByRole("listitem")
    expect(screen.getByTestId("reviews-count").textContent).toBe("2 reviews · 0 verified")
  })

  it("keeps the plain count when every listed review is verified", async () => {
    renderReviews({ reviewCount: 2, average: null })
    await screen.findAllByRole("listitem")
    expect(screen.getByTestId("reviews-count").textContent).toBe("2 reviews")
  })

  it("counts every published review across pages, not only the first page", async () => {
    // The API pages reviews 20 at a time; 30 published, 15 verified must read "30 reviews".
    api.total = 30
    renderReviews({ reviewCount: 15, average: 4.2 })
    await screen.findAllByRole("listitem")
    expect(screen.getByTestId("reviews-count").textContent).toBe("30 reviews · 15 verified")
  })

  it("hides Respond when the API does not offer it", async () => {
    api.reviews = RECORDED_REVIEWS.map((r) => ({
      ...r,
      actions: { edit: false, withdraw: false, respond: false, editResponse: false },
    }))
    renderReviews()

    await screen.findAllByRole("listitem")
    expect(screen.queryByRole("button", { name: "Respond" })).toBeNull()
  })

  it("posts the landlord's reply for the review", async () => {
    renderReviews()

    fireEvent.click(await screen.findByRole("button", { name: "Respond" }))
    fireEvent.change(screen.getByLabelText(/Your public reply/), {
      target: { value: "  Thanks, the lift was fixed.  " },
    })
    fireEvent.click(screen.getByRole("button", { name: "Post reply" }))

    await vi.waitFor(() =>
      expect(respondToReview).toHaveBeenCalledWith(
        "01a20329-e1a9-7c07-8522-df79d571f05b",
        "Thanks, the lift was fixed.",
      ),
    )
  })
})
