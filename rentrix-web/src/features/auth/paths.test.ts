import { describe, expect, it } from "vitest"
import type { CurrentUser } from "@/types/api"
import { afterSignInPath, loginPath, safeNext, shortName } from "./paths"

describe("auth paths", () => {
  it("keeps only same-app next paths", () => {
    expect(safeNext("/settings?tab=1")).toBe("/settings?tab=1")
    expect(safeNext(null)).toBe("/")
    expect(safeNext("https://evil.test")).toBe("/")
    expect(safeNext("//evil.test")).toBe("/")
    expect(safeNext("/\\evil.test")).toBe("/")
    expect(safeNext("", "/me/tenancies")).toBe("/me/tenancies")
  })

  it("builds the login path with next", () => {
    expect(loginPath("/")).toBe("/login")
    expect(loginPath("/settings?x=1")).toBe("/login?next=%2Fsettings%3Fx%3D1")
  })

  it("sends users who have not picked a role to /welcome", () => {
    const user = { needsOnboarding: true } as CurrentUser
    expect(afterSignInPath(user, "/")).toBe("/welcome")
    expect(afterSignInPath(user, "/settings")).toBe("/welcome?next=%2Fsettings")
    expect(afterSignInPath({ ...user, needsOnboarding: false }, "/settings")).toBe("/settings")
  })

  it("makes the public short name", () => {
    expect(shortName("Asha Rao")).toBe("Asha R.")
    expect(shortName("  Demo tenant BLR-1 ")).toBe("Demo T.")
    expect(shortName("Cher")).toBe("Cher")
    expect(shortName(" ")).toBe("?")
  })
})
