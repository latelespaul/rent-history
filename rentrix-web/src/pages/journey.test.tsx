// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { AddHomeResult, PropertyReview, Tenancy } from "@/types/api"
import { LAUNCH_CITIES } from "@/test/cities"
import { detail } from "@/test/properties"
import { makeUser } from "@/test/users"

// The new-user journey (captain 2026-11-22 21:38 / 22:22): add your home, then review it.
const TENANCY: Tenancy = {
  id: "t9",
  property: {
    id: "p9",
    buildingName: null as unknown as string,
    locality: "Gokulam",
    city: { code: "MYS", name: "Mysuru" },
    unitLabel: "12",
    propertyType: "INDEPENDENT_HOUSE",
  },
  myRole: "TENANT",
  landlord: { shortName: "Not on Doorway Living yet" },
  tenant: { id: "u1", shortName: "Asha R." },
  inviteEmail: null,
  startDate: "2026-03-01",
  endDate: null,
  initialRentInr: 18000,
  currentRentInr: 18000,
  depositInr: null,
  depositRefundedInr: null,
  status: "ACTIVE",
  initiatedBy: "TENANT",
  declineReason: null,
  evidenceLevel: null,
  createdAt: "2026-11-22T00:00:00Z",
  expiresAt: null,
  confirmedAt: null,
  endedAt: null,
  actions: { confirm: false, decline: false, cancel: false, end: true, addRentRecord: true },
} as Tenancy

const ADDED: AddHomeResult = {
  property: detail({
    id: "p9",
    locality: "Gokulam",
    buildingName: null,
    city: { code: "MYS", name: "Mysuru" },
    unitLabel: "12",
    addedByTenant: true,
    verification: "UNVERIFIED",
  }),
  tenancy: TENANCY,
}

const REVIEW: PropertyReview = {
  id: "r9",
  direction: "TENANT_TO_PROPERTY",
  status: "PUBLISHED",
  ratingOverall: 4,
  body: "Quiet lane, steady water supply and the owner fixed the geyser within a day.",
  verification: "UNVERIFIED",
}

vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({
    state: { status: "signedIn", user: makeUser() },
    acceptSession: vi.fn(),
  }),
}))
vi.mock("@/api/cities", () => ({
  cityKeys: { all: ["cities"] },
  fetchCities: vi.fn(async () => LAUNCH_CITIES),
}))
vi.mock("@/api/homes", () => ({
  fetchHomeMatches: vi.fn(async () => []),
  addHome: vi.fn(async () => ADDED),
  writeReview: vi.fn(async () => REVIEW),
}))
vi.mock("@/api/tenancies", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/tenancies")>()),
  fetchTenancy: vi.fn(async () => TENANCY),
}))

const homes = await import("@/api/homes")
const { default: AddHomePage } = await import("./AddHomePage")
const { default: WriteReviewPage } = await import("./WriteReviewPage")

configure({ asyncUtilTimeout: 10_000 })
vi.setConfig({ testTimeout: 240_000 })

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      { path: "/homes/new", element: <AddHomePage /> },
      { path: "/tenancies/:id/review", element: <WriteReviewPage /> },
      { path: "*", element: <p>elsewhere</p> },
    ],
    { initialEntries: [path] },
  )
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

afterEach(() => {
  cleanup()
  sessionStorage.clear()
  vi.clearAllMocks()
})

