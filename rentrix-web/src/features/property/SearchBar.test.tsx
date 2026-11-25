// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { SearchSuggestions } from "@/types/api"
import { SearchBar } from "./SearchBar"

const api = vi.hoisted(() => ({
  calls: [] as Array<{ city?: string; q: string; society?: string }>,
}))

const NONE: SearchSuggestions = { societies: [], areas: [], homes: [], towers: [] }
const LAKESIDE = { id: "11111111-1111-1111-1111-111111111111", name: "Prestige Lakeside" }
const CITY = { code: "BLR", name: "Bangalore" }

vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({ state: { status: "anonymous", user: null } }),
}))
vi.mock("@/features/property/useProperties", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/property/useProperties")>()),
  useLocalityHints: () => ({ data: [] }),
}))
vi.mock("@/features/city/CityPicker", () => ({
  CityPicker: ({ onChange }: { onChange: (code: string) => void }) => (
    <button type="button" onClick={() => onChange("PNQ")}>
      Pick Pune
    </button>
  ),
}))
vi.mock("@/api/search", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/search")>()),
  fetchSuggestions: vi.fn(async (query: { city?: string; q: string; society?: string }) => {
    api.calls.push(query)
    if (query.society === LAKESIDE.id) {
      return {
        ...NONE,
        towers: [{ id: "tb", name: "Tower B", publishedHomes: 2 }],
        homes: [
          {
            id: "h1204",
            society: LAKESIDE,
            tower: { id: "tb", name: "Tower B" },
            unitLabel: "1204",
            locality: "Whitefield",
            city: CITY,
          },
        ],
      }
    }
    if (/lak/i.test(query.q)) {
      return {
        ...NONE,
        societies: [
          {
            ...LAKESIDE,
            area: { id: "a", name: "Whitefield" },
            city: CITY,
            publishedHomes: 3,
            pending: false,
          },
        ],
      }
    }
    return NONE
  }),
}))

configure({ asyncUtilTimeout: 10000 })
afterEach(cleanup)
beforeEach(() => {
  api.calls = []
})

function renderBar(onSubmit = vi.fn(), onCityChange = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      {
        path: "/search",
        element: <SearchBar city="BLR" query="" onCityChange={onCityChange} onSubmit={onSubmit} />,
      },
      { path: "*", element: <p>elsewhere</p> },
    ],
    { initialEntries: ["/search"] },
  )
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return { router, onSubmit, onCityChange }
}

describe("the two-step search box (D-167)", () => {
  it("suggests a society while typing, then its flats and towers once picked", async () => {
    const user = userEvent.setup()
    const { router, onSubmit } = renderBar()
    const box = screen.getByRole("combobox", { name: /Locality, society, building or PIN/ })
    await user.type(box, "prestge lak")
    await user.click(await screen.findByRole("option", { name: /Prestige Lakeside/ }))

    expect(screen.getByTestId("society-chip").textContent).toContain("Prestige Lakeside")
    const flatBox = screen.getByRole("combobox", { name: "Flat number or tower" })
    expect(flatBox.getAttribute("placeholder")).toBe("Flat no…")
    await user.type(flatBox, "12")
    expect(await screen.findByRole("option", { name: /Tower B · Flat 1204/ })).toBeTruthy()
    const towers = screen.getAllByRole("option").filter((o) => o.dataset.group === "Towers")
    expect(towers.map((o) => o.textContent)).toEqual([expect.stringContaining("Tower B")])
    expect(api.calls.at(-1)).toMatchObject({ society: LAKESIDE.id })

    // Enter without picking a suggestion searches that society for the typed flat number
    await user.keyboard("{Escape}{Enter}")
    expect(onSubmit).toHaveBeenCalledWith({ city: "BLR", query: "12", society: LAKESIDE.id })
    expect(router.state.location.pathname).toBe("/search")
  })

  it("opens a picked flat's page and removes the society with its ×", async () => {
    const user = userEvent.setup()
    const { router } = renderBar()
    await user.type(screen.getByRole("combobox"), "lakeside")
    await user.click(await screen.findByRole("option", { name: /Prestige Lakeside/ }))
    await user.click(screen.getByRole("button", { name: "Remove Prestige Lakeside" }))
    expect(screen.queryByTestId("society-chip")).toBeNull()

    await user.type(screen.getByRole("combobox"), "lakeside")
    await user.click(await screen.findByRole("option", { name: /Prestige Lakeside/ }))
    await user.type(screen.getByRole("combobox"), "1")
    await user.click(await screen.findByRole("option", { name: /Flat 1204/ }))
    expect(router.state.location.pathname).toBe("/p/h1204")
  })

  it("drops a picked society when the city changes", async () => {
    const user = userEvent.setup()
    const onCityChange = vi.fn()
    renderBar(vi.fn(), onCityChange)
    await user.type(screen.getByRole("combobox"), "lakeside")
    await user.click(await screen.findByRole("option", { name: /Prestige Lakeside/ }))
    expect(screen.getByTestId("society-chip")).toBeTruthy()

    await user.click(screen.getByRole("button", { name: "Pick Pune" }))

    expect(onCityChange).toHaveBeenCalledWith("PNQ")
    expect(screen.queryByTestId("society-chip")).toBeNull()
    expect(screen.getByRole("combobox", { name: /Locality, society, building or PIN/ })).toBeTruthy()
  })

  it("opens the society page when Search is pressed with no flat number", async () => {
    const user = userEvent.setup()
    const { router } = renderBar()
    await user.type(screen.getByRole("combobox"), "lakeside")
    await user.click(await screen.findByRole("option", { name: /Prestige Lakeside/ }))
    await user.click(screen.getByRole("button", { name: "Search" }))
    expect(router.state.location.pathname).toBe(`/s/${LAKESIDE.id}`)
  })
})
