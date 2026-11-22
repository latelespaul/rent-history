import { Link } from "react-router-dom"
import { isApiError } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { Notice } from "@/components/ui/notice"
import { errorMessage } from "@/components/ui/states"
import { monthLabel } from "@/features/property/labels"

/** The API's gate codes for writes that need an approved, ID-verified account (D-91, D-105). */
export const GATE_CODES = ["approval_required", "id_verification_required"] as const

export function isGateError(error: unknown): boolean {
  return isApiError(error) && (GATE_CODES as readonly string[]).includes(error.code)
}

const ROLE_WORD = { TENANT: "tenant", LANDLORD: "landlord", ADMIN: "admin" } as const

/**
 * A friendly prompt for a gated write (DESIGN.md screen 20 pattern): what blocks the action and
 * where to go next. Any other error shows as a plain danger notice.
 */
export function ActionError({ error }: { error: unknown }) {
  if (!error) return null
  if (isApiError(error) && error.code === "approval_required") {
    const approval = error.problem.approval
    const role = approval ? ROLE_WORD[approval.role] : "account"
    const status = approval?.status
    return (
      <Notice tone="warn" icon="shield-check">
        <p className="font-semibold">
          {status === "PENDING"
            ? `Your ${role} account is pending approval`
            : status === "REJECTED"
              ? `Your ${role} verification was rejected`
              : `Verify your ${role} account first`}
        </p>
        <p className="mt-1">
          Confirming tenancies, adding rent and writing reviews need an approved account, so ratings
          come only from real, checked people. Viewing stays open to everyone.
        </p>
        <Button asChild size="sm" variant="outline" className="mt-2">
          <Link to="/settings">See verification status</Link>
        </Button>
      </Notice>
    )
  }
  if (isApiError(error) && error.code === "id_verification_required") {
    return (
      <Notice tone="warn" icon="shield-check">
        <p className="font-semibold">Verify your ID first</p>
        <p className="mt-1">
          This action needs an ID-verified account. Your documents stay private: only you and admins
          can see them.
        </p>
        <Button asChild size="sm" variant="outline" className="mt-2">
          <Link to="/settings">See verification status</Link>
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
