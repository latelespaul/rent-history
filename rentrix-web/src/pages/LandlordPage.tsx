import { useQuery, type UseQueryResult } from "@tanstack/react-query"
import { Link, useParams } from "react-router-dom"
import { isApiError } from "@/api/errors"
import { useAuth } from "@/features/auth/authContext"
import { fetchUserProfile, fetchUserReviews, reviewKeys } from "@/api/reviews"
import { LandlordIcon, TenantIcon } from "@/components/ui/icons.gen"
import { Button } from "@/components/ui/button"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { listedReviewCount, monthLabel } from "@/features/property/labels"
import { ReviewItem } from "@/features/property/PropertyReviews"
import { RatingLine } from "@/features/property/Rating"
import { TrustBadges } from "@/features/property/TrustBadges"
import { RatingBreakdown } from "@/features/review/RatingBreakdown"
import type { Page, PropertyReview, RatingDetail } from "@/types/api"

/**
 * `/u/:id`: a person's page for signed-in users. The landlord side (D-55, PLAN W6) shows their
 * rating as landlord with the review form's per-parameter averages (D-158) and the published
 * reviews they received. The tenant side (landlord-adds-tenant S2) shows the rating as tenant,
 * "The tenant by topic", "would rent to them again" and what owners say, only when the server
 * sends `tenantRating`: to the owners D-54 allows and admins, never to the tenant themself (D-175).
 * The reviews keep their "Not verified yet" label until the team's tick (D-134).
 */
