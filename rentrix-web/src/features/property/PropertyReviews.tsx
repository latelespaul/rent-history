import { useEffect, useRef, useState } from "react"
import { useLocation } from "react-router-dom"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { deleteReview, respondToReview, updateReview } from "@/api/properties"
import { myReviewKeys } from "@/api/reviews"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { PencilIcon, StarIcon, TrashIcon } from "@/components/ui/icons.gen"
import { Label } from "@/components/ui/label"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { Textarea } from "@/components/ui/textarea"
import {
  needsWarning,
  VERIFIED_DELETE_WARNING,
  VERIFIED_EDIT_WARNING,
} from "@/features/edit/verified"
import { criterionLabel } from "@/features/review/form"
import { ActionError } from "@/features/tenancy/ApprovalPrompt"
import type { PropertyReview, RatingSummary } from "@/types/api"
import { listedReviewCount, monthLabel } from "./labels"
import { Stars } from "./Rating"
import { ReviewTick } from "./ReviewTick"
import { TrustBadges } from "./TrustBadges"
import { usePropertyReviews } from "./useProperties"
import { cn } from "@/lib/utils"

/** Longest reply the API accepts (ReviewContentRules.RESPONSE_MAX). */
const RESPONSE_MAX = 1000

/** The landlord's one public reply form, shown only when the API offers `actions.respond`. */
function RespondForm({ reviewId, propertyId }: { reviewId: string; propertyId: string }) {
  const [open, setOpen] = useState(false)
  const [text, setText] = useState("")
  const queryClient = useQueryClient()
  const send = useMutation({
    mutationFn: () => respondToReview(reviewId, text.trim()),
    onSuccess: () => {
      setOpen(false)
      setText("")
      void queryClient.invalidateQueries({ queryKey: ["properties", "reviews", propertyId] })
    },
  })
  if (!open) {
    return (
      <Button size="sm" variant="outline" onClick={() => setOpen(true)}>
        Respond
      </Button>
    )
  }
  const fieldId = `respond-${reviewId}`
  return (
    <form
      className="space-y-2 rounded-lg border border-line p-3"
      onSubmit={(event) => {
        event.preventDefault()
        send.mutate()
      }}
    >
      <Label htmlFor={fieldId}>
        Your public reply{" "}
        <span className="font-normal text-ink-500">(once, shown to everyone)</span>
      </Label>
      <Textarea
        id={fieldId}
        value={text}
        maxLength={RESPONSE_MAX}
        onChange={(e) => setText(e.target.value)}
      />
      {send.isError && <ActionError error={send.error} />}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" size="sm" disabled={send.isPending || !text.trim()}>
          {send.isPending ? "Sending…" : "Post reply"}
        </Button>
        <Button type="button" size="sm" variant="ghost" onClick={() => setOpen(false)}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

/**
 * The author's own buttons (D-156): edit and delete at any time. A verified review warns first
 * that its tick goes.
 */
function MyReviewActions({ review }: { review: PropertyReview }) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [rating, setRating] = useState(review.ratingOverall ?? 5)
  const [text, setText] = useState(review.body ?? "")
  const [confirm, setConfirm] = useState<null | "save" | "delete">(null)
  const verified = needsWarning(review.verification)
  const done = () => {
    setConfirm(null)
    setEditing(false)
    void queryClient.invalidateQueries({ queryKey: ["properties"] })
    void queryClient.invalidateQueries({ queryKey: ["tenancies"] })
    void queryClient.invalidateQueries({ queryKey: myReviewKeys.all })
    void queryClient.invalidateQueries({ queryKey: ["users"] })
  }
  const save = useMutation({
    mutationFn: () =>
      updateReview(review.id, {
        ratingOverall: rating,
        ratingA: review.ratingA,
        ratingB: review.ratingB,
        ratingC: review.ratingC,
        body: text.trim(),
      }),
    onSuccess: done,
    onError: () => setConfirm(null),
  })
  const remove = useMutation({
    mutationFn: () => deleteReview(review.id),
    onSuccess: done,
    onError: () => setConfirm(null),
  })
  if (!review.actions?.edit && !review.actions?.withdraw) return null
  const fieldId = `review-edit-${review.id}`
  return (
    <div className="space-y-2">
      {editing ? (
        <form
          className="space-y-2 rounded-lg border border-line p-3"
          onSubmit={(event) => {
            event.preventDefault()
            if (verified) setConfirm("save")
            else save.mutate()
          }}
        >
          <Label htmlFor={`${fieldId}-rating`}>Your rating</Label>
          <select
            id={`${fieldId}-rating`}
            value={rating}
            onChange={(e) => setRating(Number(e.target.value))}
            className="h-10 rounded-md border border-line-strong bg-surface px-3 text-sm"
          >
            {[5, 4, 3, 2, 1].map((n) => (
              <option key={n} value={n}>
                {n} ★
              </option>
            ))}
          </select>
          <Label htmlFor={fieldId}>Your review</Label>
          <Textarea
            id={fieldId}
            value={text}
            maxLength={2000}
            onChange={(e) => setText(e.target.value)}
          />
          {save.isError && <ActionError error={save.error} />}
          <div className="flex gap-2">
            <Button
              type="submit"
              size="sm"
              disabled={save.isPending || (!text.trim() && !review.formReview)}
            >
              Save
            </Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setEditing(false)}>
              Cancel
            </Button>
          </div>
        </form>
      ) : (
        <div className="flex flex-wrap gap-2">
          {review.actions?.edit && (
            <Button size="sm" variant="outline" onClick={() => setEditing(true)}>
              <PencilIcon />
              Edit my review
            </Button>
          )}
          {review.actions?.withdraw && (
            <Button
              size="sm"
              variant="destructive"
              data-testid="review-delete"
              disabled={remove.isPending}
              onClick={() => setConfirm("delete")}
            >
              <TrashIcon />
              Delete my review
            </Button>
          )}
        </div>
      )}
      {remove.isError && <ActionError error={remove.error} />}
      <ConfirmDialog
        open={confirm === "save"}
        onOpenChange={(open) => !open && setConfirm(null)}
        title="Save and remove the tick?"
        confirmLabel="Save"
        busy={save.isPending}
        onConfirm={() => save.mutate()}
      >
        <p>{VERIFIED_EDIT_WARNING}</p>
      </ConfirmDialog>
      <ConfirmDialog
        open={confirm === "delete"}
        onOpenChange={(open) => !open && setConfirm(null)}
        title="Delete your review?"
        confirmLabel="Delete review"
        danger
        busy={remove.isPending}
        onConfirm={() => remove.mutate()}
      >
        {verified && <p>{VERIFIED_DELETE_WARNING}</p>}
        <p>It leaves the home&apos;s page and its rating. This cannot be undone.</p>
      </ConfirmDialog>
    </div>
  )
}

