// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { act, cleanup, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import {
  addHomePath,
  adoptServerMode,
  defaultMode,
  isOwnerPath,
  MODE_KEY_PREFIX,
  modeHome,
  modeSide,
  readMode,
  saveMode,
  subscribeMode,
} from "./mode"
import { ModeSwitch } from "./ModeSwitch"

const BOTH = { id: "u-both", roles: ["TENANT", "LANDLORD"] as const }

beforeEach(() => window.localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe("mode", () => {
  it("defaults to Tenant, and to Owner only for an owner-only account", () => {
    expect(defaultMode(["TENANT"])).toBe("TENANT")
    expect(defaultMode(["TENANT", "LANDLORD"])).toBe("TENANT")
    expect(defaultMode([])).toBe("TENANT")
    expect(defaultMode(["LANDLORD"])).toBe("OWNER")
    expect(readMode({ id: "u-new", roles: ["TENANT", "LANDLORD"] })).toBe("TENANT")
  })

  it("remembers the mode per user in localStorage", () => {
    saveMode(BOTH.id, "OWNER")
    expect(window.localStorage.getItem(`${MODE_KEY_PREFIX}u-both`)).toBe("OWNER")
    expect(readMode(BOTH)).toBe("OWNER")
    expect(readMode({ id: "u-other", roles: ["TENANT", "LANDLORD"] })).toBe("TENANT")
  })

  it("ignores a stored value that is not a mode", () => {
    window.localStorage.setItem(`${MODE_KEY_PREFIX}u-junk`, "ADMIN")
    expect(readMode({ id: "u-junk", roles: ["LANDLORD"] })).toBe("OWNER")
  })

  it("keeps working when storage throws", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked")
    })
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked")
    })
    expect(() => saveMode("u-private", "OWNER")).not.toThrow()
    expect(readMode({ id: "u-private", roles: ["TENANT"] })).toBe("OWNER")
  })

  it("keeps the new choice when storage reads but cannot write", () => {
    window.localStorage.setItem(`${MODE_KEY_PREFIX}u-full`, "TENANT")
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("quota")
    })
    saveMode("u-full", "OWNER")
    expect(readMode({ id: "u-full", roles: ["TENANT", "LANDLORD"] })).toBe("OWNER")
  })

  it("follows a choice another tab stored", () => {
    saveMode("u-tabs", "TENANT")
    window.localStorage.setItem(`${MODE_KEY_PREFIX}u-tabs`, "OWNER")
    expect(readMode({ id: "u-tabs", roles: ["TENANT", "LANDLORD"] })).toBe("OWNER")
  })

  it("takes the account's mode on sign-in, and keeps the local one when the account has none", () => {
    saveMode(BOTH.id, "TENANT")
    adoptServerMode({ ...BOTH, preferredMode: "OWNER" })
    expect(readMode(BOTH)).toBe("OWNER")
    expect(window.localStorage.getItem(MODE_KEY_PREFIX + BOTH.id)).toBe("OWNER")
    adoptServerMode({ ...BOTH, preferredMode: null })
    adoptServerMode(BOTH)
    expect(readMode(BOTH)).toBe("OWNER")
  })

  it("treats only the dashboard and the listing pages as owner pages", () => {
    expect(isOwnerPath("/me/dashboard")).toBe(true)
    expect(isOwnerPath("/me/properties")).toBe(true)
    expect(isOwnerPath("/me/properties/p1/edit")).toBe(true)
    expect(isOwnerPath("/me/tenancies")).toBe(false)
    expect(isOwnerPath("/me/propertiesx")).toBe(false)
    expect(isOwnerPath("/homes/new")).toBe(false)
  })

  it("tells subscribers about a change", () => {
    const listener = vi.fn()
    const stop = subscribeMode(listener)
    saveMode(BOTH.id, "TENANT")
    expect(listener).toHaveBeenCalledTimes(1)
    stop()
    saveMode(BOTH.id, "OWNER")
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it("maps each mode to its home, side and add-home path", () => {
    expect(modeHome("TENANT")).toBe("/me/tenancies")
    expect(modeHome("OWNER")).toBe("/me/dashboard")
    expect(modeSide("TENANT")).toBe("TENANT")
    expect(modeSide("OWNER")).toBe("LANDLORD")
    expect(addHomePath("TENANT")).toBe("/homes/new")
    expect(addHomePath("OWNER")).toBe("/homes/new?own=1")
  })
})

describe("ModeSwitch", () => {
  it("marks the current mode and reports the other one", async () => {
    const onChange = vi.fn()
    render(<ModeSwitch mode="TENANT" onChange={onChange} />)
    const tenant = screen.getByRole("button", { name: "Tenant" })
    const owner = screen.getByRole("button", { name: "Owner" })
    expect(tenant.getAttribute("aria-pressed")).toBe("true")
    expect(owner.getAttribute("aria-pressed")).toBe("false")

    const user = userEvent.setup()
    await act(() => user.click(tenant))
    expect(onChange).not.toHaveBeenCalled()
    await act(() => user.click(owner))
    expect(onChange).toHaveBeenCalledWith("OWNER")
  })
})
