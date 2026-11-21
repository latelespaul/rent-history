import type { IconName } from "@/components/ui/icon"

/** Who the shell is drawn for. `null` is an anonymous visitor. */
export interface ShellViewer {
  /** Public short name, e.g. "Demo T." (D-28, D-55). */
  shortName: string
  roles: ReadonlyArray<"TENANT" | "LANDLORD" | "ADMIN">
  /** True for seed users (D-68): the top bar then shows "Demo data". */
  isDemo: boolean
}

export interface NavItem {
  to: string
  label: string
  icon: IconName
}

/** The role-aware top bar links (DESIGN.md §3 routes, mockups' top bar). */
export function navFor(viewer: ShellViewer | null): NavItem[] {
  const search: NavItem = { to: "/search", label: "Search", icon: "search" }
  if (!viewer) return [search]
  if (viewer.roles.includes("ADMIN")) {
    return [
      { to: "/admin", label: "Dashboard", icon: "chart" },
      { to: "/admin/approvals", label: "Approvals", icon: "shield-check" },
      { to: "/admin/records/users", label: "Records", icon: "users" },
      { to: "/admin/cities", label: "Cities", icon: "city" },
    ]
  }
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

/** The side a viewer's avatar is coloured for: admin ink, landlord clay, tenant indigo. */
export function viewerTone(viewer: ShellViewer): "admin" | "landlord" | "tenant" {
  if (viewer.roles.includes("ADMIN")) return "admin"
  return viewer.roles.includes("LANDLORD") ? "landlord" : "tenant"
}
