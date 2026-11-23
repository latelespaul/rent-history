import type { IconName } from "@/components/ui/icon"
import type { Progress } from "@/features/verification/progress"

/** Who the shell is drawn for. `null` is an anonymous visitor. */
export interface ShellViewer {
  /** Public short name, e.g. "Demo T." (D-28, D-55). */
  shortName: string
  roles: ReadonlyArray<"TENANT" | "LANDLORD" | "ADMIN">
  /** True for seed users (D-68): the top bar then shows "Demo data". */
  isDemo: boolean
  /** The verification row of the account menu and the chip's dot (D-121); null when done. */
  progress?: Progress | null
}

export interface NavItem {
  to: string
  label: string
  icon: IconName
  /** Active only on this exact path, not on its children. */
  end?: boolean
}

/** The role-aware top bar links (DESIGN.md §3 routes, mockups' top bar). */
export function navFor(viewer: ShellViewer | null): NavItem[] {
  const search: NavItem = { to: "/search", label: "Search", icon: "search" }
  // Anonymous, or signed in but no role picked yet (D-73): only the public pages.
  if (!viewer || viewer.roles.length === 0) return [search]
  if (viewer.roles.includes("LANDLORD")) {
    return [
      search,
      { to: "/me/dashboard", label: "Dashboard", icon: "chart" },
      { to: "/me/tenancies", label: "Tenancies", icon: "tenancy" },
    ]
  }
  return [search, { to: "/me/tenancies", label: "My tenancies", icon: "tenancy" }]
}

/** Two-letter initials for the avatar, from the short name ("Demo T." → "DT"). */
export function initials(name: string): string {
  const letters = name
    .split(/\s+/)
    .map((word) => word.replace(/[^\p{L}\p{N}]/gu, "").charAt(0))
    .filter(Boolean)
  return (letters.slice(0, 2).join("") || "?").toUpperCase()
}

/**
 * The side a viewer's avatar is coloured for: landlord clay, tenant indigo. The main site never
 * shows an admin (the admin console has its own host, SYSTEM_DESIGN §13.3).
 */
export function viewerTone(viewer: ShellViewer): "landlord" | "tenant" {
  return viewer.roles.includes("LANDLORD") ? "landlord" : "tenant"
}
