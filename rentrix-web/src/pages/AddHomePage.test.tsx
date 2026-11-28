// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { AreaCard } from "@/types/api"
import { LAUNCH_CITIES } from "@/test/cities"
import { makeUser } from "@/test/users"

// Area suggestions in "Add your home" (captain 2026-11-28): type an area or a PIN code, pick one,
// and the PIN code (and an empty city) fill in.
const BLR = { code: "BLR", name: "Bangalore" }
const PNQ = { code: "PNQ", name: "Pune" }
const area = (id: string, city: typeof BLR, name: string, pincodes: string[]): AreaCard => ({
  id,
  city,
  name,
  aliases: [],
  pincodes,
  status: "ACTIVE",
  societyCount: 0,
  publishedHomes: 0,
})
const AREAS = [
  area("a1", BLR, "Koramangala", ["560034", "560095"]),
  area("a2", BLR, "HSR Layout", ["560102"]),
  area("a3", PNQ, "Baner", ["411045"]),
  area("a4", PNQ, "Balewadi", ["411045"]),
]

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
  addHome: vi.fn(),
}))
vi.mock("@/api/locations", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/locations")>()),
  // as `GET /areas`: a word prefix of the name, or an exact PIN; no city is every live city
  searchAreas: vi.fn(async (city: string, text: string) =>
    AREAS.filter(
      (a) =>
        (!city || a.city.code === city) &&
        (!text ||
          (/^\d{6}$/.test(text)
            ? a.pincodes.includes(text)
            : a.name.toLowerCase().startsWith(text.toLowerCase()))),
    ),
  ),
  fetchSocieties: vi.fn(async () => []),
  fetchTowers: vi.fn(async () => []),
}))

const locations = await import("@/api/locations")
const { default: AddHomePage } = await import("./AddHomePage")

configure({ asyncUtilTimeout: 10_000 })
vi.setConfig({ testTimeout: 60_000 })

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: "/homes/new", element: <AddHomePage /> }], {
    initialEntries: ["/homes/new"],
  })
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

const areaBox = () => screen.getByRole("combobox", { name: "Area" })
const pinBox = () => screen.getByLabelText("PIN code") as HTMLInputElement
const citySelect = () => screen.getByLabelText("City") as HTMLSelectElement

afterEach(() => {
  cleanup()
  sessionStorage.clear()
  localStorage.clear()
  vi.clearAllMocks()
})