/** A form review's parameter ratings, folded away so the list stays short on a phone (D-157). */
function CriteriaList({ review }: { review: PropertyReview }) {
  const entries = Object.entries(review.criteria ?? {}) as [string, number][]
  if (entries.length === 0) return null
  return (
    <details className="text-sm" data-testid="review-criteria">
      <summary className="cursor-pointer font-medium text-teal-700">
        Ratings by topic ({entries.length})
      </summary>
      <ul className="mt-2 space-y-1">
        {entries.map(([key, value]) => (
          <li key={key} className="flex items-center justify-between gap-3">
            <span className="text-ink-700">{criterionLabel(key as never)}</span>
            <span className="inline-flex items-center gap-1" aria-label={`${value} out of 5`}>
              <Stars value={value} />
            </span>
          </li>
        ))}
      </ul>
    </details>
  )
}

/** Who replied, by label only (D-168): "the owner", or "Verified tenant" / "Tenant". */
function replier(label: string | undefined): string {
  return !label || label === "Owner" ? "the owner" : label
}

/** One review in a list: author line, stars, the form's answers and text, the reply. */
export function ReviewItem({ review, propertyId }: { review: PropertyReview; propertyId: string }) {
  const rating = review.ratingOverall
  // Only the author gets edit or withdraw (D-156): that marks their own review.
  const mine = Boolean(review.actions?.edit || review.actions?.withdraw)
  return (
    <li
      className="space-y-1.5 py-4 first:pt-0"
      data-testid="property-review"
      data-mine={mine || undefined}
    >
      {/* Flex items wrap on a phone, so they are spaced by the gap, never by a "·" that would start a line. */}
      <p className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-sm">
        {mine && (
          <Badge variant="tenant" data-testid="my-review">
            Your review
          </Badge>
        )}
        {/*
          Never the name (D-168) and never a year or stay date (D-169): "Verified tenant", "Tenant" or
          "Owner", the same label everyone sees, so the landlord cannot tell which tenant wrote it.
        */}
        <span className="font-semibold text-ink-900" data-testid="review-author">
          {review.author?.label ?? "Tenant"}
        </span>
        {review.author?.longStay && <Badge data-testid="review-long-stay">Stayed 1+ yr</Badge>}
        <TrustBadges badges={review.author?.badges} />
        {review.verification && <ReviewTick state={review.verification} />}
      </p>
      {rating !== undefined && (
        <span role="img" className="inline-flex" aria-label={`Rated ${rating} out of 5`}>
          <Stars value={rating} />
        </span>
      )}
      {review.wouldRecommend !== undefined && (
        <p
          className={cn(
            "text-sm font-medium",
            review.wouldRecommend ? "text-success-600" : "text-ink-700",
          )}
        >
          {review.wouldRecommend ? "Would recommend" : "Would not recommend"}
        </p>
      )}
      {review.body ? (
        <p className="whitespace-pre-line text-ink-700">{review.body}</p>
      ) : review.textHidden ? (
        <p className="text-sm text-ink-500 italic">The text is hidden while a report is checked.</p>
      ) : null}
      {review.pros && (
        <p className="text-sm whitespace-pre-line text-ink-700">
          <span className="font-semibold text-ink-900">Pros: </span>
          {review.pros}
        </p>
      )}
      {review.cons && (
        <p className="text-sm whitespace-pre-line text-ink-700">
          <span className="font-semibold text-ink-900">Cons: </span>
          {review.cons}
        </p>
      )}
      <CriteriaList review={review} />
      {/* Month and year only, never the day (D-169). */}
      {(review.publishedMonth || review.edited) && (
        <p className="text-xs text-ink-500" data-testid="review-dates">
          {review.publishedMonth && `Published ${monthLabel(review.publishedMonth)}`}
          {review.publishedMonth && review.edited && " · "}
          {review.edited && "edited"}
        </p>
      )}
      {review.response && (
        <div className="mt-2 rounded-lg border-l-2 border-line bg-sunk/40 px-3 py-2">
          <p className="text-xs font-semibold text-ink-700">
            Reply from {replier(review.response.authorLabel)}
          </p>
          <p className="text-sm whitespace-pre-line text-ink-700">{review.response.body}</p>
        </div>
      )}
      {review.actions?.respond && !review.response && (
        <RespondForm reviewId={review.id} propertyId={propertyId} />
      )}
      <MyReviewActions review={review} />
    </li>
  )
}

