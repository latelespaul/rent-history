import { useQuery } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { fetchMyReceivedReviews, fetchMyWrittenReviews, myReviewKeys } from "@/api/reviews"
import { Badge } from "@/components/ui/badge"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { monthLabel } from "@/features/property/labels"
import { Stars } from "@/features/property/Rating"
import { ReviewTick } from "@/features/property/ReviewTick"
import { useSignedIn } from "@/features/property/useProperties"
import type { PropertyReview, TenancyParty } from "@/types/api"

const DIRECTION_LABEL = {
  TENANT_TO_PROPERTY: "Home",
  TENANT_TO_LANDLORD: "Landlord",
  LANDLORD_TO_TENANT: "Tenant",
} as const

/** Who or what the review is about: the person, else the home. */
function about(review: PropertyReview): string {
  const home = review.property?.buildingName || review.property?.locality
  if (review.direction === "TENANT_TO_PROPERTY" || !review.subject) return home ?? "A home"
  // A label, never the other person's name (D-168).
  const who = review.subject.label
  return home ? `${who} · ${home}` : who
}

function period(review: PropertyReview): string | null {
  const from = monthLabel(review.period?.from)
  if (!from) return null
  return `${from} – ${monthLabel(review.period?.to) ?? "now"}`
}

/** One compact row; the home's page holds the full review and its edit buttons. */
function ReviewRow({ review, kind }: { review: PropertyReview; kind: "written" | "received" }) {
  // A review about the caller still hidden until theirs (D-46): no text, ratings or author.
  if (review.status === "AWAITING_REVEAL" && kind === "received") {
    return (
      <li className="px-5 py-4 text-sm text-ink-700" data-testid="my-review-pending">
        A review about you is waiting. It shows when you review them too, or after 14 days.
      </li>
    )
  }
  const label = review.direction ? DIRECTION_LABEL[review.direction] : null
  // The stay only on the caller's own reviews: on one about them it would tell who wrote it (P1, 2026-11-26).
  const when = kind === "written" ? period(review) : null
  return (
    <li className="space-y-1.5 px-5 py-4" data-testid="my-review">
      <p className="flex flex-wrap items-center gap-2">
        {label && <Badge variant={label === "Tenant" ? "tenant" : "landlord"}>{label}</Badge>}
        {review.property ? (
          <Link to={`/p/${review.property.id}`} className="font-semibold text-ink-900">
            {about(review)}
          </Link>
        ) : (
          <span className="font-semibold text-ink-900">{about(review)}</span>
        )}
        {review.ratingOverall != null && (
          <span className="inline-flex items-center gap-1">
            <Stars value={review.ratingOverall} />
            <span className="sr-only">{review.ratingOverall} out of 5</span>
          </span>
        )}
      </p>
      {review.body && <p className="line-clamp-2 text-sm text-ink-700">{review.body}</p>}
      <p className="flex flex-wrap items-center gap-2 text-xs text-ink-500">
        {kind === "received" && review.author && <span>By {review.author.label}</span>}
        {when && <span className="num">{when}</span>}
        {review.status === "AWAITING_REVEAL" && <Badge>Waiting for the other side</Badge>}
        <ReviewTick state={review.verification} />
      </p>
    </li>
  )
}

/**
 * The caller's reviews from one side of their tenancies, for the mode views (PLAN R10):
 * `written` lists the ones they wrote, `received` the ones about them.
 */
export function MyReviews({
  kind,
  side,
  title,
  empty,
  testId,
}: {
  kind: "written" | "received"
  side: TenancyParty
  title: string
  empty: string
  testId: string
}) {
  const { ready, signedIn, viewer } = useSignedIn()
  const query = useQuery({
    queryKey:
      kind === "written" ? myReviewKeys.written(viewer, side) : myReviewKeys.received(viewer, side),
    queryFn: ({ signal }) =>
      kind === "written"
        ? fetchMyWrittenReviews(side, signal)
        : fetchMyReceivedReviews(side, signal),
    enabled: ready && signedIn,
  })
  const items = query.data?.items ?? []
  const headingId = `${testId}-heading`

  return (
    <section
      aria-labelledby={headingId}
      data-testid={testId}
      className="rounded-xl border border-line bg-surface shadow-sm"
    >
      <h2 id={headingId} className="px-5 pt-4 pb-2 text-xl font-semibold">
        {title}
        {query.isSuccess && ` (${query.data.page.totalItems})`}
      </h2>
      {query.isPending ? (
        <p className="px-5 pb-5 text-sm text-ink-500">Loading reviews…</p>
      ) : query.isError ? (
        <div className="p-5">
          <ErrorState
            title="Reviews could not be loaded"
            error={query.error}
            onRetry={() => void query.refetch()}
          />
        </div>
      ) : items.length === 0 ? (
        <div className="p-5">
          <EmptyState icon="star" title="No reviews yet" description={empty} />
        </div>
      ) : (
        <ul aria-label={title} className="divide-y divide-line">
          {items.map((r) => (
            <ReviewRow key={r.id} review={r} kind={kind} />
          ))}
        </ul>
      )}
    </section>
  )
}
