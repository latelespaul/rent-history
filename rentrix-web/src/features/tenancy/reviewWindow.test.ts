import { describe, expect, it } from "vitest"
import { reviewWindow } from "./reviewWindow"

describe("review window (D-39)", () => {
  it("opens 60 days after moving in, or at moving out, and never closes", () => {
    expect(reviewWindow({ status: "ACTIVE", startDate: "2026-03-01" }, "2026-04-29")).toEqual({
      state: "not_yet",
      opens: "2026-04-30",
    })
    expect(reviewWindow({ status: "ACTIVE", startDate: "2026-03-01" }, "2026-04-30")).toEqual({
      state: "open",
    })
    const ended = { status: "ENDED" as const, startDate: "2026-03-01", endDate: "2026-03-20" }
    expect(reviewWindow(ended, "2026-03-20")).toEqual({ state: "open" })
    expect(reviewWindow(ended, "2028-09-17")).toEqual({ state: "open" })
    expect(
      reviewWindow({ status: "PENDING_CONFIRMATION", startDate: "2026-03-01" }, "2026-11-22"),
    ).toEqual({ state: "none" })
  })
})
