import { useState, type FormEvent } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Link, useParams } from "react-router-dom"
import { writeReview } from "@/api/homes"
import { isApiError } from "@/api/errors"
import { myReviewKeys } from "@/api/reviews"
import { tenancyKeys } from "@/api/tenancies"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { CheckIcon, StarIcon } from "@/components/ui/icons.gen"
import { Label } from "@/components/ui/label"
import { ErrorState } from "@/components/ui/states"
import { Textarea } from "@/components/ui/textarea"
import { ReviewTick } from "@/features/property/ReviewTick"
import { ActionError } from "@/features/tenancy/ApprovalPrompt"
import { isGateError } from "@/features/tenancy/gate"
import { tenancyHome } from "@/features/tenancy/labels"
import { useTenancy } from "@/features/tenancy/useTenancies"
import { BRAND } from "@/lib/brand"
import { cn } from "@/lib/utils"
import type { PropertyReview } from "@/types/api"

/** The server's D-43 length rule for a review body. */
export const REVIEW_MIN = 30
export const REVIEW_MAX = 2000

const STAR_WORDS = ["", "Poor", "Below average", "OK", "Good", "Excellent"]

function Stars({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  return (
    <div role="radiogroup" aria-label="Your rating" className="flex gap-1">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          role="radio"
          aria-checked={value === n}
          aria-label={`${n} star${n > 1 ? "s" : ""}`}
          data-testid={`review-star-${n}`}
          onClick={() => onChange(n)}
          className={cn(
            "grid size-11 place-items-center rounded-md border",
            n <= value ? "border-warn-600 bg-warn-100 text-warn-600" : "border-line text-ink-300",
          )}
        >
          <StarIcon size={24} />
        </button>
      ))}
    </div>
  )
}

/** "You submitted this review" (captain 21:38): what was published, with its tick state. */
function Submitted({ review, propertyId }: { review: PropertyReview; propertyId: string }) {
  return (
    <section
      data-testid="review-submitted"
      className="rounded-xl border border-line bg-surface p-6 shadow-sm"
    >
      <div className="text-center">
        <span className="mx-auto grid size-14 place-items-center rounded-full bg-success-100 text-success-600">
          <CheckIcon size={30} />
        </span>
        <h1 className="mt-3 font-display text-2xl font-semibold text-ink-900">
          You submitted this review
        </h1>
        <p className="mt-1 text-sm text-ink-500">
          It is on the home&rsquo;s page now. The {BRAND.name} team verifies it later.
        </p>
      </div>
      <figure className="mt-5 rounded-lg border border-line p-4">
        <p className="flex flex-wrap items-center gap-2">
          <span className="font-semibold text-ink-900">{review.ratingOverall} / 5</span>
          <ReviewTick state={review.verification} />
        </p>
        <blockquote className="mt-2 text-sm whitespace-pre-line text-ink-700">
          {review.body}
        </blockquote>
      </figure>
      <Button asChild size="lg" className="mt-5 w-full" data-testid="review-view-home">
        <Link to={`/p/${propertyId}#reviews`}>See it on the home&rsquo;s page</Link>
      </Button>
    </section>
  )
}

/**
 * `/tenancies/:id/review`: the tenant reviews the home of one of their tenancies (D-27, D-43). It is
 * published at once without the green tick (D-132, D-134). The landlord review is left out while
 * the landlord is not on the platform (D-139); the society review comes later.
 */
export default function WriteReviewPage() {
  const { id = "" } = useParams()
  const tenancy = useTenancy(id)
  const queryClient = useQueryClient()
  const [rating, setRating] = useState(0)
  const [body, setBody] = useState("")
  const [missing, setMissing] = useState<string | null>(null)

  const write = useMutation({
    mutationFn: () =>
      writeReview(id, {
        direction: "TENANT_TO_PROPERTY",
        ratingOverall: rating,
        body: body.trim(),
      }),
    onSuccess: async () => {
      // Tenancies too: their "Rate and review" next step is now done.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["properties"] }),
        queryClient.invalidateQueries({ queryKey: tenancyKeys.all }),
        queryClient.invalidateQueries({ queryKey: myReviewKeys.all }),
      ])
      window.scrollTo?.({ top: 0 })
    },
  })

  if (tenancy.isError) {
    return (
      <main>
        <Container className="py-12">
          <ErrorState error={tenancy.error} onRetry={() => void tenancy.refetch()} />
        </Container>
      </main>
    )
  }
  const propertyId = tenancy.data?.property.id ?? ""

  if (write.data) {
    return (
      <main>
        <Container className="max-w-[560px] py-8">
          <Submitted review={write.data} propertyId={propertyId} />
        </Container>
      </main>
    )
  }

  const length = body.trim().length
  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (rating === 0) return setMissing("Pick a rating from 1 to 5 stars.")
    if (length < REVIEW_MIN) return setMissing(`Write at least ${REVIEW_MIN} characters.`)
    setMissing(null)
    write.mutate()
  }
  const bodyError = isApiError(write.error) ? write.error.fieldError("body") : undefined
  const ruleError = isApiError(write.error) && !bodyError ? write.error : null

  return (
    <main>
      <Container className="max-w-[560px] space-y-5 py-6 nav:py-10">
        <div>
          <p className="text-xs font-semibold tracking-wide text-teal-700 uppercase">
            Write a review
          </p>
          <h1 className="mt-1 font-display text-2xl font-semibold text-ink-900">
            {tenancy.data ? tenancyHome(tenancy.data) : "Your home"}
          </h1>
          <p className="mt-1 text-sm text-ink-500">
            About the home: repairs, water, power, noise, the society. No names, numbers or flat
            numbers.
          </p>
        </div>
        <form
          noValidate
          onSubmit={onSubmit}
          aria-label="Review"
          className="space-y-4 rounded-xl border border-line bg-surface p-5 shadow-sm"
        >
          <div className="space-y-1.5">
            <p className="text-sm font-medium text-ink-900">Your rating</p>
            <Stars value={rating} onChange={setRating} />
            <p className="text-xs text-ink-500">{STAR_WORDS[rating] || "Tap a star."}</p>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="review-body">Your review</Label>
            <Textarea
              id="review-body"
              rows={6}
              maxLength={REVIEW_MAX}
              value={body}
              onChange={(e) => setBody(e.target.value)}
            />
            <p className={cn("text-xs", bodyError ? "text-danger-600" : "text-ink-500")}>
              {bodyError ?? `${length} / ${REVIEW_MAX} · at least ${REVIEW_MIN} characters`}
            </p>
          </div>
          {missing && (
            <p className="text-sm text-danger-600" data-testid="review-missing">
              {missing}
            </p>
          )}
          {ruleError && !isGateError(ruleError) ? (
            <p className="text-sm text-danger-600" data-testid="review-refused">
              {ruleError.problem.detail}
            </p>
          ) : (
            <ActionError error={write.error} />
          )}
          <Button
            type="submit"
            size="lg"
            className="w-full"
            disabled={write.isPending || !tenancy.data}
            data-testid="review-submit"
          >
            {write.isPending ? "Publishing…" : "Publish review"}
          </Button>
        </form>
      </Container>
    </main>
  )
}
