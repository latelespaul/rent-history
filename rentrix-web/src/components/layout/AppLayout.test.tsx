// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { ApiError } from "@/api/errors"
import NotFoundPage from "@/pages/NotFoundPage"
import { LAUNCH_CITIES, NINE_CITIES, SEED_STATS, byName } from "@/test/cities"
import { AppLayout } from "./AppLayout"
import { initials, navFor, type ShellViewer } from "./nav"

// Mocked `GET /cities` and `GET /stats`; each test sets what the API returns.
const api = vi.hoisted(() => ({ cities: [] as unknown, citiesFail: false }))

vi.mock("@/api/cities", () => ({
  cityKeys: { all: ["cities"] },
  fetchCities: vi.fn(async () => {
    if (api.citiesFail) throw new ApiError({ status: 500, code: "internal_error" })
    return api.cities
  }),
}))

vi.mock("@/api/stats", () => ({
  statsKeys: { all: ["stats"] },
  fetchStats: vi.fn(async () => SEED_STATS),
}))

function renderShell(path = "/", viewer: ShellViewer | null = null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      {
        element: <AppLayout viewer={viewer} />,
        children: [
          { path: "/", element: <main>Home</main> },
          { path: "/search", element: <main>Search page</main> },
          { path: "*", element: <NotFoundPage /> },
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

const banner = () => screen.getByRole("banner")

async function openShellPicker() {
  const user = userEvent.setup()
  const trigger = await within(banner()).findByRole("button", { name: /choose a city/i })
  await user.click(trigger)
  return user
}

const optionNames = () =>
  within(screen.getByRole("listbox"))
    .getAllByRole("option")
    .map((o) => o.querySelector("span > span")?.textContent)

// The first render pays for loading the shell's modules; a cold, busy machine needs more than 5 s.
vi.setConfig({ testTimeout: 20_000 })

beforeEach(() => {
  api.cities = byName(LAUNCH_CITIES)
  api.citiesFail = false
})
afterEach(cleanup)

describe("shell city picker", () => {
  it("lists only the cities the API returns", async () => {
    renderShell()
    await openShellPicker()
    expect(optionNames()).toEqual(["Bangalore", "Pune"])
  })

  it("lists nine when the API returns nine, and nothing else", async () => {
    api.cities = NINE_CITIES
    renderShell()
    await openShellPicker()
    expect(optionNames()).toEqual(NINE_CITIES.map((c) => c.name))
  })

  it("opens Search for the chosen city", async () => {
    const router = renderShell("/")
    const user = await openShellPicker()
    await user.click(screen.getByRole("option", { name: /Pune/ }))
    expect(router.state.location.pathname).toBe("/search")
    expect(router.state.location.search).toBe("?city=PNQ")
  })

  it("keeps Search's other params and shows the current city", async () => {
    const router = renderShell("/search?city=BLR&bedrooms=2")
    expect(await within(banner()).findByRole("button", { name: /Bangalore/ })).toBeTruthy()
    const user = userEvent.setup()
    await user.click(within(banner()).getByRole("button", { name: /Bangalore/ }))
    await user.click(screen.getByRole("option", { name: /Pune/ }))
    expect(router.state.location.search).toBe("?city=PNQ&bedrooms=2")
  })

  it("says so when the cities cannot be loaded", async () => {
    api.citiesFail = true
    renderShell()
    const trigger = await within(banner()).findByRole("button", {
      name: /cities could not be loaded/i,
    })
    expect((trigger as HTMLButtonElement).disabled).toBe(true)
  })
})

describe("top bar", () => {
  it("offers Search and Sign in to an anonymous visitor", async () => {
    renderShell()
    const nav = within(banner()).getByRole("navigation", { name: "Main" })
    expect(
      within(nav)
        .getAllByRole("link")
        .map((a) => a.textContent),
    ).toEqual(["Search"])
    expect(within(banner()).getByRole("link", { name: "Sign in" }).getAttribute("href")).toBe(
      "/login",
    )
    expect(within(banner()).queryByText("Demo data")).toBeNull()
  })

  it('shows a demo tenant\'s initials and the "Demo data" badge', () => {
    renderShell("/", { shortName: "Demo T.", roles: ["TENANT"], isDemo: true })
    expect(within(banner()).getByText("Demo data")).toBeTruthy()
    expect(within(banner()).getByText("DT")).toBeTruthy()
    expect(within(banner()).queryByRole("link", { name: "Sign in" })).toBeNull()
  })

  it("marks an admin and gives the admin nav", () => {
    renderShell("/", { shortName: "Demo A.", roles: ["ADMIN"], isDemo: false })
    expect(within(banner()).getByText("Admin")).toBeTruthy()
    expect(within(banner()).queryByText("Demo data")).toBeNull()
    const nav = within(banner()).getByRole("navigation", { name: "Main" })
    expect(
      within(nav)
        .getAllByRole("link")
        .map((a) => a.textContent),
    ).toEqual(["Dashboard", "Approvals", "Records", "Cities"])
  })

  it("folds nav, city picker and sign-in into the mobile menu", async () => {
    renderShell()
    const user = userEvent.setup()
    await user.click(within(banner()).getByRole("button", { name: "Menu" }))
    const dialog = await screen.findByRole("dialog")
    expect(within(dialog).getByRole("link", { name: "Search" })).toBeTruthy()
    expect(within(dialog).getByRole("link", { name: "Sign in" })).toBeTruthy()
    expect(await within(dialog).findByRole("button", { name: /choose a city/i })).toBeTruthy()
  })
})

describe("footer and empty state", () => {
  it("builds the launch line from the API list", async () => {
    renderShell()
    expect(
      await within(screen.getByRole("contentinfo")).findByText(
        "Rentrix · Starting in Bangalore and Pune, more cities coming",
      ),
    ).toBeTruthy()
  })

  it("shows the empty state for an unknown page", () => {
    renderShell("/nowhere")
    expect(screen.getByRole("status").textContent).toContain("Page not found")
    expect(screen.getByRole("link", { name: "Go home" }).getAttribute("href")).toBe("/")
  })
})

describe("nav helpers", () => {
  it("gives a landlord the dashboard and an anonymous visitor only Search", () => {
    expect(navFor(null).map((i) => i.label)).toEqual(["Search"])
    expect(
      navFor({ shortName: "Demo L.", roles: ["TENANT", "LANDLORD"], isDemo: true }).map(
        (i) => i.label,
      ),
    ).toEqual(["Search", "Dashboard", "Tenancies"])
  })

  it("makes initials from a short name", () => {
    expect(initials("Demo T.")).toBe("DT")
    expect(initials("asha")).toBe("A")
    expect(initials("")).toBe("?")
  })
})
