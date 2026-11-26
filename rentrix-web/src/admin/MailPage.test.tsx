// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import type { ReminderResult } from "./api"
import { MailPage } from "./MailPage"

const api = vi.hoisted(() => ({ mailEnabled: true, recipients: 3 }))

vi.mock("./api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api")>()),
  sendReviewReminder: vi.fn(async (body: { dryRun: boolean }): Promise<ReminderResult> => ({
    dryRun: body.dryRun,
    mailEnabled: api.mailEnabled,
    recipients: api.recipients,
    sent: body.dryRun ? 0 : api.recipients,
    failed: 0,
  })),
}))

const { sendReviewReminder } = await import("./api")

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MailPage />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  api.mailEnabled = true
  api.recipients = 3
})

afterEach(cleanup)

describe("MailPage", { timeout: 20_000 }, () => {
  it("counts first, then sends to the counted users", async () => {
    renderPage()
    fireEvent.click(screen.getByRole("button", { name: "Count recipients" }))
    expect(await screen.findByText("3 users")).toBeTruthy()
    expect(sendReviewReminder).toHaveBeenLastCalledWith({ onlyWithoutReview: true, dryRun: true })

    fireEvent.click(screen.getByRole("button", { name: "Send to 3 users" }))
    expect((await screen.findByTestId("mail-done")).textContent).toContain("Sent to 3 users")
    expect(sendReviewReminder).toHaveBeenLastCalledWith({ onlyWithoutReview: true, dryRun: false })
  })

  it("can include reviewers, and cannot send while mail is off", async () => {
    api.mailEnabled = false
    renderPage()
    fireEvent.click(screen.getByTestId("mail-only-without-review"))
    fireEvent.click(screen.getByRole("button", { name: "Count recipients" }))
    await screen.findByTestId("mail-count")
    expect(sendReviewReminder).toHaveBeenLastCalledWith({ onlyWithoutReview: false, dryRun: true })
    expect(screen.getByText(/Mail is off on this server/)).toBeTruthy()
    expect(
      (screen.getByRole("button", { name: "Send to 3 users" }) as HTMLButtonElement).disabled,
    ).toBe(true)
  })
})
