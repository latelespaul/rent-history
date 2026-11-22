// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { AuthState } from "@/features/auth/authContext"
import { makeUser } from "@/test/users"
import HomePage from "./HomePage"

const h = vi.hoisted(() => ({ state: { status: "anonymous", user: null } as AuthState }))

vi.mock("@/features/auth/authContext", () => ({
  useAuth: () => ({ state: h.state }),
}))

function renderHome() {
  const router = createMemoryRouter([
    { path: "/", element: <HomePage /> },
    { path: "*", element: <main>App page</main> },
  ])
  render(<RouterProvider router={router} />)
  return router
}

afterEach(cleanup)

describe("HomePage", () => {
  it("shows an anonymous visitor only the brand, one line, one sign-in button, Terms and Privacy", () => {
    h.state = { status: "anonymous", user: null } as AuthState
    renderHome()

    expect(screen.getByRole("heading", { level: 1, name: "Doorway Living" })).toBeTruthy()
    expect(screen.queryAllByRole("button")).toHaveLength(0)
    expect(screen.getAllByRole("link").map((link) => link.textContent)).toEqual([
      "Sign in / Sign up",
      "Terms",
      "Privacy",
    ])
    expect(screen.getByRole("link", { name: "Sign in / Sign up" }).getAttribute("href")).toBe(
      "/login",
    )
    expect(screen.queryByRole("search")).toBeNull()
    expect(screen.queryByText(/pick a city/i)).toBeNull()
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
})
