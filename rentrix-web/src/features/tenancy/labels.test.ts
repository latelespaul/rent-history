import { describe, expect, it } from "vitest"
import { rentChangeMonths } from "./labels"

describe("rentChangeMonths", () => {
  it("opens an ended tenancy on its last month, inside the tenancy period", () => {
    expect(
      rentChangeMonths({ startDate: "2013-04-01", endDate: "2014-03-31" }, "2026-11-23"),
    ).toEqual({ min: "2013-04", max: "2014-03", initial: "2014-03" })
  })

  it("opens a running tenancy on this month", () => {
    expect(rentChangeMonths({ startDate: "2024-04-01", endDate: null }, "2026-11-23")).toEqual({
      min: "2024-04",
      max: "2026-11",
      initial: "2026-11",
    })
  })

  it("opens a tenancy that starts after this month on its start month", () => {
    expect(rentChangeMonths({ startDate: "2027-01-01", endDate: null }, "2026-11-23")).toEqual({
      min: "2027-01",
      max: "2027-01",
      initial: "2027-01",
    })
  })
})
