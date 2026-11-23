import { describe, expect, it } from "vitest"
import { DEFAULT_API_BASE_URL, isAdminHost, resolveApiBaseUrl } from "./config"

describe("resolveApiBaseUrl", () => {
  it("prefers the runtime value from public/config.js", () => {
    expect(resolveApiBaseUrl("https://api.rentrix.in/api/v1", "/api/v1")).toBe(
      "https://api.rentrix.in/api/v1",
    )
  })

  it("falls back to the build-time VITE_API_BASE_URL when the runtime value is blank", () => {
    expect(resolveApiBaseUrl("  ", "https://staging.example.test/api/v1")).toBe(
      "https://staging.example.test/api/v1",
    )
  })

  it("defaults to same-origin /api/v1", () => {
    expect(resolveApiBaseUrl(undefined, undefined)).toBe(DEFAULT_API_BASE_URL)
  })

  it("drops trailing slashes", () => {
    expect(resolveApiBaseUrl("https://api.rentrix.in/api/v1//")).toBe(
      "https://api.rentrix.in/api/v1",
    )
  })
})

describe("isAdminHost", () => {
  it("is true for the admin console's hosts", () => {
    expect(isAdminHost("admin.doorwayliving.in")).toBe(true)
    expect(isAdminHost("admin.doorway.129-225-77-1.sslip.io")).toBe(true)
    expect(isAdminHost("ADMIN.localhost")).toBe(true)
  })

  it("is false for the main site", () => {
    expect(isAdminHost("doorwayliving.in")).toBe(false)
    expect(isAdminHost("www.doorwayliving.in")).toBe(false)
    expect(isAdminHost("localhost")).toBe(false)
    expect(isAdminHost("myadmin.example.test")).toBe(false)
  })
})
