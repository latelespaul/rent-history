// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import type { AuthConfig, CurrentUser } from "@/types/api"
import { LAUNCH_CITIES } from "@/test/cities"
import { DEV_USERS, NEW_USER, makeUser } from "@/test/users"
import { AuthProvider } from "./AuthProvider"

// The API is mocked at the module edge: `@/api/client` (token store) and `@/api/auth` (endpoints).
const h = vi.hoisted(() => ({
  config: { googleClientId: null, devLogin: false } as AuthConfig,
  restored: null as CurrentUser | null,
  gis: null as null | {
    initialize: ReturnType<typeof vi.fn>
    renderButton: ReturnType<typeof vi.fn>
    disableAutoSelect: ReturnType<typeof vi.fn>
  },
}))

vi.mock("@/api/client", () => ({
  api: {
    restoreSession: vi.fn(async () => h.restored !== null),
    setAccessToken: vi.fn(),
    clearSession: vi.fn(),
  },
  setSessionExpiredHandler: vi.fn(),
}))

vi.mock("@/api/auth", () => ({
  authKeys: { config: ["auth", "config"], devUsers: ["auth", "dev-users"] },
  fetchAuthConfig: vi.fn(async () => h.config),
  fetchDevUsers: vi.fn(async () => DEV_USERS),
  fetchMe: vi.fn(async () => h.restored),
  signInWithGoogle: vi.fn(),
  devLogin: vi.fn(),
  devLoginAsNewUser: vi.fn(),
  logout: vi.fn(async () => undefined),
  completeOnboarding: vi.fn(),
  updateMe: vi.fn(),
}))

vi.mock("@/api/verification", () => ({
  verificationKeys: { all: ["me", "verification"], identity: ["me", "identity"] },
  fetchVerification: vi.fn(async () => []),
  fetchIdentity: vi.fn(async () => ({ idVerified: false, linkedinVerified: false, methods: [] })),
}))

vi.mock("sonner", async (importOriginal) => ({
  ...(await importOriginal<typeof import("sonner")>()),
  toast: { success: vi.fn() },
}))

vi.mock("@/api/cities", () => ({
  cityKeys: { all: ["cities"] },
  fetchCities: vi.fn(async () => LAUNCH_CITIES),
}))

vi.mock("@/api/stats", () => ({
  statsKeys: { all: ["stats"] },
  fetchStats: vi.fn(async () => {
    throw new Error("no stats")
  }),
}))

vi.mock("./gis", () => ({
  loadGis: vi.fn(async () => h.gis),
}))

// Full-app renders on a busy CI runner: give async queries room before they count as failures.
configure({ asyncUtilTimeout: 5000 })
vi.setConfig({ testTimeout: 30_000 })

const { api } = await import("@/api/client")
const { toast } = await import("sonner")
const auth = await import("@/api/auth")
const { loadGis } = await import("./gis")
const { routes } = await import("@/routes/router")

function renderApp(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return router
}

const where = (router: ReturnType<typeof renderApp>) =>
  `${router.state.location.pathname}${router.state.location.search}`