/**
 * What tenants say about the home: full text and ratings for everyone, signed in or not (captain
 * 2026-11-24 00:31, D-161). The author sees "Your review" with Edit and Delete. Until the API
 * serves reviews (B6) the card says so.
 */
export function PropertyReviews({
  propertyId,
  rating,
}: {
  propertyId: string
  rating: RatingSummary
}) {
  const reviews = usePropertyReviews(propertyId)
  const { hash } = useLocation()
  const section = useRef<HTMLElement>(null)
  const loaded = reviews.isSuccess
  // "See my review" and "Read n reviews" link to #reviews; the list loads after the page, so scroll
  // to it once it is there.
  useEffect(() => {
    if (loaded && hash === "#reviews") section.current?.scrollIntoView?.({ block: "start" })
  }, [loaded, hash])

  let body
  if (reviews.isPending) {
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
        description="Reviews will show here once reviews are live."
      />
    )
  } else if (reviews.data.items.length === 0) {
    body = (
      <EmptyState
        icon="star"
        title="No reviews yet"
        description="Anyone signed in can review this home. Each review shows Verified or Not verified yet; only verified ones count in the rating."
      />
    )
  } else {
    body = (
      <>
        {reviews.data.items.some(
          (review) => review.verification && review.verification !== "VERIFIED",
        ) && (
          <p className="text-sm text-ink-500" data-testid="unverified-note">
            Reviews marked &ldquo;Not verified yet&rdquo; are listed here but count in the rating
            only once our team verifies them.
          </p>
        )}
        <ul className="divide-y divide-line">
          {reviews.data.items.map((review) => (
            <ReviewItem key={review.id} review={review} propertyId={propertyId} />
          ))}
        </ul>
      </>
    )
  }

  return (
    <section
      ref={section}
      id="reviews"
      aria-labelledby="reviews-heading"
      className="space-y-3 rounded-xl border border-line bg-surface p-5 shadow-sm"
    >
      <div className="flex items-center justify-between gap-2">
        <h2 id="reviews-heading" className="inline-flex items-center gap-2 text-lg font-semibold">
          <StarIcon size={20} />
          What tenants say about the home
        </h2>
        <span
          className="shrink-0 text-sm whitespace-nowrap text-ink-500"
          data-testid="reviews-count"
        >
          {listedReviewCount(reviews.data?.total, rating.reviewCount)}
        </span>
      </div>
      {body}
    </section>
  )
}
