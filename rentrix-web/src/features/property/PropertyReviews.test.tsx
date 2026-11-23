// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, fireEvent, render, screen, within } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import type { PropertyReview } from "@/types/api"
import { RECORDED_REVIEWS } from "@/test/reviews"
import { PropertyReviews } from "./PropertyReviews"

const api = vi.hoisted(() => ({ reviews: [] as PropertyReview[] }))

vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({ state: { status: "signedIn", user: { id: "landlord-1" } } }),
}))
vi.mock("@/api/properties", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/properties")>()),
  fetchPropertyReviews: vi.fn(async () => api.reviews),
  respondToReview: vi.fn(async () => ({})),
}))

const { respondToReview } = await import("@/api/properties")

function renderReviews() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <PropertyReviews
          propertyId="01a20329-dcd1-7a5a-8ab0-1c35736d3aaa"
          rating={{ reviewCount: 3, average: 4.7 }}
          signedIn
        />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  api.reviews = RECORDED_REVIEWS
})
afterEach(cleanup)
configure({ asyncUtilTimeout: 10000 })

describe("PropertyReviews", () => {
  it("renders the author, stars, text, stay and landlord reply from the recorded API response", async () => {
    renderReviews()

    const items = await screen.findAllByRole("listitem")
    expect(items).toHaveLength(2)
    const [answered, open] = items
    expect(within(answered).getByText("Demo B.")).toBeTruthy()
    expect(within(answered).getByRole("img", { name: "Rated 5 out of 5" })).toBeTruthy()
    expect(within(answered).getByText(/Well maintained building/)).toBeTruthy()
    expect(within(answered).getByText(/stayed Mar 2024 – now/)).toBeTruthy()
    expect(within(answered).getByText(/The water pump was replaced/)).toBeTruthy()
    expect(within(answered).queryByRole("button", { name: "Respond" })).toBeNull()
    expect(within(open).getByText(/stayed Jun 2022 – Jan 2024/)).toBeTruthy()
    expect(within(open).getByRole("button", { name: "Respond" })).toBeTruthy()
  })

  it("shows each reviewer's green tick and public ID badges, or that they are not verified yet", async () => {
    const [first, second] = RECORDED_REVIEWS
    api.reviews = [
      {
        ...first,
        author: {
          shortName: "Demo B.",
          verified: true,
          badges: { idVerified: true, linkedinVerified: false },
        },
      },
      { ...second, author: { shortName: "Demo C.", verified: false } },
    ]
    renderReviews()

    const [verified, unverified] = await screen.findAllByRole("listitem")
    expect(within(verified).getByText("Verified tenant")).toBeTruthy()
    expect(within(verified).getByText("ID verified")).toBeTruthy()
    expect(within(verified).queryByText("LinkedIn verified")).toBeNull()
    expect(within(unverified).getByText("Tenant not verified yet")).toBeTruthy()
    expect(within(unverified).queryByText("Verified tenant")).toBeNull()
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
