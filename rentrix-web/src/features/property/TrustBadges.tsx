import { Badge } from "@/components/ui/badge"
import { CheckIcon, DocumentIcon, ShieldCheckIcon } from "@/components/ui/icons.gen"
import type { TrustBadges as Badges } from "@/types/api"

/**
 * Up to three verification badges on a review or a person card (D-168), in place of the person's
 * green tick: "Google verified" (signs in with Google), "ID verified" (any one of phone, Aadhaar
 * or LinkedIn) and "Agreement verified" (only when the API sends that status). Each badge shows
 * only when it holds, and each carries a word, never only a colour.
 */
export function TrustBadges({ badges }: { badges: Badges | null | undefined }) {
  if (!badges) return null
  return (
    <>
      {badges.google && (
        <Badge variant="info" data-testid="badge-google">
          <CheckIcon />
          Google verified
        </Badge>
      )}
      {badges.id && (
        <Badge variant="verified" data-testid="badge-id">
          <ShieldCheckIcon />
          ID verified
        </Badge>
      )}
      {badges.agreement && (
        <Badge variant="verified" data-testid="badge-agreement">
          <DocumentIcon />
          Agreement verified
        </Badge>
      )}
    </>
  )
}
