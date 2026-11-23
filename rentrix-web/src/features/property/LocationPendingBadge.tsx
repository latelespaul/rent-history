import { Badge } from "@/components/ui/badge"

/**
 * "Pending review" on a home whose area, society, tower or wing is a new name: it shows at once
 * (open by default), and the team reviews it (captain 2026-11-23, rules R2 / R8).
 */
export function LocationPendingBadge({ className }: { className?: string }) {
  return (
    <Badge
      variant="warn"
      className={className}
      data-testid="location-pending"
      title="A new place name. It shows now; the team reviews it."
    >
      Pending review
    </Badge>
  )
}
