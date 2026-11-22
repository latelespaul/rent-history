// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, cleanup, render, waitFor } from "@testing-library/react"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { PropertySearch } from "@/api/properties"
import type { AuthState } from "@/features/auth/authContext"
import { LAUNCH_CITIES } from "@/test/cities"
import { card, page } from "@/test/properties"
import { makeUser } from "@/test/users"
import HomePage from "./HomePage"

const h = vi.hoisted(() => ({
  state: { status: "anonymous", user: null } as AuthState,
  calls: [] as PropertySearch[],
  homes: (): Promise<unknown> => Promise.resolve({ items: [], page: {} }),
}))

vi.mock("@/features/auth/authContext", () => ({
  useAuth: () => ({ state: h.state }),
}))
vi.mock("@/api/cities", () => ({
  cityKeys: { all: ["cities"] },
  fetchCities: vi.fn(async () => LAUNCH_CITIES),
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
  it("explains the product to an anonymous visitor before sign-in", () => {
    h.state = { status: "anonymous", user: null } as AuthState
    renderHome()

    expect(texts("h1")).toEqual(["Doorway Living"])
    expect(texts("#hero-title ~ p")[0]).toBe(
      "Real rents and reviews from real tenants and owners. Green tick = verified.",
    )
    expect(texts("h2")).toEqual([
      "Ratings from both sides of the rental",
      "How it works",
      "Trust and verification",
      "A look inside",
      "Rent with your eyes open",
    ])
    expect(texts("h3", region("problem"))).toEqual([
      "Tenants rate homes",
      "Tenants rate landlords",
      "Landlords rate tenants",
    ])
    expect(region("how").querySelectorAll("ol > li")).toHaveLength(3)
    const trust = texts("h3", region("trust")).join(" | ")
    expect(trust).toMatch(/Phone OTP/)
    expect(trust).toMatch(/Verified by Doorway Living/)
    expect(trust).toMatch(/Aadhaar or PAN/)
    expect(trust).toMatch(/LinkedIn/)
    expect(document.querySelector('[role="search"], form')).toBeNull()
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
    expect(document.querySelectorAll("button")).toHaveLength(0)
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
    expect(h.calls).toEqual([{ sort: "top_rated", size: 3 }])
    expect(texts("article [data-variant=demo]", preview)).toEqual(["Demo data", "Demo data"])
    expect(texts("article h3", preview)).not.toContain("Sample apartment")
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
    // every example card says so: three sample homes and two sample reviews
    expect(texts("article [data-variant=warn]", preview)).toEqual(Array(5).fill("Sample"))
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
