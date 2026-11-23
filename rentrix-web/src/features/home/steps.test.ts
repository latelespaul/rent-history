import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Tenancy } from "@/types/api"
import { stepsLeft, stepsLeftLabel } from "./steps"

const tenancy = (over: Partial<Tenancy>): Tenancy =>
  ({
    myRole: "TENANT",
    status: "ACTIVE",
    startDate: "2026-01-01",
    endDate: null,
    evidenceLevel: null,
    reviewed: false,
    ...over,
  }) as Tenancy

describe("next steps after adding a home (captain 2026-11-23 14:53)", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date("2026-11-23T10:00:00Z"))
  })
  afterEach(() => vi.useRealTimers())

  it("counts the review until it is written, never the placeholder agreement step", () => {
    expect(stepsLeft(tenancy({}))).toBe(1)
    expect(stepsLeftLabel(1)).toBe("1 optional step left")
    expect(stepsLeftLabel(2)).toBe("2 optional steps left")
    expect(stepsLeft(tenancy({ reviewed: true }))).toBe(0)
    expect(stepsLeft(tenancy({ evidenceLevel: "MUTUAL", reviewed: true }))).toBe(0)
  })

  it("keeps a review that is not open yet, and one for a home left years ago", () => {
    expect(stepsLeft(tenancy({ startDate: "2026-11-01" }))).toBe(1)
    expect(
      stepsLeft(tenancy({ status: "ENDED", startDate: "2024-01-01", endDate: "2024-06-30" })),
    ).toBe(1)
  })

  it("is only for the tenant's own live or past records", () => {
    expect(stepsLeft(tenancy({ myRole: "LANDLORD" }))).toBe(0)
    expect(stepsLeft(tenancy({ status: "PENDING_CONFIRMATION" }))).toBe(0)
  })
})
