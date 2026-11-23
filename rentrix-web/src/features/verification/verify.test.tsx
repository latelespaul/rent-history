// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { ApiError } from "@/api/errors"
import type { CurrentUser, IdentityView, VerificationEntry } from "@/types/api"
import { LAUNCH_CITIES } from "@/test/cities"
import { makeUser } from "@/test/users"
import { entry, identity } from "@/test/verification"
import { AuthProvider } from "@/features/auth/AuthProvider"

// The API is mocked at the module edge, as in auth.test.tsx; the verification endpoints keep state.
const h = vi.hoisted(() => ({
  user: null as CurrentUser | null,
  verification: [] as VerificationEntry[],
  identity: null as IdentityView | null,
  android: false,
}))

vi.mock("@/features/verification/truecaller", () => ({
  canUseTruecaller: vi.fn(() => h.android),
  openDeepLink: vi.fn(),
}))

vi.mock("@/api/client", () => ({
  api: {
    restoreSession: vi.fn(async () => h.user !== null),
    setAccessToken: vi.fn(),
    clearSession: vi.fn(),
  },
  setSessionExpiredHandler: vi.fn(),
}))

vi.mock("@/api/auth", () => ({
  authKeys: { config: ["auth", "config"], devUsers: ["auth", "dev-users"] },
  fetchAuthConfig: vi.fn(async () => ({ googleClientId: null, devLogin: false })),
  fetchMe: vi.fn(async () => h.user),
  logout: vi.fn(async () => undefined),
  updateMe: vi.fn(),
}))

