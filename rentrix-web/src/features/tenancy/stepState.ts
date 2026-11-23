import type { ReviewTask, Tenancy } from "@/types/api"
import { reviewWindow } from "./reviewWindow"

/**
 * The two optional steps after a home or tenancy is added (captain 2026-11-23 14:53): upload the
 * rental agreement (the team gives the green tick from it, D-147) and rate and review the home.
 * Both stay visible on My tenancies, the tenancy and the home's page until they are done.
 */
export type AgreementStep =
  | { state: "todo" }
  /** Uploaded but the uploader has not confirmed the fields yet. */
  | { state: "draft" }
  /** "Agreement uploaded · awaiting team check". */
  | { state: "awaiting"; byMe: boolean }
  | { state: "verified" }
  | { state: "rejected"; reason: string | null }

export type ReviewStep =
  { state: "todo" } | { state: "done" } | { state: "later"; opens: string } | { state: "none" }

export function agreementStep(tenancy: Tenancy): AgreementStep {
  if (tenancy.verificationStatus === "VERIFIED") return { state: "verified" }
  const agreement = tenancy.agreement
  if (tenancy.verificationStatus === "REJECTED" && (!agreement || agreement.status === "DECIDED"))
    return { state: "rejected", reason: tenancy.verificationReason }
  if (!agreement) return { state: "todo" }
  if (agreement.status === "DRAFT")
    return agreement.uploadedByMe ? { state: "draft" } : { state: "todo" }
  return { state: "awaiting", byMe: agreement.uploadedByMe }
}

/**
 * The tenant's review of the home (D-27): `todo` while the window is open and no review is
 * written (`GET /me/review-tasks` lists it), `done` once written, `later` until the D-39 window
 * opens on `today` (`YYYY-MM-DD`). `tasks` undefined = not loaded.
 */
export function reviewStep(
  tenancy: Tenancy,
  tasks: ReviewTask[] | undefined,
  today: string,
): ReviewStep {
  if (tenancy.myRole !== "TENANT") return { state: "none" }
  const window = reviewWindow(tenancy, today)
  if (window.state === "not_yet") return { state: "later", opens: window.opens }
  if (window.state !== "open" || tasks === undefined) return { state: "none" }
  const open = tasks.some((t) => t.tenancyId === tenancy.id && t.direction === "TENANT_TO_PROPERTY")
  return open ? { state: "todo" } : { state: "done" }
}

/**
 * How many of the two optional steps the caller can act on now ("2 optional steps left"): a review
 * that opens `later` is not counted.
 */
export function stepsLeft(agreement: AgreementStep, review: ReviewStep): number {
  const agreementLeft =
    agreement.state === "todo" || agreement.state === "draft" || agreement.state === "rejected"
  return Number(agreementLeft) + Number(review.state === "todo")
}

export function stepsLeftLabel(count: number): string {
  if (count === 0) return "All optional steps done"
  return `${count} optional step${count === 1 ? "" : "s"} left`
}

/** Where the two steps go: the agreement upload (then the review, with `then`) and the review. */
export function agreementHref(tenancyId: string, then?: "review") {
  return `/tenancies/${tenancyId}/agreement${then ? `?next=${then}` : ""}`
}

export function reviewHref(tenancyId: string) {
  return `/tenancies/${tenancyId}/review`
}

const REJECT_WORDS: Record<string, string> = {
  CANNOT_CONFIRM: "The team could not confirm the tenancy from the agreement",
  DETAILS_WRONG: "Some details do not match the agreement",
  DUPLICATE: "This tenancy is already on the site",
  OTHER: "The team could not verify it",
}

/**
 * The team's reason in plain words: the approvals queue stores `CODE` or `CODE: note` (D-134), e.g.
 * "CANNOT_CONFIRM: The pages are unreadable." → "The team could not confirm … : The pages are …".
 */
export function rejectionText(reason: string | null): string {
  if (!reason) return "The team could not verify it."
  const match = /^([A-Z_]+)(?::\s*(.*))?$/s.exec(reason.trim())
  if (!match || !REJECT_WORDS[match[1]]) return reason
  const words = REJECT_WORDS[match[1]]
  return match[2] ? `${words}: ${match[2]}` : `${words}.`
}
