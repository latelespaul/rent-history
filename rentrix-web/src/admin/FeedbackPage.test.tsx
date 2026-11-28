// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, render, screen } from "@testing-library/react"
import type { FeedbackEntry } from "./api"
import { FeedbackPage } from "./FeedbackPage"

const items: FeedbackEntry[] = [
  {
    name: "Asha",
    email: "asha@example.test",
    type: "BUG",
    message: "Search is slow",
    createdAt: "2026-11-28T10:00:00Z",
  },
  {
    name: null,
    email: null,
    type: "IDEA",
    message: "Add Mumbai",
    createdAt: "2026-11-27T10:00:00Z",
  },
]

vi.mock("./api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api")>()),
  fetchFeedback: vi.fn(async () => ({
    items,
    page: { number: 0, size: 50, totalItems: 2, totalPages: 1 },
  })),
}))

afterEach(cleanup)

describe("FeedbackPage", () => {
  it("lists the messages with their type, sender and a reply link", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <FeedbackPage />
      </QueryClientProvider>,
    )
    expect(await screen.findByText("Search is slow")).toBeTruthy()
    expect(screen.getAllByTestId("feedback-row")).toHaveLength(2)
    expect(screen.getByText("Bug")).toBeTruthy()
    expect(screen.getByRole("link", { name: "asha@example.test" }).getAttribute("href")).toBe(
      "mailto:asha@example.test",
    )
    expect(screen.getByText(/No name/).textContent).toContain("no email")
  })
})
