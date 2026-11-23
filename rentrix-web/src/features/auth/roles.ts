import type { CurrentUser } from "@/types/api"

export type PickableRole = "TENANT" | "LANDLORD"

export const ROLE_COPY: Record<PickableRole, { title: string; badge: string; text: string }> = {
  TENANT: {
    title: "I rent a home",
    badge: "Tenant",
    text: "Check landlords and homes, record your tenancy, review your landlord and home, send enquiries.",
  },
  LANDLORD: {
    title: "I rent out a property",
    badge: "Landlord",
    text: "List properties, invite and confirm tenants, review tenants, see the profile of tenants who enquire.",
  },
}

/** Where a newly onboarded user lands without `?next=` (DESIGN.md §2.2). */
export function roleHome(roles: CurrentUser["roles"]): string {
  return roles.includes("LANDLORD") ? "/me/dashboard" : "/me/tenancies"
}

export interface Group {
  key: string
  label: string
  users: CurrentUser[]
}

/**
 * Landlords, tenants, then seed users who have not picked a role yet. The seed admin is not
 * offered on the main site; it signs in on the admin host (SYSTEM_DESIGN §13.3).
 */
export function groupDevUsers(users: CurrentUser[]): Group[] {
  const groups: Group[] = [
    { key: "landlords", label: "Landlords", users: [] },
    { key: "tenants", label: "Tenants", users: [] },
    { key: "new", label: "Not onboarded", users: [] },
  ]
  const [landlords, tenants, fresh] = groups
  for (const user of users) {
    if (user.needsOnboarding) fresh.users.push(user)
    else if (user.roles.includes("LANDLORD")) landlords.users.push(user)
    else tenants.users.push(user)
  }
  return groups.filter((g) => g.users.length > 0)
}
