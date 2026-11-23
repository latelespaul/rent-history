import type { StepStatus, VerificationEntry } from "@/types/api"

/** The stepper's steps, in order (D-120). Submit ("Request verification") follows the last one. */
export const STEPS = [
  { key: "profile", path: "/verify/profile", label: "Profile", optional: false },
  { key: "phone", path: "/verify/phone", label: "Verify (any one)", optional: false },
  { key: "id", path: "/verify/id", label: "Aadhaar, LinkedIn, PAN", optional: true },
  { key: "rental", path: "/verify/rental", label: "Rental details", optional: true },
] as const

export type StepKey = (typeof STEPS)[number]["key"]

const counts = (status: StepStatus, alsoPending: boolean) =>
  status === "DONE" || (alsoPending && status === "PENDING")

/**
 * Whether each step counts as done for one role (D-121, D-144): Profile and Rental details when
 * `DONE`; "Verify (any one)" when phone, masked Aadhaar or LinkedIn is verified (`method`); the
 * badges step when PAN, Aadhaar or LinkedIn is `DONE` or waiting for an admin (`PENDING`).
 */
export function doneSteps(entry: VerificationEntry): Record<StepKey, boolean> {
  const { steps } = entry
  return {
    profile: counts(steps.profile, false),
    phone: counts(steps.method, false),
    id: counts(steps.identity, true) || counts(steps.linkedin, true),
    rental: counts(steps.rentalAgreement, false),
  }
}

/** The first required step still open, else the first optional one not done, else null. */
export function nextStep(entry: VerificationEntry): (typeof STEPS)[number] | null {
  const done = doneSteps(entry)
  return (
    STEPS.find((step) => !step.optional && !done[step.key]) ??
    STEPS.find((step) => !done[step.key]) ??
    null
  )
}

/** Whether the user may post (D-144): any one of phone, masked Aadhaar or LinkedIn is verified. */
export function canPost(entries: VerificationEntry[]): boolean {
  return entries.some((entry) => entry.steps.method === "DONE")
}

export type ProgressKind = "phone" | "rejected" | "draft" | "pending"

/** The account-menu status row (D-121); null once every held role is approved and can post. */
export interface Progress {
  kind: ProgressKind
  label: string
  /** For `draft`: steps done out of 4. */
  done?: number
  role?: VerificationEntry["role"]
  /** Where the row links. */
  to: string
  /** A short hint under the label, e.g. "Next: ID (optional)". */
  hint?: string
}

const RANK: Record<VerificationEntry["status"], number> = {
  REJECTED: 0,
  DRAFT: 1,
  PENDING: 2,
  APPROVED: 3,
}

/**
 * The one row the account menu shows (D-121, D-133, D-144): no verified method first ("Verify to
 * post"), then the role that needs the user first: `REJECTED` ("Changes needed"), `DRAFT`
 * ("Get verified: n of 4 steps"), `PENDING` ("Waiting for review"). Null when nothing is open,
 * including for users who hold no tenant or landlord role.
 */
export function progressOf(entries: VerificationEntry[] | undefined): Progress | null {
  if (!entries || entries.length === 0) return null
  if (!canPost(entries)) {
    return {
      kind: "phone",
      label: "Verify to post",
      to: "/verify/phone",
      hint: "Any one: phone, Aadhaar or LinkedIn",
    }
  }
  const open = entries
    .filter((entry) => entry.status !== "APPROVED")
    .sort((a, b) => RANK[a.status] - RANK[b.status])
  const first = open[0]
  if (!first) return null
  if (first.status === "REJECTED") {
    return { kind: "rejected", label: "Changes needed", role: first.role, to: "/verify" }
  }
  if (first.status === "PENDING") {
    return { kind: "pending", label: "Waiting for review", role: first.role, to: "/verify" }
  }
  const done = Object.values(doneSteps(first)).filter(Boolean).length
  const next = nextStep(first)
  return {
    kind: "draft",
    label: `Get verified: ${done} of ${STEPS.length} steps`,
    done,
    role: first.role,
    to: next?.path ?? "/verify/submit",
    hint: next
      ? `Next: ${next.label}${next.optional ? " (optional)" : ""}`
      : "Next: request verification",
  }
}
