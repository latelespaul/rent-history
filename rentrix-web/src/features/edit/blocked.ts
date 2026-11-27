import { isApiError } from "@/api/errors"
import type { ChangeRequestInput } from "@/types/api"

/**
 * The refusals that protect other people's records (D-156). The user is never left at a dead end:
 * each one shows its plain reason with "Ask our team to change this".
 */
const BLOCKED = new Set([
  "confirmed_by_other_side",
  "tenancy_has_reviews",
  "owner_has_records",
  "home_in_use",
  "location_locked",
  "starting_rent",
  "role_change_not_allowed",
])

export type ChangeTarget = Omit<ChangeRequestInput, "note">

export function isBlocked(error: unknown): boolean {
  return isApiError(error) && BLOCKED.has(error.code)
}
