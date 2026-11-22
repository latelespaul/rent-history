import { StarIcon } from "@/components/ui/icons.gen"
import { EmptyState, ErrorState } from "@/components/ui/states"
import type { RatingSummary } from "@/types/api"
import { monthLabel, reviewCount } from "./labels"
import { Stars } from "./Rating"
import { SignInPrompt } from "./SignInPrompt"
import { usePropertyReviews } from "./useProperties"

/**
 * What tenants say about the home. Review text is for signed-in users (D-56); anonymous visitors
 * see the count. Until the API serves reviews (B6) the card says so.
 */
export function PropertyReviews({
  propertyId,
  rating,
  signedIn,
}: {
  propertyId: string
  rating: RatingSummary
  signedIn: boolean
}) {
  const reviews = usePropertyReviews(propertyId, signedIn)

  let body
  if (!signedIn) {
    body = (
      <SignInPrompt
        what={
          rating.reviewCount > 0
            ? `Sign in to read ${reviewCount(rating.reviewCount)} from verified tenants.`
            : "Sign in to read reviews from verified tenants."
        }
      />
    )
  } else if (reviews.isPending) {
    body = <p className="text-sm text-ink-500">Loading reviews…</p>
  } else if (reviews.isError) {
    body = (
      <ErrorState
        title="Reviews could not be loaded"
        error={reviews.error}
        onRetry={() => void reviews.refetch()}
      />
    )
  } else if (reviews.data === null) {
    body = (
      <EmptyState
        icon="star"
        title="Reviews are not available yet"
        description="Reviews from confirmed tenancies will show here once reviews are live."
      />
    )
  } else if (reviews.data.length === 0) {
    body = (
      <EmptyState
        icon="star"
        title="No reviews yet"
        description="Only tenants with a confirmed tenancy here can review this home."
      />
    )
  } else {
    body = (
      <ul className="divide-y divide-line">
        {reviews.data.map((review) => (
          <li key={review.id} className="space-y-1.5 py-4 first:pt-0">
            <p className="text-sm font-semibold text-ink-900">{review.authorName ?? "Tenant"}</p>
            <Stars value={review.rating} />
            {review.text && <p className="text-ink-700">{review.text}</p>}
            {review.publishedAt && (
              <p className="text-xs text-ink-500">Published {monthLabel(review.publishedAt)}</p>
            )}
          </li>
        ))}
      </ul>
    )
  }

  return (
    <section
      aria-labelledby="reviews-heading"
      className="space-y-3 rounded-xl border border-line bg-surface p-5 shadow-sm"
    >
      <div className="flex items-center justify-between gap-2">
        <h2 id="reviews-heading" className="inline-flex items-center gap-2 text-lg font-semibold">
          <StarIcon size={20} />
          What tenants say about the home
        </h2>
        <span className="text-sm text-ink-500">{reviewCount(rating.reviewCount)}</span>
      </div>
      {body}
    </section>
  )
}
