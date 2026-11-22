// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen } from "@testing-library/react"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { ApiError } from "@/api/errors"
import type { Stats } from "@/types/api"
import { LAUNCH_CITIES, SEED_STATS } from "@/test/cities"
import HomePage from "./HomePage"

const api = vi.hoisted(() => ({ stats: null as Stats | null }))

vi.mock("@/api/cities", () => ({
  cityKeys: { all: ["cities"] },
  fetchCities: vi.fn(async () => LAUNCH_CITIES),
}))
vi.mock("@/api/stats", () => ({
  statsKeys: { all: ["stats"] },
  fetchStats: vi.fn(async () => {
    if (!api.stats) throw new ApiError({ status: 503, code: "unavailable" })
    return api.stats
  }),
}))

function renderHome() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: "/", element: <HomePage /> }])
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

afterEach(cleanup)
// Generous waits: the page resolves several queries before it settles.
configure({ asyncUtilTimeout: 10000 })

describe("HomePage", () => {
  it("shows the counts /stats returns, whatever they are", async () => {
    api.stats = {
      ...SEED_STATS,
      cities: [
        { city: "BLR", publishedProperties: 37, confirmedTenancies: 5, publishedReviews: 9 },
        { city: "PNQ", publishedProperties: 0, confirmedTenancies: 0, publishedReviews: 0 },
      ],
    }
    renderHome()

    expect(await screen.findByText("37 properties · 5 confirmed tenancies")).toBeTruthy()
    expect(screen.getByText("No properties yet")).toBeTruthy()
  })

  it("shows no number at all when /stats fails, and never a made-up one", async () => {
    api.stats = null
    renderHome()

    expect(await screen.findByText("Bangalore")).toBeTruthy()
    await new Promise((resolve) => setTimeout(resolve, 20))
    const text = screen.getByRole("main").textContent ?? ""
    // Only the numbered trust steps and the 14-day reveal rule carry digits.
    const digits = text
      .replace(/[123]\. /g, "")
      .replace("14 days", "")
      .match(/\d/g)
    expect(digits).toBeNull()
    expect(text).not.toMatch(/cities covered|now live in/i)
  })
})
