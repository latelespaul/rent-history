// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { ReactNode } from "react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { sendFeedback } from "@/api/feedback"
import { ApiError } from "@/api/errors"
import { AuthContext, type AuthContextValue } from "@/features/auth/authContext"
import type { CurrentUser } from "@/types/api"
import { FeedbackForm } from "./FeedbackForm"

vi.mock("@/api/feedback", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/feedback")>()),
  sendFeedback: vi.fn(async () => undefined),
}))

const send = vi.mocked(sendFeedback)

beforeEach(() => send.mockReset().mockResolvedValue(undefined))
afterEach(cleanup)

function renderForm(wrap?: (children: ReactNode) => ReactNode) {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  const form = <FeedbackForm />
  render(<QueryClientProvider client={client}>{wrap ? wrap(form) : form}</QueryClientProvider>)
}

const asha = {
  id: "u1",
  displayName: "Asha Rao",
  shortName: "Asha R.",
  email: "asha@example.test",
  emailVerified: true,
  roles: ["TENANT"],
  status: "ACTIVE",
} as CurrentUser

const field = (name: RegExp) => screen.getByLabelText(name) as HTMLInputElement

describe("FeedbackForm", () => {
  it("needs a message, then sends the picked type and shows the thank-you state", async () => {
    renderForm()
    const submit = screen.getByTestId("feedback-send") as HTMLButtonElement
    expect(submit.disabled).toBe(true)
    fireEvent.click(screen.getByRole("button", { name: "Bug" }))
    expect(screen.getByRole("button", { name: "Bug" }).getAttribute("aria-pressed")).toBe("true")
    fireEvent.change(field(/^Message/), { target: { value: "  Search is slow  " } })
    fireEvent.change(field(/^Email/), { target: { value: "asha@example.test" } })
    fireEvent.click(submit)
    expect(await screen.findByTestId("feedback-thanks")).toBeTruthy()
    expect(send).toHaveBeenCalledWith({
      name: undefined,
      email: "asha@example.test",
      type: "BUG",
      message: "Search is slow",
      website: undefined,
    })
    expect(screen.getByText("We reply by email.")).toBeTruthy()
  })

  it("passes a filled honeypot through, so the API can drop it", async () => {
    renderForm()
    fireEvent.change(document.querySelector('input[name="website"]')!, {
      target: { value: "http://spam.example" },
    })
    fireEvent.change(field(/^Message/), { target: { value: "hi" } })
    fireEvent.click(screen.getByTestId("feedback-send"))
    await waitFor(() => expect(send).toHaveBeenCalled())
    expect(send.mock.calls[0][0].website).toBe("http://spam.example")
  })

  it("shows the API's error and keeps the message", async () => {
    send.mockRejectedValueOnce(
      new ApiError({ status: 429, code: "rate_limited", title: "Too many", detail: "Slow down." }),
    )
    renderForm()
    fireEvent.change(field(/^Message/), { target: { value: "hello" } })
    fireEvent.click(screen.getByTestId("feedback-send"))
    expect((await screen.findByRole("alert")).textContent).toContain("Slow down.")
    expect(field(/^Message/).value).toBe("hello")
  })

  it("prefills the name and email of a signed-in user", () => {
    const auth = { state: { status: "signedIn", user: asha } } as AuthContextValue
    renderForm((children) => <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>)
    expect(field(/^Name/).value).toBe("Asha Rao")
    expect(field(/^Email/).value).toBe("asha@example.test")
  })

  it("prefills once the session resolves after loading, keeping what was typed", () => {
    const client = new QueryClient()
    const tree = (state: AuthContextValue["state"]) => (
      <QueryClientProvider client={client}>
        <AuthContext.Provider value={{ state } as AuthContextValue}>
          <FeedbackForm />
        </AuthContext.Provider>
      </QueryClientProvider>
    )
    const { rerender } = render(tree({ status: "loading", user: null } as AuthContextValue["state"]))
    expect(field(/^Name/).value).toBe("")
    fireEvent.change(field(/^Name/), { target: { value: "A. Rao" } })
    rerender(tree({ status: "signedIn", user: asha } as AuthContextValue["state"]))
    expect(field(/^Name/).value).toBe("A. Rao")
    expect(field(/^Email/).value).toBe("asha@example.test")
  })
})
