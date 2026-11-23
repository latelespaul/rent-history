import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { deleteReview, respondToReview, updateReview } from "@/api/properties"
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
import { ActionError } from "@/features/tenancy/ApprovalPrompt"
import type { PropertyReview, RatingSummary } from "@/types/api"
import { dayLabel, monthLabel, reviewCount } from "./labels"
import { Stars } from "./Rating"
import { PersonTick } from "./PersonTick"
import { SignInPrompt } from "./SignInPrompt"
import { ReviewTick } from "./ReviewTick"
import { usePropertyReviews } from "./useProperties"

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
            <Button type="submit" size="sm" disabled={save.isPending || !text.trim()}>
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

function ReviewItem({ review, propertyId }: { review: PropertyReview; propertyId: string }) {
  const rating = review.ratingOverall
  return (
    <li className="space-y-1.5 py-4 first:pt-0" data-testid="property-review">
      <p className="flex flex-wrap items-center gap-x-1 gap-y-1 text-sm">
        <span className="font-semibold text-ink-900">{review.author?.shortName ?? "Tenant"}</span>
        {review.author && <PersonTick verified={review.author.verified} role="tenant" />}
        {review.author?.badges?.idVerified && <Badge>ID verified</Badge>}
        {review.author?.badges?.linkedinVerified && <Badge>LinkedIn verified</Badge>}
        {review.period && (
          <span className="text-ink-500">
            · stayed {monthLabel(review.period.from)} – {monthLabel(review.period.to) ?? "now"}
          </span>
        )}
        {review.verification && <ReviewTick state={review.verification} />}
      </p>
      {rating !== undefined && (
        <span role="img" className="inline-flex" aria-label={`Rated ${rating} out of 5`}>
          <Stars value={rating} />
        </span>
      )}
      {review.body ? (
        <p className="whitespace-pre-line text-ink-700">{review.body}</p>
      ) : review.textHidden ? (
        <p className="text-sm text-ink-500 italic">The text is hidden while a report is checked.</p>
      ) : null}
      {(review.publishedAt || review.editedAt) && (
        <p className="text-xs text-ink-500" data-testid="review-dates">
          {review.publishedAt && `Published ${monthLabel(review.publishedAt)}`}
          {review.publishedAt && review.editedAt && " · "}
          {review.editedAt && `edited ${dayLabel(review.editedAt)}`}
        </p>
      )}
      {review.response && (
        <div className="mt-2 rounded-lg border-l-2 border-line bg-sunk/40 px-3 py-2">
          <p className="text-xs font-semibold text-ink-700">
            Reply from {review.response.authorShortName ?? "the landlord"}
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
        description="Reviews will show here once reviews are live."
      />
    )
  } else if (reviews.data.length === 0) {
    body = (
      <EmptyState
        icon="star"
        title="No reviews yet"
        description="Anyone signed in who verified their phone or email can review this home. Each review shows Verified or Not verified yet."
      />
    )
  } else {
    body = (
      <ul className="divide-y divide-line">
        {reviews.data.map((review) => (
          <ReviewItem key={review.id} review={review} propertyId={propertyId} />
        ))}
      </ul>
    )
  }

  return (
    <section
      id="reviews"
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
