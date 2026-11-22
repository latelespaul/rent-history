import { shortName } from "@/features/auth/paths"
import type { CurrentUser } from "@/types/api"

/** Test data only: users as `/me`, `/auth/*` and `/auth/dev-users` return them. */
export function makeUser(overrides: Partial<CurrentUser> = {}): CurrentUser {
  const displayName = overrides.displayName ?? "Asha Rao"
  return {
    shortName: shortName(displayName),
    id: "00000000-0000-7000-8000-000000000001",
    displayName: "Asha Rao",
    email: "asha@example.test",
    emailVerified: true,
    avatarUrl: null,
    phone: null,
    roles: ["TENANT"],
    homeCity: "BLR",
    status: "ACTIVE",
    demo: false,
    needsOnboarding: false,
    onboardedAt: "2026-11-01T00:00:00Z",
    verification: { TENANT: "APPROVED" },
    createdAt: "2026-11-01T00:00:00Z",
    ...overrides,
  }
}

export const NEW_USER = makeUser({
  id: "00000000-0000-7000-8000-000000000009",
  displayName: "New User",
  email: "new.user@example.test",
  roles: [],
  homeCity: null,
  needsOnboarding: true,
  onboardedAt: null,
  verification: {},
})

export const DEV_USERS: CurrentUser[] = [
  makeUser({
    id: "a1",
    displayName: "Demo Admin",
    email: "demo-admin@example.test",
    roles: ["ADMIN"],
    homeCity: null,
    demo: true,
    verification: {},
  }),
  makeUser({
    id: "l1",
    displayName: "Demo Landlord BLR-1",
    roles: ["LANDLORD"],
    homeCity: "BLR",
    demo: true,
    verification: { LANDLORD: "APPROVED" },
  }),
  makeUser({
    id: "l3",
    displayName: "Demo Landlord BLR-3",
    roles: ["LANDLORD"],
    homeCity: "BLR",
    demo: true,
    verification: { LANDLORD: "PENDING" },
  }),
  makeUser({
    id: "t1",
    displayName: "Demo Tenant PNQ-1",
    roles: ["TENANT"],
    homeCity: "PNQ",
    demo: true,
  }),
  makeUser({
    id: "t8",
    displayName: "Demo Tenant BLR-8",
    roles: ["TENANT"],
    homeCity: "BLR",
    demo: true,
    verification: { TENANT: "REJECTED" },
  }),
]
