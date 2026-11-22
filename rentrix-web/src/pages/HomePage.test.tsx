// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, cleanup, render, waitFor } from "@testing-library/react"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { PropertySearch } from "@/api/properties"
import type { AuthState } from "@/features/auth/authContext"
import { fetchCities } from "@/api/cities"
import { LAUNCH_CITIES } from "@/test/cities"
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

function renderHome(initialEntries?: { pathname: string; state: unknown }[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
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
    expect(texts("h1")).toEqual(["Know the home, the landlord and the tenant before you sign."])
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
    expect(texts("h3", region("problem"))).toEqual([
      "Tenants get burned",
      "Landlords take a gamble",
      "No record anyone trusts",
    ])
    expect(texts("h3", region("audience"))).toEqual(["For tenants", "For landlords"])
    expect(region("how").querySelectorAll("ol > li")).toHaveLength(3)
    const trust = texts("h3", region("trust")).join(" | ")
    expect(trust).toMatch(/Phone OTP/)
    expect(trust).toMatch(/Green tick/)
    expect(trust).toMatch(/Aadhaar or PAN/)
    expect(trust).toMatch(/LinkedIn/)
    expect(texts("span", region("trust"))).toEqual(
      expect.arrayContaining(["Required", "Optional", "Verified by Doorway Living"]),
    )
    expect(texts("summary", region("faq"))).toHaveLength(5)
    expect(document.querySelector('[role="search"], form')).toBeNull()
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

  it("names the launch cities from GET /cities", async () => {
    h.state = { status: "anonymous", user: null } as AuthState
    renderHome()

    await waitFor(
      () =>
        expect(texts("section[aria-labelledby=hero-title] span")).toContain(
          "Starting in Bangalore and Pune, more cities coming",
        ),
      { timeout: 10_000 },
    )
  })

  it("offers sign-in as the call to action at the top and the end, plus Terms and Privacy", () => {
    h.state = { status: "anonymous", user: null } as AuthState
    renderHome()

    const ctas = ctaLinks()
    expect(ctas).toHaveLength(2)
    for (const cta of ctas) expect(cta.getAttribute("href")).toBe("/login")
    const footer = document.querySelector("footer")!
    expect(
      [...footer.querySelectorAll("a")].map((a) => [a.textContent, a.getAttribute("href")]),
    ).toEqual([
      ["Terms", "/terms"],
      ["Privacy", "/privacy"],
    ])
    // the only buttons scroll the preview row
    expect(
      [...document.querySelectorAll("button")].map((b) => b.getAttribute("aria-label")),
    ).toEqual(["Previous cards", "Next cards"])
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
    // the seed gives no review the green tick, so no example review carries one
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

    await waitFor(() => expect(vi.mocked(fetchCities)).toHaveBeenCalled())
    await act(() => new Promise((resolve) => setTimeout(resolve, 0)))
    expect(document.getElementById("coverage")).toBeNull()
    expect(texts("h2")).not.toContain("Where we are live.")
    expect(document.body.textContent).not.toMatch(/More cities are on the way/)
  })

  it("sends a signed-in user into the app", () => {
    h.state = { status: "signedIn", user: makeUser() } as AuthState
    const router = renderHome()

    expect(router.state.location.pathname).toBe("/search")
  })

  it("sends an admin to the admin console", () => {
    h.state = { status: "signedIn", user: makeUser({ roles: ["ADMIN"] }) } as AuthState
    const router = renderHome()

    expect(router.state.location.pathname).toBe("/admin")
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
