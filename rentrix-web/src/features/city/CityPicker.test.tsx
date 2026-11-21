// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { AppLayout } from "@/components/layout/AppLayout"
import HomePage from "@/pages/HomePage"
import SearchPage from "@/pages/SearchPage"
import { ApiError } from "@/api/errors"
import type { City, Stats } from "@/types/api"
import { LAUNCH_CITIES, MORE_CITIES, NINE_CITIES, SEED_STATS, byName } from "@/test/cities"

// Mocked `GET /cities` and `GET /stats`: each test sets what the API returns.
const api = vi.hoisted(() => ({
  cities: [] as unknown[],
  stats: null as unknown,
}))

vi.mock("@/api/cities", () => ({
  cityKeys: { all: ["cities"] },
  fetchCities: vi.fn(async () => api.cities),
}))

vi.mock("@/api/stats", () => ({
  statsKeys: { all: ["stats"] },
  fetchStats: vi.fn(async () => {
    if (api.stats) return api.stats
    throw new ApiError({ status: 404, code: "not_found" })
  }),
}))

function respond(cities: City[], stats: Stats | null = SEED_STATS) {
  api.cities = cities
  api.stats = stats
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      {
        element: <AppLayout />,
        children: [
          { path: "/", element: <HomePage /> },
          { path: "/search", element: <SearchPage /> },
        ],
      },
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

async function openPicker() {
  const user = userEvent.setup()
  const trigger = await screen.findByRole("button", { name: /city/i, expanded: false })
  await vi.waitFor(() => expect((trigger as HTMLButtonElement).disabled).toBe(false))
  await user.click(trigger)
  return { user, input: screen.getByRole("combobox", { name: "Search city" }) }
}

const optionNames = () =>
  within(screen.getByRole("listbox", { hidden: true }))
    .queryAllByRole("option")
    .map((o) => o.querySelector("span > span")?.textContent)

beforeEach(() => respond(byName(LAUNCH_CITIES)))
afterEach(cleanup)

describe("CityPicker", () => {
  it("lists the two launch cities from the API", async () => {
    renderAt("/")
    await openPicker()

    expect(optionNames()).toEqual(["Bangalore", "Pune"])
    expect(screen.getByText("2 active")).toBeTruthy()
    const listbox = screen.getByRole("listbox")
    expect(within(listbox).getAllByText("4 properties")).toHaveLength(2)
  })

  it("lists nine cities when the API returns nine, with no made-up counts", async () => {
    respond(NINE_CITIES)
    renderAt("/")
    await openPicker()

    expect(optionNames()).toEqual(NINE_CITIES.map((c) => c.name))
    expect(screen.getByText("9 active")).toBeTruthy()
    expect(within(screen.getByRole("listbox")).getAllByText("new")).toHaveLength(7)
  })

  it("finds Bangalore through its alias", async () => {
    respond(NINE_CITIES)
    renderAt("/")
    const { user, input } = await openPicker()

    await user.type(input, "beng")

    expect(optionNames()).toEqual(["Bangalore", "Kolkata"])
    expect(input.getAttribute("aria-activedescendant")).toMatch(/-opt-BLR$/)
  })

  it("says the city is not live yet when nothing matches", async () => {
    renderAt("/")
    const { user, input } = await openPicker()

    await user.type(input, "Goa")

    expect(screen.queryAllByRole("option")).toHaveLength(0)
    expect(screen.getByRole("status").textContent).toContain("Rentrix is not live in “Goa” yet.")
    expect(screen.getByRole("status").textContent).toContain(
      "Starting in Bangalore and Pune, more cities coming",
    )
  })

  it("writes the chosen code to ?city= on click", async () => {
    const router = renderAt("/")
    const { user } = await openPicker()

    await user.click(screen.getByRole("option", { name: /Pune/ }))

    expect(router.state.location.search).toBe("?city=PNQ")
    expect(screen.queryByRole("listbox")).toBeNull()
    expect(screen.getByRole("button", { name: /city/i }).textContent).toContain("Pune")
  })

  it("chooses with the keyboard on the search page", async () => {
    respond(NINE_CITIES)
    const router = renderAt("/search?city=PNQ")
    const { user, input } = await openPicker()

    expect(input.getAttribute("aria-activedescendant")).toMatch(/-opt-PNQ$/)
    await user.type(input, "cal")
    await user.keyboard("{Enter}")

    expect(router.state.location.search).toBe("?city=CCU")
  })

  it("closes on Escape without changing the city", async () => {
    const router = renderAt("/?city=BLR")
    const { user } = await openPicker()

    await user.keyboard("{ArrowDown}{Escape}")

    expect(screen.queryByRole("listbox")).toBeNull()
    expect(router.state.location.search).toBe("?city=BLR")
  })
})

describe("Home city cards and launch copy", () => {
  /** Under the search box and in the footer. */
  const expectLaunchCopy = (copy: string) => {
    expect(screen.getByText(copy)).toBeTruthy()
    expect(screen.getByRole("contentinfo").textContent).toBe(`Rentrix · ${copy}`)
  }

  const cardNames = () =>
    within(screen.getByRole("region", { name: "Pick a city" }))
      .getAllByRole("link")
      .map((a) => a.querySelector("span > span")?.textContent)

  it("renders one card per city with real counts", async () => {
    renderAt("/")

    await screen.findAllByText("4 properties · 6 confirmed tenancies")
    expect(cardNames()).toEqual(["Bangalore", "Pune"])
    expectLaunchCopy("Starting in Bangalore and Pune, more cities coming")
  })

  it("changes when a third city is added to the response", async () => {
    respond(byName([...LAUNCH_CITIES, MORE_CITIES[3]]))
    renderAt("/")

    await screen.findByText("No properties yet")
    expect(cardNames()).toEqual(["Bangalore", "Pune", "Hyderabad"])
    expectLaunchCopy("Starting in Bangalore, Hyderabad and Pune, more cities coming")
  })

  it("shows eight cards, then all nine after Show all", async () => {
    respond(NINE_CITIES)
    renderAt("/")
    const user = userEvent.setup()

    await screen.findAllByText("No properties yet")
    expect(cardNames()).toHaveLength(8)
    expectLaunchCopy("Live in 9 cities, more coming")

    await user.click(screen.getByRole("button", { name: "Show all 9 cities" }))
    expect(cardNames()).toHaveLength(9)
  })

  it("shows no counts at all while /stats is not served", async () => {
    respond(byName(LAUNCH_CITIES), null)
    renderAt("/")

    await vi.waitFor(() => expect(cardNames()).toEqual(["Bangalore", "Pune"]))
    expect(screen.queryByText(/propert/)).toBeNull()
  })
})
