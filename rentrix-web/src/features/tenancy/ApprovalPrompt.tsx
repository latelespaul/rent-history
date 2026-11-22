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
export function ActionError({ error }: { error: unknown }) {
  const phoneStep = usePhoneStepHref()
  if (!error) return null
  if (isGateError(error)) {
    return (
      <Notice tone="warn" icon="shield-check">
        <p className="font-semibold">Verify your phone to post</p>
        <p className="mt-1">
          It takes a minute: we send a 6-digit code by SMS. What you typed here is kept, and you
          come straight back. Viewing stays open to everyone.
        </p>
        <Button asChild size="sm" variant="outline" className="mt-2">
          <Link to={phoneStep}>Verify your phone number</Link>
        </Button>
      </Notice>
    )
  }
  if (isApiError(error) && error.code === "tenancy_overlap" && error.problem.conflict) {
    const conflict = error.problem.conflict
    const period = `${monthLabel(conflict.from)} – ${monthLabel(conflict.to) ?? "now"}`
    return (
      <Notice tone="warn" icon="tenancy">
        <p className="font-semibold">This home already has a tenancy for these dates</p>
        <p className="mt-1">
          A confirmed tenancy runs {period}. A whole home has one tenancy at a time, so the new one
          must start after it ends.
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
