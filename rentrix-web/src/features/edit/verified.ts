import { BRAND } from "@/lib/brand"
import type { VerificationState } from "@/types/api"

/**
 * Owners edit their own entries (D-156): free while the team has not verified them; once
 * verified, a save or delete first warns that the green tick goes until the team checks again.
 */
export const VERIFIED_EDIT_WARNING = `Saving will remove the Verified by ${BRAND.name} tick until our team checks it again.`

export const VERIFIED_DELETE_WARNING = `This item has the Verified by ${BRAND.name} tick. Deleting it removes it, with its tick, for good.`

/** Whether a change needs the warning first: only an item that has the team's green tick. */
export function needsWarning(state: VerificationState | undefined): boolean {
  return state === "VERIFIED"
}
