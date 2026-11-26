// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { clearRef, keepRefFromUrl, normaliseRef, readRef } from "./ref"

afterEach(() => {
  clearRef()
  window.history.replaceState(null, "", "/")
  vi.restoreAllMocks()
})

describe("invite code from the link", () => {
  it("keeps a well-formed code, upper-cased, and removes it from the address", () => {
    window.history.replaceState(null, "", "/search?city=BLR&ref=k7qm2xwr4d#top")
    keepRefFromUrl()
    expect(readRef()).toBe("K7QM2XWR4D")
    expect(window.location.pathname + window.location.search + window.location.hash).toBe(
      "/search?city=BLR#top",
    )
  })

  it("drops a malformed code from the address and keeps nothing", () => {
    window.history.replaceState(null, "", "/?ref=<script>")
    keepRefFromUrl()
    expect(readRef()).toBeNull()
    expect(window.location.search).toBe("")
  })

  it("lets a newer link replace an older code", () => {
    window.history.replaceState(null, "", "/?ref=AAAAAAAAAA")
    keepRefFromUrl()
    window.history.replaceState(null, "", "/?ref=BBBBBBBBBB")
    keepRefFromUrl()
    expect(readRef()).toBe("BBBBBBBBBB")
  })

  it("forgets a code after 30 days", () => {
    window.history.replaceState(null, "", "/?ref=K7QM2XWR4D")
    keepRefFromUrl()
    expect(readRef(Date.now() + 31 * 24 * 60 * 60 * 1000)).toBeNull()
  })

  it("keeps the code in memory when storage is blocked", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked")
    })
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked")
    })
    window.history.replaceState(null, "", "/?ref=K7QM2XWR4D")
    keepRefFromUrl()
    expect(readRef()).toBe("K7QM2XWR4D")
  })

  it("accepts only the code alphabet", () => {
    expect(normaliseRef(" k7qm2xwr4d ")).toBe("K7QM2XWR4D")
    expect(normaliseRef("K7QM2XWR4I")).toBeNull()
    expect(normaliseRef("SHORT")).toBeNull()
    expect(normaliseRef(null)).toBeNull()
  })
})
