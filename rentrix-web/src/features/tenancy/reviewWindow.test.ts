import { describe, expect, it } from "vitest"
import { reviewWindow } from "./reviewWindow"

describe("review window (D-39)", () => {
  it("is open from moving in, current tenants too, and never closes", () => {
    expect(reviewWindow({ status: "ACTIVE" })).toEqual({ state: "open" })
    expect(reviewWindow({ status: "ENDED" })).toEqual({ state: "open" })
    expect(reviewWindow({ status: "PENDING_CONFIRMATION" })).toEqual({ state: "none" })
  })
})