describe("area suggestions in Add your home", () => {
  it("suggests areas as you type, and picking one by keyboard fills its PIN code", async () => {
    renderPage()
    const user = userEvent.setup({ delay: null })
    await screen.findByRole("option", { name: "Bangalore" })
    await user.selectOptions(citySelect(), "BLR")

    await user.type(areaBox(), "kora")
    const list = await screen.findByRole("listbox", { name: "Areas" })
    // the city's list shows until the debounced search for "kora" answers
    await waitFor(() =>
      expect(
        within(list)
          .getAllByRole("option")
          .map((o) => o.getAttribute("aria-label")),
      ).toEqual(["Koramangala, PIN 560034, 560095", "Suggest new area “kora”"]),
    )
    expect(areaBox().getAttribute("aria-expanded")).toBe("true")
    // debounced: one search for the whole word, none per key
    expect(locations.searchAreas).not.toHaveBeenCalledWith("BLR", "k", expect.anything())
    expect(locations.searchAreas).toHaveBeenCalledWith("BLR", "kora", expect.anything())

    await user.keyboard("{ArrowDown}")
    const first = within(list).getAllByRole("option")[0]
    expect(first.getAttribute("aria-selected")).toBe("true")
    expect(areaBox().getAttribute("aria-activedescendant")).toBe(first.id)
    await user.keyboard("{Enter}")

    expect(await screen.findByRole("button", { name: "Change area" })).toBeTruthy()
    expect(screen.getByText("Koramangala")).toBeTruthy()
    expect(pinBox().value).toBe("560034")
    // the user can still change the PIN code
    await user.clear(pinBox())
    await user.type(pinBox(), "560095")
    expect(pinBox().value).toBe("560095")
  })

  it("Escape closes the list, and a click picks an area", async () => {
    renderPage()
    const user = userEvent.setup({ delay: null })
    await screen.findByRole("option", { name: "Bangalore" })
    await user.selectOptions(citySelect(), "BLR")
    await user.type(areaBox(), "hsr")
    await screen.findByRole("option", { name: /^HSR Layout/ })
    await user.keyboard("{Escape}")
    expect(screen.queryByRole("listbox", { name: "Areas" })).toBeNull()
    await user.keyboard("{ArrowDown}")
    await user.click(await screen.findByRole("option", { name: /^HSR Layout/ }))
    expect(pinBox().value).toBe("560102")
  })

  it("a PIN code typed with no city picked suggests that PIN's areas and fills the city", async () => {
    renderPage()
    const user = userEvent.setup({ delay: null })
    await screen.findByRole("option", { name: "Bangalore" })

    await user.type(areaBox(), "411045")
    const list = await screen.findByRole("listbox", { name: "Areas" })
    await within(list).findByRole("option", { name: "Baner, PIN 411045 · Pune" })
    await waitFor(() => expect(within(list).getAllByRole("option")).toHaveLength(2))
    expect(locations.searchAreas).toHaveBeenCalledWith("", "411045", expect.anything())

    await user.click(within(list).getByRole("option", { name: /^Baner/ }))
    expect(citySelect().value).toBe("PNQ")
    expect(pinBox().value).toBe("411045")
    expect(screen.getByText("Baner")).toBeTruthy()
  })

  it("a PIN code typed first lists its areas and is kept when one is picked", async () => {
    renderPage()
    const user = userEvent.setup({ delay: null })
    await screen.findByRole("option", { name: "Bangalore" })
    await user.selectOptions(citySelect(), "BLR")
    await user.type(pinBox(), "560095")

    const option = await screen.findByRole("option", { name: /^Koramangala/ })
    await waitFor(() => expect(screen.queryByRole("option", { name: /^HSR Layout/ })).toBeNull())
    await user.click(option)
    expect(pinBox().value).toBe("560095")
  })

  it("switching the city never offers the last city's areas", async () => {
    renderPage()
    const user = userEvent.setup({ delay: null })
    await screen.findByRole("option", { name: "Bangalore" })
    await user.selectOptions(citySelect(), "PNQ")
    await user.type(areaBox(), "ban")
    await screen.findByRole("option", { name: /^Baner/ })

    // hold Bangalore's answer, so the list shows what it holds while that search loads
    let release = () => {}
    const held = new Promise<void>((resolve) => (release = resolve))
    const search = vi.mocked(locations.searchAreas)
    const answer = search.getMockImplementation()!
    search.mockImplementation(async (city, text, signal) => {
      if (city === "BLR") await held
      return answer(city, text, signal)
    })

    await user.selectOptions(citySelect(), "BLR")
    await waitFor(() =>
      expect(search).toHaveBeenCalledWith("BLR", "ban", expect.anything()),
    )
    expect(screen.queryByRole("option", { name: /^Baner|^Balewadi/ })).toBeNull()
    release()
    expect(await screen.findByText("No area matches “ban”.")).toBeTruthy()
    expect(screen.queryByRole("option", { name: /^Baner|^Balewadi/ })).toBeNull()
    search.mockImplementation(answer)
  })

  it("calls only a whole 6-digit number a PIN when nothing matches", async () => {
    renderPage()
    const user = userEvent.setup({ delay: null })
    await screen.findByRole("option", { name: "Bangalore" })
    await user.selectOptions(citySelect(), "BLR")

    // part of a PIN, e.g. a pause while typing it
    await user.type(areaBox(), "56099")
    expect(await screen.findByText("No area matches “56099”.")).toBeTruthy()
    expect(screen.queryByText(/No area has PIN/)).toBeNull()

    await user.type(areaBox(), "9")
    expect(await screen.findByText("No area has PIN 560999 yet. Type the area name.")).toBeTruthy()
  })
})
