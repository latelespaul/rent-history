import { Badge, VerifiedBadge } from "@/components/ui/badge"
import { BRAND } from "@/lib/brand"
import type { VerificationState } from "@/types/api"

/**
 * The green tick of one review, home or tenancy (D-134): "Verified by Doorway Living" once the team
 * checked it, else "Not verified yet". Everything shows at once; the tick comes later (D-132).
 */
export function ReviewTick({ state }: { state: VerificationState | undefined }) {
  return state === "VERIFIED" ? (
    <VerifiedBadge data-testid="tick-verified">Verified by {BRAND.name}</VerifiedBadge>
  ) : (
    <Badge data-testid="tick-unverified">Not verified yet</Badge>
  )
}
