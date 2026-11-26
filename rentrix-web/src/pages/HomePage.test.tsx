// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, cleanup, fireEvent, render, waitFor } from "@testing-library/react"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { PropertySearch } from "@/api/properties"
import type { AuthState } from "@/features/auth/authContext"
import { fetchCities } from "@/api/cities"
import { LAUNCH_CITIES, NINE_CITIES } from "@/test/cities"
import { card, page } from "@/test/properties"
import { makeUser } from "@/test/users"
import HomePage from "./HomePage"

const h = vi.hoisted(() => ({
  state: { status: "anonymous", user: null } as AuthState,
  calls: [] as PropertySearch[],
  homes: (): Promise<unknown> => Promise.resolve({ items: [], page: {} }),
  areaCalls: [] as string[],
}))

vi.mock("@/features/auth/authContext", () => ({
  useAuth: () => ({ state: h.state }),
}))
vi.mock("@/api/cities", () => ({
  cityKeys: { all: ["cities"] },
  fetchCities: vi.fn(async () => LAUNCH_CITIES),
}))
vi.mock("@/api/locations", () => ({
  searchAreas: vi.fn(async (city: string) => {
    h.areaCalls.push(city)
    const names = city === "BLR" ? ["Koramangala", "HSR Layout"] : ["Baner"]
    return names.map((name) => ({ id: `${city}-${name}`, name }))
  }),
}))
vi.mock("@/api/properties", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/properties")>()),
  searchProperties: vi.fn((search: PropertySearch) => {
    h.calls.push(search)
    return h.homes()
  }),
}))

const CTA = "Sign in / Sign up with Google"

let client: QueryClient

function renderHome(initialEntries?: { pathname: string; state: unknown }[]) {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      { path: "/", element: <HomePage /> },
      { path: "*", element: <main>App page</main> },
    ],
    { initialEntries },
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
  h.calls = []
  h.areaCalls = []
  h.homes = () => Promise.resolve(page([]))
})

/** Text of every element matching `selector`; DOM queries keep this big page fast to test. */
function texts(selector: string, root: ParentNode = document): string[] {
  return [...root.querySelectorAll(selector)].map((el) => el.textContent?.trim() ?? "")
}

function region(id: string): HTMLElement {
  const el = document.querySelector<HTMLElement>(`section[aria-labelledby="${id}-title"]`)
  if (!el) throw new Error(`no section ${id}`)
  return el
}

function ctaLinks(): HTMLAnchorElement[] {
  return [...document.querySelectorAll("a")].filter((a) => a.textContent?.trim() === CTA)
}