export default function LandlordPage() {
  const { id = "" } = useParams()
  const { state } = useAuth()
  const ownTenantPage = state.user?.id === id && !!state.user?.roles.includes("TENANT")
  const profile = useQuery({
    queryKey: reviewKeys.user(id),
    queryFn: ({ signal }) => fetchUserProfile(id, signal),
  })
  const landlordReviews = useQuery({
    queryKey: reviewKeys.userReviews(id, "LANDLORD"),
    queryFn: ({ signal }) => fetchUserReviews(id, "LANDLORD", signal),
    enabled: !!profile.data?.landlordRating,
  })
  const tenantReviews = useQuery({
    queryKey: reviewKeys.userReviews(id, "TENANT"),
    queryFn: ({ signal }) => fetchUserReviews(id, "TENANT", signal),
    enabled: !!profile.data?.tenantRating,
  })

  if (profile.isPending) {
    return (
      <main className="mx-auto max-w-page px-4 py-12 nav:px-6">
        <p className="text-sm text-ink-500">Loading the profile…</p>
      </main>
    )
  }
  const person = profile.data
  const self = !!person?.shortName
  if (profile.isError || (!person?.landlordRating && !person?.tenantRating && !self)) {
    const missing = !person || (isApiError(profile.error) && profile.error.status === 404)
    return (
      <main className="mx-auto max-w-page px-4 py-12 nav:px-6">
        {missing ? (
          <EmptyState
            icon="home"
            title="This profile is not available"
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

  const landlord = !!person.landlordRating
  return (
    <main className="mx-auto max-w-[720px] space-y-6 px-4 py-8 nav:px-6">
      <section
        className="flex items-start gap-4 rounded-xl border border-line bg-surface p-5 shadow-sm"
        aria-labelledby="landlord-name"
      >
        {landlord ? (
          <span className="grid size-14 shrink-0 place-items-center rounded-full bg-landlord-100 text-lg font-semibold text-landlord-600">
            <LandlordIcon className="size-7" aria-hidden="true" />
          </span>
        ) : (
          <span className="grid size-14 shrink-0 place-items-center rounded-full bg-tenant-100 text-lg font-semibold text-tenant-600">
            <TenantIcon className="size-7" aria-hidden="true" />
          </span>
        )}
        <div className="min-w-0 space-y-1">
          <h1
            id="landlord-name"
            className="flex flex-wrap items-center gap-2 font-display text-2xl font-semibold"
          >
            {/* Never the name (D-168), except on the person's own profile. */}
            {person.shortName ? `${person.shortName} (you)` : person.label}
          </h1>
          <p className="flex flex-wrap gap-2 text-sm text-ink-500">
            <span>Member since {monthLabel(person.memberSince)}</span>
            {person.listedHomes !== undefined && (
              <span>
                · {person.listedHomes} {person.listedHomes === 1 ? "home" : "homes"} listed
              </span>
            )}
          </p>
          <p className="flex flex-wrap gap-2">
            <TrustBadges badges={person.badges} />
          </p>
        </div>
      </section>

      {person.landlordRating && (
        <>
          <RatingSection
            side="landlord"
            rating={person.landlordRating}
            heading="Rating as landlord"
            topicTitle="The landlord by topic"
          />
          <ReviewsSection
            side="landlord"
            title="What tenants say about this landlord"
            rating={person.landlordRating}
            reviews={landlordReviews}
            empty="Tenants review their landlord 60 days after moving in, or when they move out."
          />
        </>
      )}

      {person.tenantRating && (
        <>
          <RatingSection
            side="tenant"
            rating={person.tenantRating}
            heading="Rating as tenant"
            topicTitle="The tenant by topic"
            recommendPhrase="would rent to them again"
          />
          <ReviewsSection
            side="tenant"
            title="What owners say"
            rating={person.tenantRating}
            reviews={tenantReviews}
            empty="Owners review their tenant 60 days after the move-in, or when the tenant moves out."
          />
        </>
      )}

      {ownTenantPage && !person.tenantRating && (
        <p className="text-sm text-ink-500" data-testid="own-tenant-reviews-note">
          Owners&apos; reviews of a tenant are shown only to owners and our team, never to the
          tenant they are about.
        </p>
      )}
    </main>
  )
}

function RatingSection({
  side,
  rating,
  heading,
  topicTitle,
  recommendPhrase,
}: {
  side: "landlord" | "tenant"
  rating: RatingDetail
  heading: string
  topicTitle: string
  recommendPhrase?: string
}) {
  return (
    <section
      aria-labelledby={`${side}-rating-heading`}
      className="space-y-3 rounded-xl border border-line bg-surface p-5 shadow-sm"
      data-testid={`${side}-rating`}
    >
      <h2
        id={`${side}-rating-heading`}
        className="text-xs font-semibold tracking-[0.08em] text-teal-700 uppercase"
      >
        {heading}
      </h2>
      <RatingLine rating={rating} className="text-base" />
      {rating.average === null && rating.reviewCount > 0 && (
        <p className="text-sm text-ink-500">
          An average appears after 3 reviews, so one review cannot define a person.
        </p>
      )}
      <RatingBreakdown
        rating={rating}
        title={topicTitle}
        testId={`${side}-breakdown`}
        recommendPhrase={recommendPhrase}
      />
    </section>
  )
}

function ReviewsSection({
  side,
  title,
  rating,
  reviews,
  empty,
}: {
  side: "landlord" | "tenant"
  title: string
  rating: RatingDetail
  reviews: UseQueryResult<Page<PropertyReview>>
  empty: string
}) {
  let list
  if (reviews.isPending) list = <p className="text-sm text-ink-500">Loading reviews…</p>
  else if (reviews.isError)
    list = <ErrorState error={reviews.error} onRetry={() => void reviews.refetch()} />
  else if (reviews.data.items.length === 0)
    list = <EmptyState icon="star" title="No reviews yet" description={empty} />
  else
    list = (
      <ul className="divide-y divide-line">
        {reviews.data.items.map((review) => (
          <ReviewItem key={review.id} review={review} propertyId={review.property?.id ?? ""} />
        ))}
      </ul>
    )
  return (
    <section
      aria-labelledby={`${side}-reviews-heading`}
      className="space-y-3 rounded-xl border border-line bg-surface p-5 shadow-sm"
      data-testid={`${side}-reviews`}
    >
      <div className="flex items-center justify-between gap-2">
        <h2 id={`${side}-reviews-heading`} className="text-lg font-semibold">
          {title}
        </h2>
        <span
          className="shrink-0 text-sm whitespace-nowrap text-ink-500"
          data-testid={`${side}-reviews-count`}
        >
          {listedReviewCount(reviews.data?.page.totalItems, rating.reviewCount)}
        </span>
      </div>
      {list}
    </section>
  )
}
