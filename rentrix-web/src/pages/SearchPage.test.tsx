// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { PropertySearch } from "@/api/properties"
import type { Page, PropertyCard } from "@/types/api"
import { LAUNCH_CITIES, SEED_STATS } from "@/test/cities"
import { card, page } from "@/test/properties"
import SearchPage from "./SearchPage"

const api = vi.hoisted(() => ({
  calls: [] as PropertySearch[],
  respond: (_: PropertySearch) => ({}) as unknown,
}))

// Search is open to everyone (D-162): these tests run signed out.
vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({ state: { status: "anonymous", user: null } }),
}))
// The box's suggestions (D-164): none here.
vi.mock("@/api/search", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/search")>()),
  fetchSuggestions: vi.fn(async () => ({ societies: [], areas: [], homes: [], towers: [] })),
}))
vi.mock("@/api/cities", () => ({
  cityKeys: { all: ["cities"] },
  fetchCities: vi.fn(async () => LAUNCH_CITIES),
}))
vi.mock("@/api/stats", () => ({
  statsKeys: { all: ["stats"] },
  fetchStats: vi.fn(async () => SEED_STATS),
}))
vi.mock("@/api/properties", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/properties")>()),
  searchProperties: vi.fn(async (search: PropertySearch) => {
    api.calls.push(search)
    return api.respond(search)
  }),
  // The results list reads the grouped search (D-166); here every row is a home.
  searchGrouped: vi.fn(async (search: PropertySearch) => {
    api.calls.push(search)
    const homes = api.respond(search) as Page<PropertyCard>
    return {
      items: homes.items.map((home) => ({ kind: "HOME", home })),
      page: homes.page,
      totalHomes: homes.page.totalItems,
    }
  }),
}))

const BLR_HOMES = [
  card({ id: "a", buildingName: "Demo Residency A", rating: { reviewCount: 4, average: 4.3 } }),
  card({ id: "b", buildingName: "Demo Heights B", rating: { reviewCount: 1, average: null } }),
]

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: "/search", element: <SearchPage /> }], {
    initialEntries: [path],
  })
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

/** The searches the results list made (the locality hints make their own `size: 50` call). */
const resultCalls = () => api.calls.filter((c) => c.size !== 50)

beforeEach(() => {
  api.calls = []
  api.respond = (search) => (search.size === 50 ? page(BLR_HOMES) : page(BLR_HOMES, 0, 12))
})
afterEach(cleanup)
// Generous waits: the page resolves several queries before it settles.
configure({ asyncUtilTimeout: 10000 })

describe("SearchPage", () => {
  it("shows the API's results for the city in the URL", async () => {
    renderAt("/search?city=BLR")

    expect(await screen.findByRole("heading", { name: "12 properties in Bangalore" })).toBeTruthy()
    expect(screen.getByRole("link", { name: "Demo Residency A" }).getAttribute("href")).toBe("/p/a")
    expect(screen.getByLabelText("Rated 4.3 out of 5 from 4 verified of 4 reviews")).toBeTruthy()
    // one verified review is no average yet (D-52): the card counts it
    expect(screen.getByText("1 review")).toBeTruthy()
    expect(resultCalls()[0]).toMatchObject({ city: "BLR", page: undefined, size: 10 })
  })

  it("writes each filter to the URL and sends it to the API", async () => {
    const user = userEvent.setup()
    const router = renderAt("/search?city=BLR")
    await screen.findByRole("heading", { name: /in Bangalore/ })

    const filters = screen.getByRole("region", { name: "Filters" })
    await user.click(within(filters).getByRole("button", { name: "Villa" }))
    await user.click(within(filters).getByRole("button", { name: "3 BHK" }))
    await user.selectOptions(within(filters).getByLabelText("Property rating"), "4")
    await user.selectOptions(screen.getByLabelText("Sort"), "newest")

    expect(router.state.location.search).toBe(
      "?city=BLR&type=VILLA&homeSize=BHK3&minRating=4&sort=newest",
    )
    await vi.waitFor(() =>
      expect(resultCalls().at(-1)).toMatchObject({
        city: "BLR",
        type: "VILLA",
        homeSize: "BHK3",
        minRating: 4,
        sort: "newest",
      }),
    )

    await user.click(within(filters).getByRole("button", { name: "Clear" }))
    expect(router.state.location.search).toBe("?city=BLR&sort=newest")
  })

  it("sends the search box as q, so a society name or a PIN code finds the home (D-162)", async () => {
    api.respond = (search) =>
      search.size === 50
        ? page(BLR_HOMES)
        : page([card({ id: "s", buildingName: "Sobha", verification: "UNVERIFIED" })])
    const user = userEvent.setup()
    const router = renderAt("/search?city=BLR&locality=HSR%20Layout")
    const box = await screen.findByRole("combobox", { name: /Locality, society, building or PIN/ })
    expect((box as HTMLInputElement).value).toBe("HSR Layout")

    await user.clear(box)
    await user.type(box, "560102")
    await user.click(screen.getByRole("button", { name: "Search" }))

    expect(router.state.location.search).toBe("?city=BLR&q=560102")
    await vi.waitFor(() =>
      expect(resultCalls().at(-1)).toMatchObject({ city: "BLR", q: "560102", locality: undefined }),
    )
    expect(await screen.findByTestId("card-not-verified")).toBeTruthy()
  })

  it("pages through results with a 1-based page in the URL", async () => {
    const user = userEvent.setup()
    const router = renderAt("/search?city=BLR")
    await screen.findByText("Page 1 of 2")

    await user.click(screen.getByRole("button", { name: /Next/ }))

    expect(router.state.location.search).toBe("?city=BLR&page=2")
    await vi.waitFor(() => expect(resultCalls().at(-1)).toMatchObject({ page: 1 }))
  })

  it("says so when nothing matches the filters", async () => {
    api.respond = () => page([])
    renderAt("/search?city=PNQ&type=VILLA")

    expect(await screen.findByText("No homes match")).toBeTruthy()
    expect(screen.getByText("Try fewer filters or another locality.")).toBeTruthy()
    expect(screen.getByRole("heading", { name: "0 properties in Pune" })).toBeTruthy()
  })
})
