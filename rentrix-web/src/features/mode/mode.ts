import type { Role, TenancyParty } from "@/types/api"

/**
 * The header's Tenant | Owner switch (PLAN R10): one account, both sides (D-16). The mode picks the
 * home page, the "+ Add your home" action and which side's tenancies and reviews are listed.
 * It is a view choice only: switching never adds a role, and the API still checks every write.
 */
export type Mode = "TENANT" | "OWNER"

export const MODES: ReadonlyArray<{ value: Mode; label: string }> = [
  { value: "TENANT", label: "Tenant" },
  { value: "OWNER", label: "Owner" },
]

/** Per user, so two accounts on one browser keep their own choice. */
export const MODE_KEY_PREFIX = "doorway.mode."

interface ModeUser {
  id: string
  roles: ReadonlyArray<Role>
}

/** What a browser without storage (private window, blocked site data) remembers for this tab. */
const memory = new Map<string, Mode>()
const listeners = new Set<() => void>()

function isMode(value: unknown): value is Mode {
  return value === "TENANT" || value === "OWNER"
}

/** Tenant, except for an owner-only account, which has nothing to see as a tenant. */
export function defaultMode(roles: ReadonlyArray<Role>): Mode {
  return roles.includes("LANDLORD") && !roles.includes("TENANT") ? "OWNER" : "TENANT"
}

/** The saved mode of this user, else the default. Never throws. */
export function readMode(user: ModeUser): Mode {
  const remembered = memory.get(user.id)
  if (remembered) return remembered
  try {
    const saved = window.localStorage.getItem(MODE_KEY_PREFIX + user.id)
    if (isMode(saved)) return saved
  } catch {
    // Storage can be blocked.
  }
  return defaultMode(user.roles)
}

/** Saves the mode for this user and tells every `useMode` reader. Never throws. */
export function saveMode(userId: string, mode: Mode) {
  try {
    window.localStorage.setItem(MODE_KEY_PREFIX + userId, mode)
    memory.delete(userId)
  } catch {
    memory.set(userId, mode)
  }
  listeners.forEach((listener) => listener())
}

/** Subscribes to mode changes in this tab and, through the `storage` event, in other tabs. */
export function subscribeMode(listener: () => void): () => void {
  listeners.add(listener)
  window.addEventListener("storage", listener)
  return () => {
    listeners.delete(listener)
    window.removeEventListener("storage", listener)
  }
}

/** Where a mode starts: My tenancies for a tenant, the dashboard for an owner. */
export function modeHome(mode: Mode): string {
  return mode === "OWNER" ? "/me/dashboard" : "/me/tenancies"
}

/** Owner-only pages: opening one puts the header in Owner mode. */
export function isOwnerPath(pathname: string): boolean {
  return /^\/me\/(dashboard|properties)(\/|$)/.test(pathname)
}

/** The tenancy side (`?role=`, `?as=`) a mode lists. */
export function modeSide(mode: Mode): TenancyParty {
  return mode === "OWNER" ? "LANDLORD" : "TENANT"
}

/** Where "+ Add your home" goes: an owner adds a home with "I own it" already picked. */
export function addHomePath(mode: Mode): string {
  return mode === "OWNER" ? "/homes/new?own=1" : "/homes/new"
}
