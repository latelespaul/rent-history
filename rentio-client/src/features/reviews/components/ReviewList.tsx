import { Loader2 } from "lucide-react"
import type { Review } from "@/types"
import { ReviewCard } from "./ReviewCard"

interface Props {
  reviews: Review[]
  isLoading: boolean
}

export function ReviewList({ reviews, isLoading }: Props) {
  if (isLoading) {
    return (
      <div className="flex items-center gap-2 py-8 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading reviews…
      </div>
    )
  }

  if (reviews.length === 0) {
    return (
      <p className="rounded-lg border py-8 text-center text-sm text-muted-foreground">
        No reviews yet. Be the first to review this flat.
      </p>
    )
  }

  return (
    <div className="space-y-3">
      {reviews.map((r) => (
        <ReviewCard key={r.id} review={r} />
      ))}
    </div>
  )
}
