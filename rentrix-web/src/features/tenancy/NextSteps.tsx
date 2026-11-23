import { Link } from "react-router-dom"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  CheckIcon,
  ClockIcon,
  DocumentIcon,
  ShieldCheckIcon,
  StarIcon,
} from "@/components/ui/icons.gen"
import { BRAND } from "@/lib/brand"
import { cn } from "@/lib/utils"
import type { Tenancy } from "@/types/api"
import { dateLabel, today } from "./labels"
import {
  agreementHref,
  agreementStep,
  rejectionText,
  reviewHref,
  reviewStep,
  stepsLeft,
  stepsLeftLabel,
  type AgreementStep,
  type ReviewStep,
} from "./stepState"
import { useReviewTasks } from "./useTenancies"


function AgreementRow({
  tenancy,
  step,
  then,
}: {
  tenancy: Tenancy
  step: AgreementStep
  then?: "review"
}) {
  const body = (() => {
    switch (step.state) {
      case "verified":
        return (
          <Badge variant="verified" data-testid="agreement-status">
            <ShieldCheckIcon />
            Verified by {BRAND.name}
          </Badge>
        )
      case "awaiting":
        return (
          <Badge variant="info" data-testid="agreement-status">
            <ClockIcon />
            {step.byMe
              ? "Agreement uploaded · awaiting team check"
              : `The ${tenancy.myRole === "TENANT" ? "landlord" : "tenant"} uploaded the agreement · awaiting team check`}
          </Badge>
        )
      case "rejected":
        return (
          <div className="space-y-2">
            <p className="text-sm text-danger-600" data-testid="agreement-status">
              {rejectionText(step.reason)}
            </p>
            <Button asChild size="sm" variant="outline" data-testid="next-step-agreement">
              <Link to={agreementHref(tenancy.id, then)}>Upload the agreement again</Link>
            </Button>
          </div>
        )
      case "draft":
        return (
          <Button asChild size="sm" data-testid="next-step-agreement">
            <Link to={agreementHref(tenancy.id, then)}>Finish uploading your agreement</Link>
          </Button>
        )
      case "todo":
        return (
          <Button asChild size="sm" data-testid="next-step-agreement">
            <Link to={agreementHref(tenancy.id, then)}>
              <DocumentIcon />
              Upload your rental agreement
            </Link>
          </Button>
        )
    }
  })()
  return (
    <li className="space-y-1.5">
      <p className="text-sm font-semibold text-ink-900">Rental agreement</p>
      <p className="text-xs text-ink-500">
        Gets the green tick &ldquo;Verified by {BRAND.name}&rdquo; once the team checks it. Only the
        two of you and the team see the file.
      </p>
      {body}
    </li>
  )
}

function ReviewRow({ tenancy, step }: { tenancy: Tenancy; step: ReviewStep }) {
  if (step.state === "none") return null
  return (
    <li className="space-y-1.5">
      <p className="text-sm font-semibold text-ink-900">Your review</p>
      {step.state === "todo" && (
        <Button asChild size="sm" data-testid="next-step-review">
          <Link to={reviewHref(tenancy.id)}>
            <StarIcon />
            Rate and review this home
          </Link>
        </Button>
      )}
      {step.state === "done" && (
        <Badge variant="success" data-testid="review-status">
          <CheckIcon />
          You reviewed this home
        </Badge>
      )}
      {step.state === "later" && (
        <p className="text-sm text-ink-700" data-testid="review-status">
          You can rate and review this home from {dateLabel(step.opens)}: reviews open 60 days after
          you move in, or when you move out.
        </p>
      )}
    </li>
  )
}

/**
 * The optional next steps of one tenancy (captain 2026-11-23 14:53): the rental agreement and the
 * review, each with its state or its action, and how many are left. Shown after "Save my home",
 * on My tenancies, on the tenancy and on the home's page until both are done.
 */
export function NextSteps({
  tenancy,
  title,
  className,
  then,
}: {
  tenancy: Tenancy
  /** Replaces the default heading, e.g. "Home added". */
  title?: string
  className?: string
  /** After the agreement upload, go on to the review. */
  then?: "review"
}) {
  const open = tenancy.status === "ACTIVE" || tenancy.status === "ENDED"
  const tasks = useReviewTasks(open && tenancy.myRole === "TENANT")
  if (!open || tenancy.myRole === null) return null
  const agreement = agreementStep(tenancy)
  const review = reviewStep(tenancy, tasks.data, today())
  const left = stepsLeft(agreement, review)
  return (
    <section
      aria-label="Next steps"
      data-testid="next-steps"
      className={cn("space-y-3 rounded-lg border border-line bg-sunk/40 p-4", className)}
    >
      <p className="text-sm font-semibold text-ink-900" data-testid="steps-left">
        {title ? `${title}. ` : ""}
        {stepsLeftLabel(left)}
      </p>
      <ul className="space-y-4">
        <AgreementRow tenancy={tenancy} step={agreement} then={then} />
        <ReviewRow tenancy={tenancy} step={review} />
      </ul>
    </section>
  )
}
