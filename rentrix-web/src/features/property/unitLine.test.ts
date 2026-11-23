import { describe, expect, it } from "vitest"
import { locationPending, unitLine } from "./labels"

const level = (name: string, status = "ACTIVE") => ({ id: name, name, status })

describe("the home's place inside its society", () => {
  it("names a bare tower and wing, and keeps names that already say it", () => {
    expect(unitLine({ tower: level("4"), wing: level("B") }, "1204")).toBe(
      "Tower 4 · Wing B · Flat 1204",
    )
    expect(unitLine({ tower: level("Block C"), wing: level("Wing A") }, undefined)).toBe(
      "Block C · Wing A",
    )
    expect(unitLine(undefined, "Flat 3")).toBe("Flat 3")
    expect(unitLine({}, null)).toBe("")
  })

  it("is pending while any level is a new, unreviewed name", () => {
    expect(locationPending({ area: level("Gachibowli"), wing: level("B", "PENDING") })).toBe(true)
    expect(locationPending({ area: level("Gachibowli") })).toBe(false)
    expect(locationPending(undefined)).toBe(false)
  })
})
