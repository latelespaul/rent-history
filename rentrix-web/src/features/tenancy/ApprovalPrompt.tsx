import { Link } from "react-router-dom"
import { isApiError } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { Notice } from "@/components/ui/notice"
import { errorMessage } from "@/components/ui/states"
import { monthLabel } from "@/features/property/labels"
import { isGateError, usePhoneStepHref } from "./gate"

/**
 * A friendly prompt for a gated write (DESIGN.md screen 20 pattern): what blocks the action and
 * where to go next. Any other error shows as a plain danger notice.
 */
export function ActionError({
  error,
  startDate,
}: {
  error: unknown
  /** The start date ("2024-03-01" or "2024-03") the user asked for, to word an overlap right. */
  startDate?: string
}) {
  const phoneStep = usePhoneStepHref()
  if (!error) return null
  if (isGateError(error)) {
    return (
      <Notice tone="warn" icon="shield-check">
        <p className="font-semibold">Verify to post</p>
        <p className="mt-1">
          Any one is enough: phone, Aadhaar or LinkedIn. A phone code takes a minute. What you typed
          here is kept, and you come straight back. Viewing stays open to everyone.
        </p>
        <Button asChild size="sm" variant="outline" className="mt-2">
          <Link to={phoneStep}>Verify now</Link>
        </Button>
      </Notice>
    )
  }
  if (isApiError(error) && error.code === "tenancy_overlap" && error.problem.conflict) {
    const conflict = error.problem.conflict
    const period = `${monthLabel(conflict.from)} – ${monthLabel(conflict.to) ?? "now"}`
    const asked = startDate?.slice(0, 7)
    return (
      <Notice tone="warn" icon="tenancy">
        <p className="font-semibold">This home already has a tenancy for these dates</p>
        <p className="mt-1">
          A confirmed tenancy runs {period}. A whole home has one tenancy at a time, so the new one{" "}
          {overlapFix(asked, conflict)}
        </p>
        {conflict.tenancyId && conflict.status === "ACTIVE" && (
          <Button asChild size="sm" variant="outline" className="mt-2">
            <Link to={`/tenancies/${conflict.tenancyId}`}>End the current tenancy first</Link>
          </Button>
        )}
      </Notice>
    )
  }
  return <Notice tone="danger">{errorMessage(error)}</Notice>
}

/**
 * How the new range must move to clear the clash: end before it starts when the user asked for an
 * earlier start, start after it ends otherwise. Without the asked date, both ways are named.
 */
function overlapFix(asked: string | undefined, conflict: { from: string; to?: string }): string {
  const before = `must end before ${monthLabel(conflict.from) ?? conflict.from}`
  const after = conflict.to
    ? `must start after ${monthLabel(conflict.to) ?? conflict.to}`
    : "can start only once it ends"
  if (!asked)
    return `${before} or ${conflict.to ? after.replace("must ", "") : "start once it ends"}.`
  return `${asked < conflict.from ? before : after}.`
}
