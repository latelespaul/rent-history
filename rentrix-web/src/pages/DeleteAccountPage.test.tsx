// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { ApiError } from "@/api/errors"
import type { CurrentUser } from "@/types/api"
import DeleteAccountPage from "./DeleteAccountPage"

const api = vi.hoisted(() => ({
  failure: null as Error | null,
  signOut: vi.fn(async () => {}),
}))

vi.mock("@/features/auth/authContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/auth/authContext")>()),
  useAuth: () => ({ state: { status: "signedIn", user: USER }, signOut: api.signOut }),
}))
vi.mock("@/api/auth", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/auth")>()),
  deleteAccount: vi.fn(async () => {
    if (api.failure) throw api.failure
  }),
}))

const { deleteAccount } = await import("@/api/auth")

const USER: CurrentUser = {
  id: "u-tenant",
  email: "new-user-abc@example.test",
  emailVerified: true,
  displayName: "Asha Rao",
  shortName: "Asha R.",
  roles: ["TENANT"],
  status: "ACTIVE",
  demo: false,
  needsOnboarding: false,
  verification: { TENANT: "DRAFT" },
  createdAt: "2026-11-21T00:00:00Z",
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      { path: "/settings/delete-account", element: <DeleteAccountPage /> },
      { path: "/", element: <p>Landing</p> },
    ],
    { initialEntries: ["/settings/delete-account"] },
  )
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

beforeEach(() => {
  configure({ asyncUtilTimeout: 3000 })
  api.failure = null
  api.signOut.mockClear()
  vi.mocked(deleteAccount).mockClear()
})
afterEach(cleanup)

// Typing with userEvent is slow on a busy CI runner.
describe("DeleteAccountPage", { timeout: 30_000 }, () => {
  it("says what is removed and kept, and deletes only once DELETE is typed", async () => {
    const router = renderPage()
    expect(screen.getByTestId("delete-removed").textContent).toContain("phone number")
    expect(screen.getByTestId("delete-kept").textContent).toContain("Deleted user")
    const submit = screen.getByTestId("delete-submit") as HTMLButtonElement
    expect(submit.disabled).toBe(true)
    await userEvent.type(screen.getByLabelText("Type DELETE to confirm"), "delete")
    expect(submit.disabled).toBe(true)
    await userEvent.clear(screen.getByLabelText("Type DELETE to confirm"))
    await userEvent.type(screen.getByLabelText("Type DELETE to confirm"), "DELETE")
    expect(submit.disabled).toBe(false)
    await userEvent.click(submit)
    expect(await screen.findByText("Landing")).toBeTruthy()
    expect(vi.mocked(deleteAccount)).toHaveBeenCalledWith("DELETE")
    expect(router.state.location.pathname).toBe("/")
    expect(router.state.location.state).toEqual({ signedOut: true, accountDeleted: true })
  })

  it("asks for a fresh sign-in when the API wants one", async () => {
    api.failure = new ApiError({
      status: 403,
      code: "reauth_required",
      detail: "For your safety, sign in again, then delete your account.",
    })
    renderPage()
    await userEvent.type(screen.getByLabelText("Type DELETE to confirm"), "DELETE")
    await userEvent.click(screen.getByTestId("delete-submit"))
    await userEvent.click(await screen.findByTestId("delete-reauth"))
    expect(api.signOut).toHaveBeenCalled()
  })
})
