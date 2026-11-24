// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { LAUNCH_CITIES, SEED_STATS } from "@/test/cities"
import { makeUser } from "@/test/users"
import { MODE_KEY_PREFIX, saveMode } from "@/features/mode/mode"
import { ShellRoot } from "./ShellRoot"

const BOTH = makeUser({ id: "u-shell-both", roles: ["TENANT", "LANDLORD"] })

vi.mock("./authContext", () => ({
  useAuth: () => ({ state: { status: "signedIn", user: BOTH }, signOut: vi.fn() }),
}))

vi.mock("@/features/verification/useVerification", () => ({
  useVerificationProgress: () => null,
}))

vi.mock("@/api/cities", () => ({
  cityKeys: { all: ["cities"] },
  fetchCities: vi.fn(async () => LAUNCH_CITIES),
}))

vi.mock("@/api/stats", () => ({
  statsKeys: { all: ["stats"] },
  fetchStats: vi.fn(async () => SEED_STATS),
}))

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      {
        element: <ShellRoot />,
        children: [{ path: "*", element: <main>Page</main> }],
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

const pressed = (name: string) =>
  within(screen.getByRole("banner"))
    .getByRole("button", { name })
    .getAttribute("aria-pressed")

beforeEach(() => {
  window.localStorage.clear()
  saveMode(BOTH.id, "TENANT")
})
afterEach(cleanup)

describe("ShellRoot mode", () => {
  it.each(["/me/dashboard", "/me/properties", "/me/properties/new", "/me/properties/p1/edit"])(
    "switches a Tenant-mode user to Owner on %s",
    async (path) => {
      renderAt(path)
      await waitFor(() => expect(pressed("Owner")).toBe("true"))
      expect(pressed("Tenant")).toBe("false")
      expect(window.localStorage.getItem(MODE_KEY_PREFIX + BOTH.id)).toBe("OWNER")
    },
  )

  it("lets an Owner-mode user switch to Tenant from the dashboard", async () => {
    saveMode(BOTH.id, "OWNER")
    const router = renderAt("/me/dashboard")
    await screen.findByText("Page")
    await userEvent
      .setup()
      .click(within(screen.getByRole("banner")).getByRole("button", { name: "Tenant" }))

    await waitFor(() => expect(router.state.location.pathname).toBe("/me/tenancies"))
    expect(pressed("Tenant")).toBe("true")
    expect(window.localStorage.getItem(MODE_KEY_PREFIX + BOTH.id)).toBe("TENANT")
  })

  it("keeps the saved mode on a page both modes share", async () => {
    renderAt("/me/tenancies")
    await screen.findByText("Page")
    expect(pressed("Tenant")).toBe("true")
    expect(window.localStorage.getItem(MODE_KEY_PREFIX + BOTH.id)).toBe("TENANT")
  })
})
