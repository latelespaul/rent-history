// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { ApiError } from "@/api/errors"
import type { ReviewTask, Tenancy } from "@/types/api"
import { makeUser } from "@/test/users"

// The review form (captain 2026-11-23 21:29, D-157): a tenancy whose landlord is on the platform.
const TENANCY = {
  id: "t5",
  property: {
    id: "p5",
    buildingName: "Demo Villa C",
    locality: "Whitefield",
    city: { code: "BLR", name: "Bangalore" },
    unitLabel: null,
    propertyType: "VILLA",
  },
  myRole: "TENANT",
  landlord: { id: "l5", shortName: "Demo L." },
  tenant: { id: "u1", shortName: "Asha R." },
  startDate: "2024-05-01",
  endDate: null,
  status: "ACTIVE",
} as unknown as Tenancy

const task = (direction: ReviewTask["direction"]): ReviewTask => ({
  tenancyId: "t5",
  direction,
  property: TENANCY.property,
  period: { from: "2024-05" },
  otherSideWaiting: true,
})

vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({ state: { status: "signedIn", user: makeUser() }, acceptSession: vi.fn() }),
}))
vi.mock("@/api/tenancies", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/tenancies")>()),
  fetchTenancy: vi.fn(async () => TENANCY),
}))
vi.mock("@/api/reviews", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/reviews")>()),
  fetchReviewTasks: vi.fn(async () => [task("TENANT_TO_LANDLORD"), task("TENANT_TO_PROPERTY")]),
  submitReviewForm: vi.fn(),
}))

const reviews = await import("@/api/reviews")
const { default: WriteReviewPage } = await import("./WriteReviewPage")

configure({ asyncUtilTimeout: 10_000 })
vi.setConfig({ testTimeout: 120_000 })

function renderForm() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [{ path: "/tenancies/:id/review", element: <WriteReviewPage /> }],
    { initialEntries: ["/tenancies/t5/review"] },
  )
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.clearAllMocks()
})

