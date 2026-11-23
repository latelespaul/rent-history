import { describe, expect, it } from "vitest"
import { expiryLabel } from "./relayTime"

describe("expiryLabel", () => {
  const now = Date.parse("2026-11-23T10:00:00Z")

  it("rounds whole minutes up", () => {
    expect(expiryLabel("2026-11-23T10:06:30Z", now)).toBe("Expires in 7 min")
    expect(expiryLabel("2026-11-23T10:01:00Z", now)).toBe("Expires in 1 min")
  })

  it("counts seconds in the last minute", () => {
    expect(expiryLabel("2026-11-23T10:00:40Z", now)).toBe("Expires in 40 s")
  })

  it("says Expired at or after the expiry", () => {
    expect(expiryLabel("2026-11-23T10:00:00Z", now)).toBe("Expired")
    expect(expiryLabel("2026-11-23T09:59:00Z", now)).toBe("Expired")
  })
})
