import { useQuery } from "@tanstack/react-query"
import { Link, useParams } from "react-router-dom"
import { isApiError } from "@/api/errors"
import { fetchUserProfile, fetchUserReviews, reviewKeys } from "@/api/reviews"
import { initials } from "@/components/layout/nav"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { listedReviewCount, monthLabel } from "@/features/property/labels"
import { ReviewItem } from "@/features/property/PropertyReviews"
import { RatingLine } from "@/features/property/Rating"
import { RatingBreakdown } from "@/features/review/RatingBreakdown"

/**
 * `/u/:id`: a landlord's page for signed-in users (D-55, PLAN W6): their rating as landlord with
 * the review form's per-parameter averages (D-158) and the published reviews they received. The
 * reviews keep their "Not verified yet" label until the team's tick (D-134).
 */
export default function LandlordPage() {
  const { id = "" } = useParams()
  const profile = useQuery({
    queryKey: reviewKeys.user(id),
    queryFn: ({ signal }) => fetchUserProfile(id, signal),
  })
  const reviews = useQuery({
    queryKey: reviewKeys.userReviews(id),
    queryFn: ({ signal }) => fetchUserReviews(id, signal),
    enabled: !!profile.data?.landlordRating,
  })

  if (profile.isPending) {
    return (
      <main className="mx-auto max-w-page px-4 py-12 nav:px-6">
        <p className="text-sm text-ink-500">Loading the landlord…</p>
      </main>
    )
  }
  const person = profile.data
  if (profile.isError || !person?.landlordRating) {
    const missing = !person || (isApiError(profile.error) && profile.error.status === 404)
    return (
      <main className="mx-auto max-w-page px-4 py-12 nav:px-6">
        {missing ? (
          <EmptyState
            icon="home"
            title="This landlord page is not available"
            description="The link may be wrong, or the person does not rent out a home here."
            action={
              <Button asChild variant="outline">
                <Link to="/search">Search homes</Link>
              </Button>
            }
          />
        ) : (
          <ErrorState error={profile.error} onRetry={() => void profile.refetch()} />
        )}
      </main>
    )
  }

  const rating = person.landlordRating
  let list
  if (reviews.isPending) list = <p className="text-sm text-ink-500">Loading reviews…</p>
  else if (reviews.isError)
    list = <ErrorState error={reviews.error} onRetry={() => void reviews.refetch()} />
  else if (reviews.data.items.length === 0)
    list = (
      <EmptyState
        icon="star"
        title="No reviews yet"
        description="Tenants review their landlord 60 days after moving in, or when they move out."
      />
    )
  else
    list = (
      <ul className="divide-y divide-line">
        {reviews.data.items.map((review) => (
          <ReviewItem key={review.id} review={review} propertyId={review.property?.id ?? ""} />
        ))}
      </ul>
    )

  return (
    <main className="mx-auto max-w-[720px] space-y-6 px-4 py-8 nav:px-6">
      <section
        className="flex items-start gap-4 rounded-xl border border-line bg-surface p-5 shadow-sm"
        aria-labelledby="landlord-name"
      >
        <span className="grid size-14 shrink-0 place-items-center rounded-full bg-landlord-100 text-lg font-semibold text-landlord-600">
          {initials(person.shortName)}
        </span>
        <div className="min-w-0 space-y-1">
          <h1
            id="landlord-name"
            className="flex flex-wrap items-center gap-2 font-display text-2xl font-semibold"
          >
            {person.shortName}
            <Badge variant="landlord">Landlord</Badge>
          </h1>
          <p className="flex flex-wrap gap-2 text-sm text-ink-500">
            <span>Member since {monthLabel(person.memberSince)}</span>
            {person.listedHomes !== undefined && (
              <span>
                · {person.listedHomes} {person.listedHomes === 1 ? "home" : "homes"} listed
              </span>
            )}
          </p>
          {person.badges?.idVerified && <Badge>ID verified</Badge>}
        </div>
      </section>

      <section
        aria-labelledby="landlord-rating-heading"
        className="space-y-3 rounded-xl border border-line bg-surface p-5 shadow-sm"
        data-testid="landlord-rating"
      >
        <h2
          id="landlord-rating-heading"
          className="text-xs font-semibold tracking-[0.08em] text-teal-700 uppercase"
        >
          Rating as landlord
        </h2>
        <RatingLine rating={rating} className="text-base" />
        {rating.average === null && rating.reviewCount > 0 && (
          <p className="text-sm text-ink-500">
            An average appears after 3 reviews, so one review cannot define a person.
          </p>
        )}
        <RatingBreakdown
          rating={rating}
          title="The landlord by topic"
          testId="landlord-breakdown"
        />
      </section>

      <section
        aria-labelledby="landlord-reviews-heading"
        className="space-y-3 rounded-xl border border-line bg-surface p-5 shadow-sm"
      >
        <div className="flex items-center justify-between gap-2">
          <h2 id="landlord-reviews-heading" className="text-lg font-semibold">
            What tenants say about this landlord
          </h2>
          <span
            className="shrink-0 text-sm whitespace-nowrap text-ink-500"
            data-testid="landlord-reviews-count"
          >
            {listedReviewCount(reviews.data?.page.totalItems, rating.reviewCount)}
          </span>
        </div>
        {list}
      </section>
    </main>
  )
}