vi.mock("@/api/verification", () => ({
  verificationKeys: { all: ["me", "verification"], identity: ["me", "identity"] },
  fetchVerification: vi.fn(async () => h.verification),
  fetchIdentity: vi.fn(async () => h.identity),
  startPhoneCheck: vi.fn(async () => ({
    challengeId: "c1",
    expiresAt: "2026-11-22T10:10:00Z",
  })),
  confirmPhoneCheck: vi.fn(),
  startTruecaller: vi.fn(async () => ({
    requestId: "r1",
    deepLink: "truecallersdk://truesdk/web_verify?type=btmsheet&requestNonce=r1",
    expiresAt: "2026-11-22T10:10:00Z",
  })),
  fetchTruecallerRequest: vi.fn(),
  requestVerification: vi.fn(),
  saveLinkedin: vi.fn(),
  removeLinkedin: vi.fn(),
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

configure({ asyncUtilTimeout: 5000 })
vi.setConfig({ testTimeout: 30_000 })

const verification = await import("@/api/verification")
const truecaller = await import("@/features/verification/truecaller")
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

/** What the API answers once the code is confirmed. */
function phoneConfirmed() {
  h.verification = h.verification.map((e) => ({
    ...e,
    steps: { ...e.steps, phone: "DONE", method: "DONE" },
  }))
  h.identity = identity("VERIFIED")
  return h.identity
}

beforeEach(() => {
  h.user = makeUser({ verification: { TENANT: "DRAFT" } })
  h.verification = [entry()]
  h.identity = identity(null)
  h.android = false
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  // Only spies made with vi.spyOn (document.hasFocus); module mocks keep their implementations.
  vi.restoreAllMocks()
})

describe("verification stepper", () => {
  it("resumes at the first open step", async () => {
    const router = renderApp("/verify")

    expect(
      await screen.findByRole("heading", { name: /step 2 of 4 · verify \(any one\)/i }),
    ).toBeTruthy()
    expect(where(router)).toBe("/verify/phone")
  })

  it("verifies the phone: send code, a wrong code shows the tries left, the right one is done", async () => {
    vi.mocked(verification.confirmPhoneCheck)
      .mockRejectedValueOnce(
        new ApiError({ status: 422, code: "otp_invalid", detail: "Wrong code. 4 tries left." }),
      )
      .mockImplementationOnce(async () => phoneConfirmed())
    const router = renderApp("/verify/phone")
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(/your mobile number/i), "98765 43210")
    await user.click(screen.getByRole("button", { name: /send code/i }))
    expect(verification.startPhoneCheck).toHaveBeenCalledWith("+919876543210", "SMS")
    expect(await screen.findByText("Code sent")).toBeTruthy()
    expect(screen.getByText(/^resend code in (1:00|0:5\d)$/i)).toBeTruthy()
    expect(screen.getByText(/we sent it by sms/i)).toBeTruthy()

    await user.type(screen.getByLabelText(/enter the 6-digit code/i), "111111")
    await user.click(screen.getByRole("button", { name: /verify and continue/i }))
    expect(await screen.findByText("Wrong code. 4 tries left.")).toBeTruthy()

    const code = screen.getByLabelText(/enter the 6-digit code/i)
    await user.clear(code)
    await user.type(code, "482913")
    await user.click(screen.getByRole("button", { name: /verify and continue/i }))

    expect(verification.confirmPhoneCheck).toHaveBeenLastCalledWith("c1", "482913")
    expect(
      await screen.findByRole("heading", { name: /step 3 of 4 · aadhaar, linkedin, pan/i }),
    ).toBeTruthy()
    expect(where(router)).toBe("/verify/id")
    expect(screen.getByText(/phone verified · \+91 xxxxxx3210/i)).toBeTruthy()
    expect(screen.getAllByText("Coming soon")).toHaveLength(2)
  })

  it("offers any one method and counts a verified LinkedIn without a phone (D-144)", async () => {
    renderApp("/verify/phone")
    expect(await screen.findByText(/pick any one: phone, aadhaar or linkedin/i)).toBeTruthy()
    cleanup()

    h.identity = {
      ...identity(),
      linkedinVerified: true,
      methods: identity().methods.map((m) =>
        m.method === "LINKEDIN"
          ? { ...m, status: "VERIFIED", masked: "https://www.linkedin.com/in/test" }
          : m,
      ),
    }
    h.verification = h.verification.map((e) => ({
      ...e,
      steps: { ...e.steps, method: "DONE", linkedin: "DONE" },
    }))
    renderApp("/verify/phone")
    expect(await screen.findByText(/linkedin verified\./i)).toBeTruthy()
    expect(
      screen.getByText(/that is all you need to post\. a phone is an optional extra/i),
    ).toBeTruthy()
  })

  it("sends the code to Telegram by default when the API offers it, with SMS as the fallback (D-146)", async () => {
    h.identity = identity(null, true, false, true)
    vi.mocked(verification.startPhoneCheck).mockImplementationOnce(async () => ({
      challengeId: "c1",
      expiresAt: "2026-11-22T10:10:00Z",
      delivery: "TELEGRAM",
    }))
    renderApp("/verify/phone")
    const user = userEvent.setup()

    expect(await screen.findByText("We send the code to your Telegram.")).toBeTruthy()
    await user.type(screen.getByLabelText(/your mobile number/i), "98765 43210")
    await user.click(screen.getByRole("button", { name: "Send code to Telegram" }))

    expect(verification.startPhoneCheck).toHaveBeenCalledWith("+919876543210", "TELEGRAM")
    expect(await screen.findByText("Code sent")).toBeTruthy()
    expect(screen.getByText(/we sent it to your telegram: open telegram to see it/i)).toBeTruthy()
    cleanup()

    renderApp("/verify/phone")
    await user.click(
      await screen.findByRole("button", { name: /no telegram\? get the code by sms/i }),
    )
    await user.type(screen.getByLabelText(/your mobile number/i), "98765 43210")
    await user.click(screen.getByRole("button", { name: "Send code" }))
    expect(verification.startPhoneCheck).toHaveBeenLastCalledWith("+919876543210", "SMS")
  })

  it("shows the server's fallback text when Telegram cannot reach the number (D-146)", async () => {
    h.identity = identity(null, true, false, true)
    const detail =
      "We could not send the code to Telegram. Check that this number has Telegram, or get the code by SMS instead."
    vi.mocked(verification.startPhoneCheck).mockRejectedValueOnce(
      new ApiError({ status: 503, code: "telegram_send_failed", detail }),
    )
    renderApp("/verify/phone")
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(/your mobile number/i), "98765 43210")
    await user.click(screen.getByRole("button", { name: "Send code to Telegram" }))

    expect(await screen.findByText(detail)).toBeTruthy()
    expect(screen.queryByText(/the server had a problem/i)).toBeNull()
  })

  it("says the team will message the code when the API relays it by hand", async () => {
    vi.mocked(verification.startPhoneCheck).mockImplementationOnce(async () => ({
      challengeId: "c1",
      expiresAt: "2026-11-22T10:10:00Z",
      delivery: "TEAM",
    }))
    renderApp("/verify/phone")
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(/your mobile number/i), "98765 43210")
    await user.click(screen.getByRole("button", { name: /send code/i }))

    expect(await screen.findByText("Code requested")).toBeTruthy()
    expect(
      screen.getByText(
        /we'll message you the code shortly \(whatsapp\/sms from the doorway living team\)/i,
      ),
    ).toBeTruthy()
    expect(screen.queryByText(/we sent it by sms/i)).toBeNull()
  })

  it("returns to the page the user started from once the phone is verified", async () => {
    vi.mocked(verification.confirmPhoneCheck).mockImplementationOnce(async () => phoneConfirmed())
    const router = renderApp("/verify/phone?next=%2Fme%2Ftenancies%3Ftab%3Dpending")
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(/your mobile number/i), "+919876543210")
    await user.click(screen.getByRole("button", { name: /send code/i }))
    await user.type(await screen.findByLabelText(/enter the 6-digit code/i), "482913")
    await user.click(screen.getByRole("button", { name: /verify and continue/i }))

    await waitFor(() => expect(where(router)).toBe("/me/tenancies?tab=pending"))
  })

  it("offers Truecaller first on Android and continues once Truecaller's callback has landed", async () => {
    h.identity = identity(null, true, true)
    h.android = true
    vi.spyOn(document, "hasFocus").mockReturnValue(false)
    vi.mocked(verification.fetchTruecallerRequest).mockImplementation(async () => {
      phoneConfirmed()
      return { requestId: "r1", status: "VERIFIED", masked: "+91 XXXXXX3210" }
    })
    const router = renderApp("/verify/phone")
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: /verify with truecaller/i }))
    expect(truecaller.openDeepLink).toHaveBeenCalledWith(
      "truecallersdk://truesdk/web_verify?type=btmsheet&requestNonce=r1",
    )
    expect(
      await screen.findByRole("heading", { name: /step 3 of 4 · aadhaar, linkedin, pan/i }),
    ).toBeTruthy()
    expect(where(router)).toBe("/verify/id")
    expect(verification.fetchTruecallerRequest).toHaveBeenCalledWith("r1")
  })

  it("falls back to the SMS code when the user declines in Truecaller", async () => {
    h.identity = identity(null, true, true)
    h.android = true
    vi.spyOn(document, "hasFocus").mockReturnValue(false)
    vi.mocked(verification.fetchTruecallerRequest).mockResolvedValue({
      requestId: "r1",
      status: "REJECTED",
      reason: "user_rejected",
    })
    renderApp("/verify/phone")
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: /verify with truecaller/i }))
    expect(await screen.findByText(/truecaller verification was cancelled/i)).toBeTruthy()
    expect(screen.getByLabelText(/your mobile number/i)).toBeTruthy()
    expect(screen.getByRole("button", { name: /verify with truecaller/i })).toBeTruthy()
  })

  it("hides Truecaller off Android and when the API has no Truecaller key", async () => {
    h.identity = identity(null, true, true)
    renderApp("/verify/phone")
    expect(await screen.findByLabelText(/your mobile number/i)).toBeTruthy()
    expect(screen.queryByRole("button", { name: /verify with truecaller/i })).toBeNull()
    cleanup()

    h.android = true
    h.identity = identity(null, true, false)
    renderApp("/verify/phone")
    expect(await screen.findByLabelText(/your mobile number/i)).toBeTruthy()
    expect(screen.queryByRole("button", { name: /verify with truecaller/i })).toBeNull()
  })

  it("says phone verification is not available when the API has no SMS sender", async () => {
    h.identity = identity(null, false)
    renderApp("/verify/phone")

    expect(await screen.findByText(/phone verification is not available yet/i)).toBeTruthy()
    expect(screen.queryByRole("button", { name: /send code/i })).toBeNull()
  })

  it("lists the missing steps when Request verification is refused", async () => {
    h.verification = [entry({ steps: { phone: "DONE", method: "DONE" } })]
    h.identity = identity("VERIFIED")
    vi.mocked(verification.requestVerification).mockRejectedValueOnce(
      new ApiError({
        status: 422,
        code: "verification_incomplete",
        detail: "Finish the missing steps first.",
        missing: ["profile"],
      }),
    )
    renderApp("/verify/submit")
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Request verification" }))

    expect(await screen.findByText("Finish these first:")).toBeTruthy()
    expect(screen.getByRole("link", { name: /profile: your name/i }).getAttribute("href")).toBe(
      "/verify/profile",
    )
    expect(verification.requestVerification).toHaveBeenCalledWith("TENANT")
  })

  it("shows Waiting for review after the request, and that posting is already open", async () => {
    h.verification = [entry({ status: "PENDING", steps: { phone: "DONE", method: "DONE" } })]
    h.identity = identity("VERIFIED")
    renderApp("/verify")

    expect(
      await screen.findByRole("heading", { name: /thanks, your verification is requested/i }),
    ).toBeTruthy()
    expect(screen.getByText("Waiting for review")).toBeTruthy()
    expect(screen.getByText(/you can post now/i)).toBeTruthy()
  })
})

describe("phone banner and menu dot (D-121, D-132)", () => {
  it("shows the banner with a way back on pages, while the phone is not verified", async () => {
    renderApp("/settings")

    const banner = await screen.findByRole("region", { name: "Verification" })
    expect(banner.textContent).toContain("Verify to post: any one of phone, Aadhaar or LinkedIn.")
    expect(within(banner).getByRole("link", { name: "Verify now" }).getAttribute("href")).toBe(
      "/verify/phone?next=%2Fsettings",
    )
    expect(screen.getByTestId("progress-dot")).toBeTruthy()
  })

  it("lets a phone-verified DRAFT user reach a write page without the banner", async () => {
    h.verification = [entry({ steps: { phone: "DONE", method: "DONE" } })]
    h.identity = identity("VERIFIED")
    const router = renderApp("/tenancies/new?property=p1")

    await waitFor(() => expect(verification.fetchVerification).toHaveBeenCalled())
    await screen.findByTestId("progress-dot")
    expect(where(router)).toBe("/tenancies/new?property=p1")
    expect(screen.queryByRole("region", { name: "Verification" })).toBeNull()
  })
})
