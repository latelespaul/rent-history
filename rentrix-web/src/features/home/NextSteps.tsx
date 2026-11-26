import { Link } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { StarIcon } from "@/components/ui/icons.gen"
import { dateLabel, today } from "@/features/tenancy/labels"
import { reviewWindow } from "@/features/tenancy/reviewWindow"
import type { Tenancy } from "@/types/api"
import { AgreementStepCard } from "./AgreementStepCard"
import { reviewStepOpen, stepsLeft } from "./steps"

function ReviewStepCard({ tenancy, accent }: { tenancy: Tenancy; accent: boolean }) {
  if (!reviewStepOpen(tenancy)) return null
  const window = reviewWindow(tenancy, today())
  return (
    <div
      data-testid="next-step-review"
      className="flex items-start gap-3 rounded-lg border border-line bg-surface p-4 text-left"
    >
      <StarIcon size={20} className="mt-0.5 shrink-0 text-teal-700" />
      <div className="min-w-0 flex-1 space-y-1">
        <p className="font-semibold text-ink-900">Rate and review this home and landlord</p>
        {window.state === "open" ? (
          <>
            <p className="text-sm text-ink-700">
              Your review shows as &ldquo;Not verified yet&rdquo; until the tenancy is verified.
              Optional.
            </p>
            <Button
              asChild
              size="sm"
              variant={accent ? "accent" : "default"}
              className="mt-1"
              data-testid="add-home-write-review"
            >
              <Link to={`/tenancies/${tenancy.id}/review`}>Write a review</Link>
            </Button>
          </>
        ) : (
          window.state === "not_yet" && (
            <p className="text-sm text-ink-700">
              You can review it from {dateLabel(window.opens)}: reviews open 60 days after you move
              in, or when you move out.
            </p>
          )
        )}
      </div>
    </div>
  )
}

/**
 * The optional next steps for a home the tenant added: review it, and upload the rental
 * agreement. Each is skippable; both stay visible on My tenancies and the home's page until done.
 * `accent` gives "Write a review" the accent, for a screen where it is the one primary action
 * (DESIGN.md §4.1).
 */
export function NextSteps({ tenancy, accent = false }: { tenancy: Tenancy; accent?: boolean }) {
  if (stepsLeft(tenancy) === 0) return null
  return (
    <div className="space-y-2" data-testid="next-steps">
      <ReviewStepCard tenancy={tenancy} accent={accent} />
      <AgreementStepCard tenancy={tenancy} />
    </div>
  )
}
