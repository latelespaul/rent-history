// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, render, screen } from "@testing-library/react"
import type { TrafficSummary } from "./api"
import { TrafficPage } from "./TrafficPage"

const summary: TrafficSummary = {
  today: { views: 12, visitors: 5 },
  last7Days: { views: 80, visitors: 30 },
  last30Days: { views: 1200, visitors: 400 },
  topPages: [{ name: "/p/{id}", views: 700 }],
  topSources: [{ name: "whatsapp", views: 300 }],
}

vi.mock("./api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api")>()),
  fetchTraffic: vi.fn(async () => summary),
}))

afterEach(cleanup)

describe("TrafficPage", () => {
  it("shows the windows, top pages and top sources", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <TrafficPage />
      </QueryClientProvider>,
    )
    expect(await screen.findByText("/p/{id}")).toBeTruthy()
    expect(screen.getByText("whatsapp")).toBeTruthy()
    expect(screen.getByText("1,200")).toBeTruthy()
    expect(screen.getByText("5 visitors")).toBeTruthy()
  })
})