describe("new-user journey", () => {
  it("adds a rented home in a city not in the list, in four steps, then offers the review", async () => {
    renderAt("/homes/new")
    const user = userEvent.setup({ delay: null })

    expect(await screen.findByRole("heading", { name: /step 1 of 4 · where is it/i })).toBeTruthy()
    await screen.findByRole("option", { name: "Bangalore" })
    await user.selectOptions(screen.getByLabelText("City"), "OTHER")
    await user.type(screen.getAllByLabelText("City")[1], "Mysuru")
    await user.type(screen.getByLabelText("State"), "Karnataka")
    await user.type(screen.getByLabelText("Area or locality"), "Gokulam")
    await user.type(screen.getByLabelText("Building name"), "Lake View")
    await user.type(screen.getByLabelText("Flat / house no."), "12")
    await user.type(screen.getByLabelText("PIN code"), "570002")
    await user.click(screen.getByTestId("ah-next"))

    expect(
      await screen.findByRole("heading", { name: /step 2 of 4 · your role here/i }),
    ).toBeTruthy()
    await user.click(screen.getByTestId("ah-next"))
    expect(screen.getByText("Choose one.")).toBeTruthy()
    await user.click(screen.getByText("I rent it, or rented it"))
    await user.click(screen.getByTestId("ah-next"))

    expect(await screen.findByRole("heading", { name: /step 3 of 4 · your tenancy/i })).toBeTruthy()
    await user.type(screen.getByLabelText("Moved in"), "2026-03-01")
    await user.type(screen.getByLabelText(/monthly rent/i), "18000")
    await user.click(screen.getByTestId("ah-next"))

    expect(await screen.findByRole("heading", { name: /step 4 of 4 · the owner/i })).toBeTruthy()
    await user.type(screen.getByLabelText(/owner’s name/i), "Ravi")
    await user.click(screen.getByTestId("ah-save"))

    const done = await screen.findByTestId("add-home-done")
    expect(within(done).getByText("Added by a tenant · not verified yet")).toBeTruthy()
    expect(homes.addHome).toHaveBeenCalledWith({
      relation: "RENT",
      home: {
        city: "OTHER",
        pincode: "570002",
        locality: "Gokulam",
        buildingName: "Lake View",
        unitLabel: "12",
        propertyType: "APARTMENT",
        bedrooms: null,
      },
      newCity: { name: "Mysuru", state: "Karnataka" },
      startDate: "2026-03-01",
      monthlyRentInr: 18000,
      owner: { name: "Ravi", email: undefined, phone: undefined },
    })
    // The flow goes on (captain 2026-11-23 14:53): the review counts; the agreement card is a
    // placeholder until PR #62 and is not counted.
    expect(within(done).getByTestId("add-home-steps-left").textContent).toMatch(
      /home added\. 1 optional step left/i,
    )
    expect(
      within(done)
        .getByRole("link", { name: /write a review/i })
        .getAttribute("href"),
    ).toBe("/tenancies/t9/review")
    expect(within(done).getByTestId("next-step-agreement").textContent).toMatch(/coming soon/i)
    expect(within(done).getByTestId("add-home-skip").getAttribute("href")).toBe("/me/tenancies")
  })

  it("writes the review and shows it as submitted, not verified yet", async () => {
    renderAt("/tenancies/t9/review")
    const user = userEvent.setup({ delay: null })

    expect(await screen.findByRole("heading", { name: /gokulam/i })).toBeTruthy()
    await user.click(screen.getByTestId("review-submit"))
    expect(screen.getByTestId("review-missing").textContent).toMatch(/pick a rating/i)

    await user.click(screen.getByTestId("review-star-4"))
    await user.type(screen.getByLabelText("Your review"), REVIEW.body!)
    await user.click(screen.getByTestId("review-submit"))

    const submitted = await screen.findByTestId("review-submitted")
    expect(within(submitted).getByText("You submitted this review")).toBeTruthy()
    expect(within(submitted).getByTestId("tick-unverified").textContent).toBe("Not verified yet")
    expect(homes.writeReview).toHaveBeenCalledWith("t9", {
      direction: "TENANT_TO_PROPERTY",
      ratingOverall: 4,
      body: REVIEW.body,
    })
    expect(
      within(submitted)
        .getByRole("link", { name: /see it on the home/i })
        .getAttribute("href"),
    ).toBe("/p/p9#reviews")
  })
})
