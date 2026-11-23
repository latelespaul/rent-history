// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, configure, fireEvent, render, screen, within } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import type { QueueItem, QueueView } from "./api"
import { ago, reasonsFor } from "./queueText"
import { ApprovalsPage } from "./ApprovalsPage"

const HOME: QueueItem = {
  type: "PROPERTY",
  id: "01a20329-dcd1-7a5a-8ab0-1c35736d3aaa",
  title: "Prestige Lakeside, Koramangala",
  subtitle: "Bengaluru",
  details: ["2 BHK · Apartment · PIN 560034", "Added by tenant Demo T."],
  flags: [],
  city: "BLR",
  createdAt: "2026-11-23T08:00:00Z",
  verificationStatus: "UNVERIFIED",
  link: "/p/01a20329-dcd1-7a5a-8ab0-1c35736d3aaa",
  actions: ["APPROVE", "REJECT"],
  rejectCodes: ["CANNOT_CONFIRM", "DETAILS_WRONG", "DUPLICATE", "OTHER"],
}
const REVIEW: QueueItem = {
  ...HOME,
  type: "REVIEW",
  id: "01a20329-dcd1-7a5a-8ab0-1c35736d3bbb",
  title: "Review of the home Prestige Lakeside, Koramangala",
  details: ["4 ★ by Demo T.", "Quiet street."],
  actions: ["APPROVE", "REJECT", "HIDE"],
  rejectCodes: ["CANNOT_CONFIRM", "DETAILS_WRONG", "DUPLICATE", "ABUSE", "PERSONAL_DATA", "OTHER"],
}

const api = vi.hoisted(() => ({ view: { items: [], counts: {} } as QueueView }))

vi.mock("./api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api")>()),
  fetchQueue: vi.fn(async () => api.view),
  decideQueueItem: vi.fn(async (item: QueueItem) => ({
    type: item.type,
    id: item.id,
    outcome: "OK",
  })),
}))

const { decideQueueItem, fetchQueue } = await import("./api")

function renderPage(path = "/approvals") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <ApprovalsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  api.view = { items: [REVIEW, HOME], counts: { PROPERTY: 1, REVIEW: 1, CITY: 0 } }
})
afterEach(cleanup)
configure({ asyncUtilTimeout: 10000 })

describe("ApprovalsPage", () => {
  it("lists every waiting card with counts per type", async () => {
    renderPage()
    const cards = await screen.findAllByRole("listitem", { name: /Prestige Lakeside/ })
    expect(cards).toHaveLength(2)
    expect(screen.getByRole("button", { name: /^All\s*2$/ }).getAttribute("aria-pressed")).toBe(
      "true",
    )
    expect(screen.getByRole("button", { name: /^Homes\s*1$/ })).toBeTruthy()
    expect(within(cards[1]).getByText("2 BHK · Apartment · PIN 560034")).toBeTruthy()
    expect(within(cards[1]).queryByRole("button", { name: /^Hide/ })).toBeNull()
    expect(within(cards[0]).getByRole("button", { name: /^Hide/ })).toBeTruthy()
  })

  it("approves in one tap", async () => {
    renderPage()
    const home = await screen.findByRole("listitem", { name: HOME.title })
    fireEvent.click(within(home).getByRole("button", { name: `Approve: ${HOME.title}` }))
    await vi.waitFor(() => expect(decideQueueItem).toHaveBeenCalledTimes(1))
    expect(vi.mocked(decideQueueItem).mock.calls[0]).toEqual([HOME, "APPROVE", undefined])
  })

  it("rejects in two taps: Reject, then a reason, with the optional note", async () => {
    renderPage()
    const home = await screen.findByRole("listitem", { name: HOME.title })
    fireEvent.click(within(home).getByRole("button", { name: `Reject: ${HOME.title}` }))
    expect(decideQueueItem).not.toHaveBeenCalled()
    fireEvent.change(within(home).getByLabelText(/Note for the owner/), {
      target: { value: "  Rent looks off  " },
    })
    fireEvent.click(within(home).getByRole("button", { name: "Details look wrong" }))
    await vi.waitFor(() => expect(decideQueueItem).toHaveBeenCalledTimes(1))
    expect(vi.mocked(decideQueueItem).mock.calls[0]).toEqual([
      HOME,
      "REJECT",
      { code: "DETAILS_WRONG", note: "Rent looks off", noteFallback: "Details look wrong." },
    ])
  })

  it("filters by type through the tab", async () => {
    renderPage()
    fireEvent.click(await screen.findByRole("button", { name: /^Reviews\s*1$/ }))
    await vi.waitFor(() =>
      expect(vi.mocked(fetchQueue).mock.calls.some(([f]) => f.type === "REVIEW")).toBe(true),
    )
  })

  it("says so when nothing waits", async () => {
    api.view = { items: [], counts: {} }
    renderPage()
    expect(await screen.findByText("Nothing waiting")).toBeTruthy()
  })
})

describe("queueText", () => {
  it("offers abuse reasons only for a hide, and never for a reject", () => {
    expect(reasonsFor(REVIEW, "HIDE")).toEqual(["ABUSE", "PERSONAL_DATA", "OTHER"])
    expect(reasonsFor(REVIEW, "REJECT")).toEqual([
      "CANNOT_CONFIRM",
      "DETAILS_WRONG",
      "DUPLICATE",
      "OTHER",
    ])
  })

  it("says how long ago a card came in", () => {
    const now = Date.parse("2026-11-23T10:00:00Z")
    expect(ago("2026-11-23T09:59:50Z", now)).toBe("just now")
    expect(ago("2026-11-23T09:15:00Z", now)).toBe("45 min ago")
    expect(ago("2026-11-23T07:00:00Z", now)).toBe("3 h ago")
    expect(ago("2026-11-20T10:00:00Z", now)).toBe("3 d ago")
    expect(ago(null, now)).toBe("")
  })
})
