import { Badge } from "@/components/ui/badge"
import { StarIcon } from "@/components/ui/icons.gen"
import { cn } from "@/lib/utils"
import type { RatingSummary } from "@/types/api"
import { hasAverage, reviewCount } from "./labels"

/** Five stars filled to the nearest whole star; the number beside them is the real value. */
export function Stars({ value, className }: { value: number; className?: string }) {
  const filled = Math.round(value)
  return (
    <span className={cn("inline-flex", className)} aria-hidden>
      {[1, 2, 3, 4, 5].map((n) => (
        <StarIcon
          key={n}
          size={14}
          className={n <= filled ? "fill-marigold-500 text-marigold-500" : "text-(--rx-star-empty)"}
        />
      ))}
    </span>
  )
}

/**
 * A rating as the API sends it: stars and the one-decimal average from 3 reviews on, else
 * "New · n reviews" (D-52), and "No reviews yet" at zero.
 */
export function RatingLine({
  rating,
  className,
}: {
  rating: RatingSummary | null | undefined
  className?: string
}) {
  const count = rating?.reviewCount ?? 0
  if (hasAverage(rating)) {
    return (
      <span
        className={cn("inline-flex items-center gap-1.5 text-sm", className)}
        aria-label={`Rated ${rating.average.toFixed(1)} out of 5 from ${reviewCount(count)}`}
      >
        <Stars value={rating.average} />
        <span className="font-semibold text-ink-900">{rating.average.toFixed(1)}</span>
        <span className="text-ink-500">({count})</span>
      </span>
    )
  }
  if (count === 0) {
    return <span className={cn("text-sm text-ink-500", className)}>No reviews yet</span>
  }
  return (
    <span className={cn("inline-flex items-center gap-1.5 text-sm", className)}>
      <Badge variant="verified">New</Badge>
      <span className="text-ink-700">{reviewCount(count)}</span>
    </span>
  )
}
