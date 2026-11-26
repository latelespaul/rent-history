// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, render as rtlRender, screen, within } from "@testing-library/react"
import type { ReactElement } from "react"
import userEvent from "@testing-library/user-event"
import { inviteLink, inviteMessage, shareInvite, whatsappUrl } from "./invite"
import { InviteCard } from "./InviteCard"

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
// `GET /me/invite`: the user's own code and friends-joined count (invite-friends §4).
vi.mock("@/api/invite", () => ({
  inviteKeys: { mine: ["me", "invite"] },
  fetchInvite: vi.fn(async () => ({ code: "K7QM2XWR4D", joined: 2 })),
}))

/** Renders with the user's code already loaded, unless `loaded` is false. */
function render(ui: ReactElement, { loaded = true } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  if (loaded) client.setQueryData(["me", "invite"], { code: "K7QM2XWR4D", joined: 2 })
  return rtlRender(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

const LINK = `${window.location.origin}/?ref=K7QM2XWR4D`

// The first render pays for loading the sheet modules; a cold, busy machine needs more than 5 s.
vi.setConfig({ testTimeout: 20_000 })
const { toast } = await import("sonner")
const { fetchInvite } = await import("@/api/invite")

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.clearAllMocks()
  Reflect.deleteProperty(navigator, "share")
  Reflect.deleteProperty(navigator, "canShare")
})

function stubShare(share: (data: ShareData) => Promise<void>) {
  const fn = vi.fn(share)
  Object.defineProperty(navigator, "share", { value: fn, configurable: true, writable: true })
  return fn
}

describe("invite text", () => {
  it("links the site root, never a page", () => {
    expect(inviteLink("https://doorwayliving.in")).toBe("https://doorwayliving.in/")
    expect(inviteLink("https://doorwayliving.in/")).toBe("https://doorwayliving.in/")
    expect(inviteLink("https://doorwayliving.in", "K7QM2XWR4D")).toBe(
      "https://doorwayliving.in/?ref=K7QM2XWR4D",
    )
  })

  it("says the honest claims and ends with the link", () => {
    const text = inviteMessage("https://doorwayliving.in/")
    expect(text).toBe(
      "Renting in Bangalore or Pune? Check the landlord on Doorway Living before you pay a deposit. " +
        "Tenants review anonymously. https://doorwayliving.in/",
    )
  })

  it("puts the whole message into the WhatsApp link", () => {
    const url = new URL(whatsappUrl("Hi & bye https://doorwayliving.in/"))
    expect(url.origin + url.pathname).toBe("https://wa.me/")
    expect(url.searchParams.get("text")).toBe("Hi & bye https://doorwayliving.in/")
  })
})

describe("shareInvite", () => {
  it("falls back when the browser has no share sheet", async () => {
    expect(await shareInvite("hi")).toBe("fallback")
  })

  it("opens the share sheet with the text", async () => {
    const share = stubShare(async () => {})
    expect(await shareInvite("hi")).toBe("shared")
    expect(share).toHaveBeenCalledWith({ text: "hi" })
  })

  it("treats closing the share sheet as a cancel, not a failure", async () => {
    stubShare(async () => {
      throw new DOMException("closed", "AbortError")
    })
    expect(await shareInvite("hi")).toBe("cancelled")
  })

  it("falls back when sharing fails", async () => {
    stubShare(async () => {
      throw new DOMException("no", "NotAllowedError")
    })
    expect(await shareInvite("hi")).toBe("fallback")
  })

  it("falls back when the browser cannot share text", async () => {
    const share = stubShare(async () => {})
    Object.defineProperty(navigator, "canShare", { value: () => false, configurable: true })
    expect(await shareInvite("hi")).toBe("fallback")
    expect(share).not.toHaveBeenCalled()
  })
})

describe("Help the next tenant card", () => {
  it("opens the sheet while the link loads, then fills it in", async () => {
    const share = stubShare(async () => {})
    let answer: (value: { code: string; joined: number }) => void = () => {}
    vi.mocked(fetchInvite).mockImplementationOnce(
      () => new Promise((resolve) => (answer = resolve)),
    )
    render(<InviteCard />, { loaded: false })
    await userEvent.setup().click(screen.getByTestId("invite-card"))
    const sheet = await screen.findByTestId("invite-sheet")
    expect(share).not.toHaveBeenCalled()
    expect(within(sheet).getByTestId("invite-message").textContent).toBe("Getting your link…")
    answer({ code: "K7QM2XWR4D", joined: 2 })
    expect(await within(sheet).findByText(inviteMessage(LINK))).toBeTruthy()
  })

  it("opens the phone's share sheet and no fallback", async () => {
    const share = stubShare(async () => {})
    render(<InviteCard />)
    await userEvent.setup().click(screen.getByTestId("invite-card"))
    expect(share).toHaveBeenCalledWith({
      text: inviteMessage(LINK),
    })
    expect(screen.queryByTestId("invite-sheet")).toBeNull()
  })

  it("shows WhatsApp and Copy link when there is no share sheet", async () => {
    // user-event puts its own clipboard on navigator, so spy on that one.
    const user = userEvent.setup()
    const writeText = vi.spyOn(navigator.clipboard, "writeText")
    render(<InviteCard />)
    await user.click(screen.getByTestId("invite-card"))

    const sheet = await screen.findByTestId("invite-sheet")
    const link = LINK
    expect(within(sheet).getByTestId("invite-joined").textContent).toBe("2 friends joined")
    expect(within(sheet).getByTestId("invite-message").textContent).toBe(inviteMessage(link))
    const whatsapp = within(sheet).getByTestId("invite-whatsapp")
    expect(whatsapp.getAttribute("href")).toBe(whatsappUrl(inviteMessage(link)))

    await user.click(within(sheet).getByTestId("invite-copy"))
    expect(writeText).toHaveBeenCalledWith(link)
    expect(toast.success).toHaveBeenCalledWith("Link copied")
  })

  it("says so when copying fails", async () => {
    const user = userEvent.setup()
    vi.spyOn(navigator.clipboard, "writeText").mockRejectedValue(new Error("denied"))
    render(<InviteCard />)
    await user.click(screen.getByTestId("invite-card"))
    await user.click(await screen.findByTestId("invite-copy"))
    expect(toast.error).toHaveBeenCalled()
  })
})