beforeEach(() => {
  h.config = { googleClientId: null, devLogin: false }
  h.restored = null
  h.gis = {
    initialize: vi.fn(),
    renderButton: vi.fn(),
    disableAutoSelect: vi.fn(),
  }
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe("login page", () => {
  it("hides the Google button when the API has no client id", async () => {
    renderApp("/login")

    expect(await screen.findByText(/google sign-in is not set up/i)).toBeTruthy()
    expect(screen.queryByTestId("google-button")).toBeNull()
    expect(screen.queryByText(/dev login · seed users/i)).toBeNull()
    expect(loadGis).not.toHaveBeenCalled()
  })

  it("renders GIS with the client id from /auth/config and signs in with its ID token", async () => {
    h.config = { googleClientId: "test-client.apps.googleusercontent.com", devLogin: false }
    vi.mocked(auth.signInWithGoogle).mockResolvedValue({
      accessToken: "google-token",
      user: NEW_USER,
      needsOnboarding: true,
    })
    const router = renderApp("/login")

    await screen.findByTestId("google-button")
    await waitFor(() => expect(h.gis!.renderButton).toHaveBeenCalled())
    const [{ client_id, callback }] = h.gis!.initialize.mock.calls[0]
    expect(client_id).toBe("test-client.apps.googleusercontent.com")

    callback({ credential: "google-id-token" })

    expect(await screen.findByRole("heading", { name: /welcome to doorway living/i })).toBeTruthy()
    expect(auth.signInWithGoogle).toHaveBeenCalledWith("google-id-token")
    expect(api.setAccessToken).toHaveBeenCalledWith("google-token")
    expect(where(router)).toBe("/welcome")
  })

  it("lists seed users in the dev picker and signs in as the chosen one", async () => {
    h.config = { googleClientId: null, devLogin: true }
    const landlord = DEV_USERS[0]
    vi.mocked(auth.devLogin).mockResolvedValue({
      accessToken: "dev-token",
      user: landlord,
      needsOnboarding: false,
    })
    const router = renderApp("/login?next=%2Fsettings")
    const user = userEvent.setup()

    const panel = await screen.findByRole("region", { name: /dev login/i })
    await within(panel).findByText("Demo Landlord BLR-1")
    expect(within(panel).queryByRole("group", { name: /staff/i })).toBeNull()
    expect(within(panel).getByRole("group", { name: "Landlords (2)" })).toBeTruthy()
    expect(within(panel).getByText("Landlord pending")).toBeTruthy()
    expect(within(panel).getByText("Tenant rejected")).toBeTruthy()

    // City tabs come from the users' home cities, named by GET /cities.
    await user.click(within(panel).getByRole("button", { name: "Pune" }))
    expect(within(panel).queryByText("Demo Landlord BLR-1")).toBeNull()
    expect(within(panel).getByText("Demo Tenant PNQ-1")).toBeTruthy()
    await user.click(within(panel).getByRole("button", { name: "All" }))

    const submit = within(panel).getByRole("button", { name: /sign in as selected seed user/i })
    expect((submit as HTMLButtonElement).disabled).toBe(true)
    const radio = within(panel).getByRole("radio", { name: /demo landlord blr-1/i })
    await user.click(radio)
    expect((radio as HTMLInputElement).checked).toBe(true)
    await user.click(submit)

    expect(await screen.findByRole("heading", { name: "Settings" })).toBeTruthy()
    expect(auth.devLogin).toHaveBeenCalledWith("l1")
    expect(api.setAccessToken).toHaveBeenCalledWith("dev-token")
    expect(where(router)).toBe("/settings")
    // A returning sign-in says who is signed in (D-119).
    expect(toast.success).toHaveBeenCalledWith("Signed in as Demo Landlord BLR-1", {
      duration: 4000,
    })
  })

  it("lands a returning sign-in without next on the role home", async () => {
    h.config = { googleClientId: null, devLogin: true }
    vi.mocked(auth.devLogin).mockResolvedValue({
      accessToken: "dev-token",
      user: DEV_USERS[0],
      needsOnboarding: false,
    })
    const router = renderApp("/login")
    const user = userEvent.setup()

    const panel = await screen.findByRole("region", { name: /dev login/i })
    await user.click(await within(panel).findByRole("radio", { name: /demo landlord blr-1/i }))
    await user.click(within(panel).getByRole("button", { name: /sign in as selected seed user/i }))

    await waitFor(() => expect(where(router)).toBe("/me/dashboard"))
    expect(toast.success).toHaveBeenCalledTimes(1)
  })

  it("signs in as a brand-new dev user and goes to the role pick", async () => {
    h.config = { googleClientId: null, devLogin: true }
    vi.mocked(auth.devLoginAsNewUser).mockResolvedValue({
      accessToken: "new-token",
      user: NEW_USER,
      needsOnboarding: true,
    })
    const router = renderApp("/login")
    const user = userEvent.setup()

    const panel = await screen.findByRole("region", { name: /dev login/i })
    await within(panel).findByText("Demo Landlord BLR-1")
    await user.click(within(panel).getByRole("button", { name: /sign in as a new user/i }))

    expect(await screen.findByRole("heading", { name: /welcome to doorway living/i })).toBeTruthy()
    expect(where(router)).toBe("/welcome")
    expect(toast.success).not.toHaveBeenCalled()
  })

  it("sends a signed-in visitor on to next, with no toast for a restored session", async () => {
    h.restored = makeUser()
    const router = renderApp("/login?next=%2Fsettings")

    expect(await screen.findByRole("heading", { name: "Settings" })).toBeTruthy()
    expect(where(router)).toBe("/settings")
    expect(toast.success).not.toHaveBeenCalled()
  })

  it("gives every Sign in link the page it was clicked on as next", async () => {
    renderApp("/terms")

    const signIn = await screen.findAllByRole("link", { name: /^sign in$/i })
    expect(signIn.map((link) => link.getAttribute("href"))).toContain("/login?next=%2Fterms")
  })
})

describe("guarded routes", () => {
  it("sends anonymous visitors to /login with next", async () => {
    const router = renderApp("/settings")

    expect(await screen.findByRole("heading", { name: /sign in to doorway living/i })).toBeTruthy()
    expect(where(router)).toBe("/login?next=%2Fsettings")
  })

  it("sends users without a role to the role pick first", async () => {
    h.restored = NEW_USER
    const router = renderApp("/me/tenancies")

    expect(await screen.findByRole("heading", { name: /welcome to doorway living/i })).toBeTruthy()
    expect(where(router)).toBe("/welcome?next=%2Fme%2Ftenancies")
  })

  it("has no admin pages on the main site, not even for admins", async () => {
    h.restored = makeUser({ roles: ["ADMIN", "TENANT"] })
    renderApp("/admin")

    expect(await screen.findByText("Page not found")).toBeTruthy()
    expect(screen.queryByText("Admin console")).toBeNull()
  })
})

describe("onboarding", () => {
  it("posts the role pick, swaps in the fresh token and shows the account-created screen", async () => {
    h.restored = NEW_USER
    const onboarded = makeUser({
      ...NEW_USER,
      roles: ["LANDLORD"],
      needsOnboarding: false,
      verification: { LANDLORD: "DRAFT" },
    })
    vi.mocked(auth.completeOnboarding).mockResolvedValue({
      user: onboarded,
      accessToken: "fresh-token",
    })
    vi.mocked(auth.updateMe).mockResolvedValue({
      user: { ...onboarded, displayName: "Neha User" },
      accessToken: "fresher-token",
    })
    const router = renderApp("/welcome?next=%2Fsettings")
    const user = userEvent.setup()

    await screen.findByRole("heading", { name: /welcome to doorway living/i })
    await user.click(screen.getByRole("button", { name: "Continue" }))
    expect(screen.getByText("Pick tenant, landlord or both.")).toBeTruthy()
    expect(auth.completeOnboarding).not.toHaveBeenCalled()

    await user.click(screen.getByRole("checkbox", { name: /i rent out a property/i }))
    await user.click(screen.getByRole("checkbox", { name: /i accept the terms/i }))
    const name = screen.getByLabelText("Display name")
    await user.clear(name)
    await user.type(name, "Neha User")
    await user.click(screen.getByRole("button", { name: "Continue" }))

    // The account-created screen (D-118): name, email and role, then one next step.
    expect(await screen.findByText("Your account is created.")).toBeTruthy()
    expect(where(router)).toBe("/welcome/created")
    expect(screen.getByText("Neha User")).toBeTruthy()
    expect(screen.getByText("new.user@example.test")).toBeTruthy()
    expect(screen.getByText("Landlord")).toBeTruthy()
    expect(screen.getByRole("link", { name: /verify to post/i }).getAttribute("href")).toBe(
      "/verify/phone?next=%2Fhomes%2Fnew%3Fown%3D1",
    )
    await user.click(screen.getByRole("link", { name: /not now/i }))
    expect(await screen.findByRole("heading", { name: "Settings" })).toBeTruthy()
    expect(auth.completeOnboarding).toHaveBeenCalledWith({
      roles: ["LANDLORD"],
      homeCity: null,
      acceptTerms: true,
    })
    expect(auth.updateMe).toHaveBeenCalledWith({ displayName: "Neha User" })
    expect(vi.mocked(api.setAccessToken).mock.calls.map(([t]) => t)).toEqual([
      "fresh-token",
      "fresher-token",
    ])
    expect(where(router)).toBe("/settings")
  })

  it("rejects a display name shorter than 2 characters before submitting", async () => {
    h.restored = NEW_USER
    renderApp("/welcome")
    const user = userEvent.setup()

    await screen.findByRole("heading", { name: /welcome to doorway living/i })
    await user.click(screen.getByRole("checkbox", { name: /i rent a home/i }))
    await user.click(screen.getByRole("checkbox", { name: /i accept the terms/i }))
    const name = screen.getByLabelText("Display name")
    await user.clear(name)
    await user.type(name, " N ")
    await user.click(screen.getByRole("button", { name: "Continue" }))

    expect(screen.getByText("Use 2 to 60 characters for your display name.")).toBeTruthy()
    expect(auth.completeOnboarding).not.toHaveBeenCalled()
    expect(auth.updateMe).not.toHaveBeenCalled()
  })

  it("sends a new landlord who picks Not now to the dashboard without next", async () => {
    h.restored = NEW_USER
    vi.mocked(auth.completeOnboarding).mockResolvedValue({
      user: makeUser({ ...NEW_USER, roles: ["LANDLORD", "TENANT"], needsOnboarding: false }),
      accessToken: "fresh-token",
    })
    const router = renderApp("/welcome")
    const user = userEvent.setup()

    await screen.findByRole("heading", { name: /welcome to doorway living/i })
    await user.click(screen.getByRole("checkbox", { name: /i rent a home/i }))
    await user.click(screen.getByRole("checkbox", { name: /i rent out a property/i }))
    await user.click(screen.getByRole("checkbox", { name: /i accept the terms/i }))
    await user.click(screen.getByRole("button", { name: "Continue" }))

    await screen.findByText("Your account is created.")
    await user.click(screen.getByRole("link", { name: /not now/i }))
    await waitFor(() => expect(where(router)).toBe("/me/dashboard"))
    expect(auth.updateMe).not.toHaveBeenCalled()
  })
})

describe("settings", () => {
  it("adds the other role and stores the fresh token", async () => {
    h.restored = makeUser()
    vi.mocked(auth.updateMe).mockResolvedValue({
      user: makeUser({
        roles: ["LANDLORD", "TENANT"],
        verification: { TENANT: "APPROVED", LANDLORD: "DRAFT" },
      }),
      accessToken: "role-token",
    })
    renderApp("/settings")
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: /add landlord role/i }))

    await waitFor(() => expect(screen.getAllByText("You have this role")).toHaveLength(2))
    expect(auth.updateMe).toHaveBeenCalledWith({ addRoles: ["LANDLORD"] })
    expect(api.setAccessToken).toHaveBeenCalledWith("role-token")
    expect(screen.getByText("Not verified yet")).toBeTruthy()
  })

  it("signs out and returns home", async () => {
    h.restored = makeUser()
    window.sessionStorage.setItem(
      "draft:tenancy:p1",
      JSON.stringify({ email: "owner@example.com" }),
    )
    window.sessionStorage.setItem("other", "kept")
    const router = renderApp("/settings")
    const user = userEvent.setup()

    await screen.findByRole("heading", { name: "Settings" })
    await user.click(within(screen.getByRole("main")).getByRole("button", { name: /sign out/i }))

    await waitFor(() => expect(where(router)).toBe("/"))
    expect(auth.logout).toHaveBeenCalled()
    expect(api.clearSession).toHaveBeenCalled()
    expect(window.sessionStorage.getItem("draft:tenancy:p1")).toBeNull()
    expect(window.sessionStorage.getItem("other")).toBe("kept")
    window.sessionStorage.removeItem("other")
    expect(
      await screen.findAllByRole("link", { name: "Sign in / Sign up with Google" }),
    ).toHaveLength(2)
  })
})