describe("review form", () => {
  it("rates the home and the landlord, checks, and submits both", async () => {
    vi.mocked(reviews.submitReviewForm).mockResolvedValue({
      home: { id: "r1", status: "PUBLISHED", ratingOverall: 4, verification: "UNVERIFIED" },
      landlord: { id: "r2", status: "AWAITING_REVEAL", ratingOverall: 2 },
    })
    renderForm()
    const user = userEvent.setup({ delay: null })

    expect(await screen.findByTestId("review-period")).toBeTruthy()
    expect(screen.getByTestId("review-progress").textContent).toMatch(/step 1 of 5/i)
    await user.click(screen.getByTestId("review-next"))

    await user.click(screen.getByTestId("rate-WATER_SUPPLY-5"))
    await user.click(screen.getByTestId("rate-PARKING-3"))
    // tapping the picked star again clears it: the parameter stays optional
    await user.click(screen.getByTestId("rate-NOISE-2"))
    await user.click(screen.getByTestId("rate-NOISE-2"))
    await user.click(screen.getByTestId("review-next"))

    expect(screen.getByTestId("review-progress").textContent).toMatch(/step 3 of 5/i)
    await user.click(screen.getByTestId("rate-RESPONSIVENESS-2"))
    await user.click(screen.getByTestId("review-next"))

    // overall: the home is worked out (5 and 3 → 4); the landlord gets their own value
    expect(screen.getByTestId("overall-home-4").getAttribute("aria-checked")).toBe("true")
    await user.click(screen.getByTestId("overall-landlord-1"))
    await user.click(screen.getByRole("button", { name: /use the worked-out rating \(2 stars\)/i }))
    await user.click(screen.getByTestId("recommend-home-yes"))
    await user.type(screen.getByLabelText(/^pros/i), "Steady water")
    await user.click(screen.getByTestId("review-next"))

    const home = screen.getByTestId("review-summary-home")
    expect(within(home).getByText("Noise").nextSibling?.textContent).toBe("Skipped")
    await user.click(screen.getByTestId("review-submit"))

    expect(await screen.findByTestId("review-submitted")).toBeTruthy()
    expect(screen.getByTestId("review-landlord-state").textContent).toMatch(/hidden until/i)
    expect(screen.getByTestId("invite-card").textContent).toMatch(/help the next tenant/i)
    expect(reviews.submitReviewForm).toHaveBeenCalledWith("t5", {
      home: {
        ratingOverall: 4,
        criteria: { WATER_SUPPLY: 5, PARKING: 3 },
        wouldRecommend: true,
        pros: "Steady water",
      },
      landlord: { ratingOverall: 2, criteria: { RESPONSIVENESS: 2 } },
    })
    expect(localStorage.getItem("rx-review-draft:t5")).toBeNull()
  })

  it("asks for an overall rating when nothing is rated", async () => {
    renderForm()
    const user = userEvent.setup({ delay: null })
    await user.click(await screen.findByTestId("review-next"))
    await user.click(screen.getByTestId("review-next"))
    await user.click(screen.getByTestId("review-next"))
    await user.click(screen.getByTestId("review-next"))
    const errors = screen.getAllByTestId("review-field-error").map((e) => e.textContent)
    expect(errors).toEqual([
      "Pick an overall rating for the home.",
      "Pick an overall rating for the landlord.",
    ])
    expect(screen.getByTestId("review-step").getAttribute("data-section")).toBe("overall")
  })

  it("opens the screen of a server field error and shows it under its field", async () => {
    vi.mocked(reviews.submitReviewForm).mockRejectedValue(
      new ApiError({
        status: 422,
        code: "validation_failed",
        detail: "The request has invalid fields.",
        errors: [
          { field: "landlord.body", code: "contains_phone", message: "Remove the phone number." },
        ],
      }),
    )
    renderForm()
    const user = userEvent.setup({ delay: null })
    await user.click(await screen.findByTestId("review-next"))
    await user.click(screen.getByTestId("rate-WATER_SUPPLY-4"))
    await user.click(screen.getByTestId("review-next"))
    await user.click(screen.getByTestId("rate-PRIVACY-4"))
    await user.type(screen.getByLabelText(/anything else about the landlord/i), "Call 98450 12345")
    await user.click(screen.getByTestId("review-next"))
    await user.click(screen.getByTestId("review-next"))
    await user.click(screen.getByTestId("review-submit"))

    expect(await screen.findByText("Remove the phone number.")).toBeTruthy()
    expect(screen.getByTestId("review-step").getAttribute("data-section")).toBe("landlord")
    expect(screen.queryByText(/invalid fields/i)).toBeNull()
  })

  it("shows the owner no tenant invite card after they review their tenant", async () => {
    vi.mocked(reviews.fetchReviewTasks).mockResolvedValueOnce([task("LANDLORD_TO_TENANT")])
    vi.mocked(reviews.submitReviewForm).mockResolvedValue({
      tenant: { id: "r3", status: "AWAITING_REVEAL", ratingOverall: 4 },
    })
    renderForm()
    const user = userEvent.setup({ delay: null })
    await user.click(await screen.findByTestId("review-next"))
    await user.click(screen.getByTestId("rate-RENT_ON_TIME-4"))
    await user.click(screen.getByTestId("review-next"))
    await user.click(screen.getByTestId("overall-tenant-4"))
    await user.click(screen.getByTestId("review-next"))
    await user.click(screen.getByTestId("review-submit"))

    expect(await screen.findByTestId("review-submitted")).toBeTruthy()
    expect(screen.getByTestId("review-tenant-state")).toBeTruthy()
    expect(screen.queryByTestId("invite-card")).toBeNull()
  })

  it("keeps the draft on this device and offers it again", async () => {
    renderForm()
    const user = userEvent.setup({ delay: null })
    await user.click(await screen.findByTestId("review-next"))
    await user.click(screen.getByTestId("rate-SECURITY-5"))
    await user.click(screen.getByTestId("review-save-draft"))
    expect(screen.getByTestId("review-draft-saved")).toBeTruthy()
    cleanup()

    renderForm()
    expect(await screen.findByTestId("review-draft-restored")).toBeTruthy()
    expect(screen.getByTestId("review-step").getAttribute("data-section")).toBe("home")
    expect(screen.getByTestId("rate-SECURITY-5").getAttribute("aria-checked")).toBe("true")
  })
})