// The landing is a long page; give each test room on a busy CI runner.
describe("HomePage", { timeout: 20_000 }, () => {
  it("explains the product to an anonymous visitor before sign-in", async () => {
    h.state = { status: "anonymous", user: null } as AuthState
    renderHome()

    await waitFor(() => expect(texts("h2")).toContain("Where we are live."), { timeout: 10_000 })
    expect(texts("h1")).toEqual(["Check the home, the landlord and the tenant."])
    expect(texts("h2")).toEqual([
      "Renting runs on trust nobody can check.",
      "Three steps, a few minutes.",
      "One place, two points of view.",
      "Open to everyone. Verified where it counts.",
      "Cards straight from the app.",
      "Where we are live.",
      "Questions, answered.",
      "Rent with your eyes open.",
    ])
    // the problem is three icon tiles of a few words each and one banner, with no paragraph
    const problem = region("problem")
    expect(texts("h3", problem)).toEqual([
      "Deposits kept, repairs ignored",
      "Late rent, damaged homes",
      "No record follows anyone",
    ])
    expect(problem.querySelectorAll("li svg[aria-hidden=true]")).toHaveLength(3)
    expect(texts("p", problem)).toEqual(["Doorway Living keeps the record."])
    expect(texts("h3", region("audience"))).toEqual(["For tenants", "For landlords"])
    expect(region("how").querySelectorAll("ol > li")).toHaveLength(3)
    const trust = texts("h3", region("trust")).join(" | ")
    expect(trust).toMatch(/Phone OTP/)
    expect(trust).toMatch(/Green tick/)
    expect(trust).toMatch(/Aadhaar/)
    expect(trust).toMatch(/LinkedIn/)
    expect(texts("span", region("trust"))).toEqual(
      expect.arrayContaining(["Any one", "By our team", "Verified by Doorway Living"]),
    )
    expect(texts("summary", region("faq"))).toHaveLength(5)
  })

  it("shows how it works as three steps of an icon and a short label, no body text", () => {
    h.state = { status: "anonymous", user: null } as AuthState
    renderHome()

    const how = region("how")
    expect(texts("ol > li h3", how)).toEqual([
      "Sign in with Google",
      "Verify once",
      "Rate your rental",
    ])
    // one short line per step (captain 2026-11-26): 3-5 words, no chips, no body text
    for (const title of texts("ol > li h3", how))
      expect(title.split(" ").length).toBeLessThanOrEqual(5)
    expect(how.querySelectorAll("ol p, ol ul")).toHaveLength(0)
    for (const svg of how.querySelectorAll("ol svg"))
      expect(svg.closest("[aria-hidden=true]")).not.toBeNull()
    // searching needs no account (captain 2026-11-25): one line under the steps says so
    expect(texts("p", how)).toEqual(["Search and read reviews with no account."])
  })

  it("shows each side as three icon rows, one side at a time on the I rent / I own toggle", () => {
    h.state = { status: "anonymous", user: null } as AuthState
    renderHome()

    const audience = region("audience")
    const tiles = (id: string) => texts(`#${id} li`, audience)
    expect(tiles("for-tenants")).toEqual(["Check the landlord", "Know the home", "See real rents"])
    expect(tiles("for-landlords")).toEqual([
      "Check a tenant",
      "List your homes",
      "Earn the green tick",
    ])
    expect(audience.querySelectorAll("li svg[aria-hidden=true]")).toHaveLength(6)
    expect(audience.querySelectorAll("p")).toHaveLength(0)
    expect(
      [...audience.querySelectorAll("article a")].map((a) => [
        a.textContent,
        a.getAttribute("href"),
      ]),
    ).toEqual([
      ["Search homes", "/search"],
      ["List your home", "/login"],
    ])

    const list = audience.querySelector('[role="tablist"]')!
    expect(list.getAttribute("aria-label")).toBe("Choose your side")
    const [rent, own] = [...list.querySelectorAll<HTMLButtonElement>('[role="tab"]')]
    expect([rent?.textContent, own?.textContent]).toEqual(["I rent", "I own"])
    // both cards stay in the page (from 1024 px both show); the toggle marks one active
    const panel = (id: string) => audience.querySelector(`#${id}`)!.closest('[role="tabpanel"]')!
    expect(panel("for-tenants").getAttribute("data-state")).toBe("active")
    expect(panel("for-landlords").getAttribute("data-state")).toBe("inactive")
    fireEvent.mouseDown(own!, { button: 0 })
    expect(own!.getAttribute("aria-selected")).toBe("true")
    expect(panel("for-tenants").getAttribute("data-state")).toBe("inactive")
    expect(panel("for-landlords").getAttribute("data-state")).toBe("active")
  })

  it("puts the search box in the hero, open to anyone, and sends a search to /search", async () => {
    h.state = { status: "anonymous", user: null } as AuthState
    const router = renderHome()

    const hero = document.querySelector('section[aria-labelledby="hero-title"]')!
    const form = hero.querySelector('form[role="search"]')!
    expect(form).not.toBeNull()
    const input = form.querySelector<HTMLInputElement>('input[role="combobox"]')!
    const button = [...form.querySelectorAll("button")].find((b) => b.textContent === "Search")!
    expect(button.getAttribute("type")).toBe("submit")

    fireEvent.change(input, { target: { value: "HSR Layout" } })
    fireEvent.click(button)

    await waitFor(() => expect(router.state.location.pathname).toBe("/search"))
    expect(router.state.location.search).toBe("?q=HSR+Layout")
  })

  it("leads the hero with three icon badges and one privacy line instead of a paragraph", () => {
    h.state = { status: "anonymous", user: null } as AuthState
    renderHome()

    const hero = document.querySelector('section[aria-labelledby="hero-title"]')!
    const badges = [...hero.querySelectorAll("ul:not([aria-label]) > li")]
    expect(badges.map((li) => li.textContent)).toEqual([
      "Two-sided reviews",
      "Real rents",
      "Team-verified",
    ])
    for (const li of badges)
      expect(li.querySelector("svg")?.getAttribute("aria-hidden")).toBe("true")
    expect(texts("p", hero.querySelector("div")!)).toContain("Phone, Aadhaar, PAN stay private")
    // the search box comes before the badges and the call to action
    const order = [hero.querySelector("h1"), hero.querySelector("form"), badges[0], ctaLinks()[0]]
    for (let i = 1; i < order.length; i++)
      expect(
        order[i - 1]!.compareDocumentPosition(order[i]!) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy()
  })

  it("links the hero and the header to the sections they name", () => {
    h.state = { status: "anonymous", user: null } as AuthState
    renderHome()

    const how = [...document.querySelectorAll("a")].find(
      (a) => a.textContent?.trim() === "See how it works",
    )
    expect(how?.getAttribute("href")).toBe("#how")
    const nav = document.querySelector('nav[aria-label="Sections"]')!
    for (const a of nav.querySelectorAll("a")) {
      expect(document.getElementById(a.getAttribute("href")!.slice(1))).not.toBeNull()
    }
    expect(
      [...document.querySelectorAll("header a")].find((a) => a.textContent === "Sign in"),
    ).toHaveProperty("pathname", "/login")
  })

  it("shows the live cities from GET /cities as a short Live in line in the hero", async () => {
    h.state = { status: "anonymous", user: null } as AuthState
    renderHome()

    await waitFor(
      () =>
        expect(
          texts('section[aria-labelledby=hero-title] ul[aria-label="Live cities"] li'),
        ).toEqual(["Bangalore", "Pune", "+ More soon"]),
      { timeout: 10_000 },
    )
  })

  it("names only two cities in the hero and counts the rest (captain 2026-11-26)", async () => {
    h.state = { status: "anonymous", user: null } as AuthState
    vi.mocked(fetchCities).mockResolvedValueOnce(NINE_CITIES)
    renderHome()

    await waitFor(
      () =>
        expect(
          texts('section[aria-labelledby=hero-title] ul[aria-label="Live cities"] li'),
        ).toEqual(["Ahmedabad", "Bangalore", "+7 more"]),
      { timeout: 10_000 },
    )
  })

  it("shows no city chips when GET /cities fails", async () => {
    h.state = { status: "anonymous", user: null } as AuthState
    vi.mocked(fetchCities).mockRejectedValueOnce(new Error("offline"))
    renderHome()

    await waitFor(() => expect(client.getQueryState(["cities"])?.status).toBe("error"))
    expect(document.querySelector('ul[aria-label="Live cities"]')).toBeNull()
  })

  it("offers sign-in as the call to action at the top and the end, plus Search, Terms and Privacy", () => {
    h.state = { status: "anonymous", user: null } as AuthState
    renderHome()

    const ctas = ctaLinks()
    expect(ctas).toHaveLength(2)
    for (const cta of ctas) expect(cta.getAttribute("href")).toBe("/login")
    const footer = document.querySelector("footer")!
    expect(
      [...footer.querySelectorAll("a")].map((a) => [a.textContent, a.getAttribute("href")]),
    ).toEqual([
      ["Search", "/search"],
      ["Terms", "/terms"],
      ["Privacy", "/privacy"],
    ])
    // besides the hero search (city picker, Search), the only buttons are the side toggle and
    // the preview row's scroll buttons
    expect(
      [...document.querySelectorAll("main button:not(form[role=search] button)")].map(
        (b) => b.getAttribute("aria-label") ?? b.textContent,
      ),
    ).toEqual(["I rent", "I own", "Previous cards", "Next cards"])
  })

  it("previews the top-rated published homes, linked to their pages", async () => {
    h.state = { status: "anonymous", user: null } as AuthState
    h.homes = () =>
      Promise.resolve(
        page([
          card({
            id: "a",
            buildingName: "Demo Residency A",
            rating: { reviewCount: 4, average: 4.3 },
          }),
          card({ id: "b", buildingName: "Demo Heights B" }),
        ]),
      )
    renderHome()

    const preview = region("preview")
    await waitFor(() => expect(texts("article h3", preview)).toContain("Demo Residency A"), {
      timeout: 10_000,
    })
    expect(texts("article h3 a", preview)).toEqual(["Demo Residency A", "Demo Heights B"])
    expect(preview.querySelector("article h3 a")!.getAttribute("href")).toBe("/p/a")
    expect(h.calls).toEqual([{ sort: "top_rated", size: 6 }])
    // two homes from the seed and an example review after each, every one marked "Demo data"
    expect(texts("article [data-variant=demo]", preview)).toEqual(Array(4).fill("Demo data"))
    expect(texts("article blockquote", preview)).toHaveLength(2)
    // the seed gives no review the green tick, so every example review is "Not verified yet"
    expect(texts("article [data-variant=neutral]", preview)).toEqual(
      Array(2).fill("Not verified yet"),
    )
    expect(texts("article [data-variant=success]", preview)).toEqual([])
    expect(texts("article h3", preview)).not.toContain("Sample apartment")
    // rent shows only where both parties share it; a card never carries one
    expect(preview.textContent).not.toMatch(/₹/)
  })

  it("shows sample cards marked Sample when no home can be read", async () => {
    h.state = { status: "anonymous", user: null } as AuthState
    h.homes = () => Promise.reject(new Error("offline"))
    renderHome()

    const preview = region("preview")
    await waitFor(
      () =>
        expect(texts("article h3", preview).filter((t) => t === "Sample apartment")).toHaveLength(
          3,
        ),
      { timeout: 10_000 },
    )
    // every example card says so: three sample homes, and three example reviews from the seed
    expect(texts("article [data-variant=warn]", preview)).toEqual(Array(3).fill("Sample"))
    expect(texts("article [data-variant=demo]", preview)).toEqual(Array(3).fill("Demo data"))
    expect(texts("article [data-variant=neutral]", preview)).toEqual(
      Array(3).fill("Not verified yet"),
    )
    expect(texts("article [data-variant=success]", preview)).toEqual([])
  })

  it("shows each live city with its first localities from GET /areas", async () => {
    h.state = { status: "anonymous", user: null } as AuthState
    renderHome()

    await waitFor(() => expect(texts("li a", region("coverage"))).toContain("Baner"), {
      timeout: 10_000,
    })
    const coverage = region("coverage")
    expect(texts("h3", coverage)).toEqual(["Bangalore", "Pune"])
    expect(h.areaCalls.sort()).toEqual(["BLR", "PNQ"])
    const chip = [...coverage.querySelectorAll("li a")].find((a) => a.textContent === "HSR Layout")
    expect(chip?.getAttribute("href")).toBe("/search?city=BLR&locality=HSR%20Layout")
  })

  it("hides the whole coverage section when GET /cities fails", async () => {
    h.state = { status: "anonymous", user: null } as AuthState
    vi.mocked(fetchCities).mockRejectedValueOnce(new Error("offline"))
    renderHome()

    await waitFor(() => expect(client.getQueryState(["cities"])?.status).toBe("error"))
    expect(document.getElementById("coverage")).toBeNull()
    expect(texts("h2")).not.toContain("Where we are live.")
    expect(document.body.textContent).not.toMatch(/More cities are on the way/)
  })

  it("sends a signed-in user into the app", () => {
    h.state = { status: "signedIn", user: makeUser() } as AuthState
    const router = renderHome()

    expect(router.state.location.pathname).toBe("/search")
  })

  it("never sends anyone to an admin console on the main site", () => {
    h.state = { status: "signedIn", user: makeUser({ roles: ["ADMIN"] }) } as AuthState
    const router = renderHome()

    expect(router.state.location.pathname).toBe("/search")
  })

  it("drops the sign-out flag once signed out, so Back after a new sign-in goes into the app", async () => {
    h.state = { status: "anonymous", user: null } as AuthState
    const router = renderHome([{ pathname: "/", state: { signedOut: true } }])
    await waitFor(() => expect(router.state.location.state).toBeNull())

    await act(() => router.navigate("/login"))
    h.state = { status: "signedIn", user: makeUser() } as AuthState
    await act(() => router.navigate(-1))

    await waitFor(() => expect(router.state.location.pathname).toBe("/search"))
  })

  it("keeps the landing for a user still signing out", () => {
    h.state = { status: "signedIn", user: makeUser() } as AuthState
    const router = renderHome([{ pathname: "/", state: { signedOut: true } }])

    expect(router.state.location.pathname).toBe("/")
    expect(ctaLinks()).toHaveLength(2)
  })
})
