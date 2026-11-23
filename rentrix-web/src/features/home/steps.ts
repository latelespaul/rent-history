import { today } from "@/features/tenancy/labels"
import { reviewWindow } from "@/features/tenancy/reviewWindow"
import type { Tenancy } from "@/types/api"

/** The optional next steps after adding a home (captain 2026-11-23 14:53): review, agreement. */

/** True once the tenancy is backed by a checked rental agreement (D-91). */
export function agreementDone(tenancy: Pick<Tenancy, "evidenceLevel">): boolean {
  return tenancy.evidenceLevel === "DOCUMENT"
}

/** Whether the "Rate and review" step is still open: not written yet, and its window not closed. */
export function reviewStepOpen(tenancy: Tenancy): boolean {
  if (tenancy.reviewed) return false
  const window = reviewWindow(tenancy, today())
  return window.state === "open" || window.state === "not_yet"
}

/**
 * How many optional steps are left for the tenant's own record (captain 2026-11-23 14:53): rate
 * and review it, and upload the rental agreement. Zero for anyone else's tenancy.
 */
export function stepsLeft(tenancy: Tenancy): number {
  if (tenancy.myRole !== "TENANT") return 0
  if (tenancy.status !== "ACTIVE" && tenancy.status !== "ENDED") return 0
  return (reviewStepOpen(tenancy) ? 1 : 0) + (agreementDone(tenancy) ? 0 : 1)
}

/** "2 optional steps left" */
export function stepsLeftLabel(n: number): string {
  return `${n} optional ${n === 1 ? "step" : "steps"} left`
}
