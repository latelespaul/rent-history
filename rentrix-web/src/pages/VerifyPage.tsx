import { Link, Navigate, Route, Routes } from "react-router-dom"
import { Container } from "@/components/layout/Container"
import { Badge, VerifiedBadge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { AlertIcon, ClockIcon } from "@/components/ui/icons.gen"
import { Notice } from "@/components/ui/notice"
import { ErrorState, EmptyState } from "@/components/ui/states"
import { BRAND } from "@/lib/brand"
import { canPost, nextStep } from "@/features/verification/progress"
import { roleWords, useNext, useWithNext } from "@/features/verification/next"
import {
  IdStep,
  PhoneStep,
  ProfileStep,
  RentalStep,
  SubmitStep,
} from "@/features/verification/steps"
import { useIdentity, useVerification } from "@/features/verification/useVerification"
import type { VerificationEntry } from "@/types/api"

const ROLE_TITLE = { TENANT: "Tenant", LANDLORD: "Landlord" } as const

/** `VerificationDecisionCode` (§5.13) in words; the admin's note follows. */
const DECISION_TEXT: Record<string, string> = {
  NO_VERIFIED_ID: "No verified ID yet",
  ID_UNREADABLE: "The ID could not be read",
  ID_NAME_MISMATCH: "The name on the ID does not match your profile",
  AADHAAR_NOT_MASKED: "The Aadhaar copy was not masked",
  DOCUMENT_MISSING: "A document is missing",
  DUPLICATE_ACCOUNT: "This looks like a second account",
  OTHER: "Something needs changing",
}

/** One role's status card (D-120): waiting, changes needed or verified (mockups `verify-*.html`). */
function RoleStatus({ entry }: { entry: VerificationEntry }) {
  const withNext = useWithNext()
  const first = nextStep(entry)
  return (
    <div className="rounded-lg border border-line p-4">
      <p className="flex flex-wrap items-center gap-2">
        <b className="text-ink-900">{ROLE_TITLE[entry.role]}</b>
        {entry.status === "APPROVED" && <VerifiedBadge>Verified by {BRAND.name}</VerifiedBadge>}
        {entry.status === "PENDING" && <Badge variant="warn">Waiting for review</Badge>}
        {entry.status === "REJECTED" && <Badge variant="danger">Changes needed</Badge>}
        {entry.status === "DRAFT" && <Badge>Not requested yet</Badge>}
      </p>
      {entry.status === "PENDING" && (
        <p className="mt-2 flex gap-2 text-sm text-ink-700">
          <ClockIcon size={18} className="shrink-0 text-ink-500" />
          An admin checks it, usually within 48 hours. We email you the result. You do not have to
          wait: you can post now.
        </p>
      )}
      {entry.status === "REJECTED" && (
        <div className="mt-2 space-y-2 text-sm text-ink-700">
          <p className="flex gap-2">
            <AlertIcon size={18} className="shrink-0 text-danger-600" />
            <span>
              <b>{DECISION_TEXT[entry.decision?.code ?? "OTHER"] ?? DECISION_TEXT.OTHER}.</b>{" "}
              {entry.decision?.note}
            </span>
          </p>
          <p className="text-ink-500">What you posted stays visible, without the tick.</p>
          <Button asChild size="sm">
            <Link to={withNext(first?.path ?? "/verify/submit")}>Fix and resubmit</Link>
          </Button>
        </div>
      )}
      {entry.status === "APPROVED" && (
        <p className="mt-2 text-sm text-ink-700">
          Your {ROLE_TITLE[entry.role].toLowerCase()} profile shows the green tick.
        </p>
      )}
      {entry.status === "DRAFT" && (
        <Button asChild size="sm" variant="outline" className="mt-2">
          <Link to={withNext(first?.path ?? "/verify/submit")}>Continue verification</Link>
        </Button>
      )}
    </div>
  )
}

/**
 * `/verify` (D-120): the first open step while every role is still a draft, else the status of
 * each held role ("Waiting for review", "Changes needed" with "Fix and resubmit", the green tick).
 */
function VerifyHome({ entries }: { entries: VerificationEntry[] }) {
  const next = useNext()
  const withNext = useWithNext()
  if (entries.every((entry) => entry.status === "DRAFT")) {
    const first = nextStep(entries[0])
    return <Navigate to={withNext(first?.path ?? "/verify/submit")} replace />
  }
  const waiting = entries.some((entry) => entry.status === "PENDING")
  return (
    <section className="rounded-xl border border-line bg-surface p-6 shadow-sm nav:p-8">
      <p className="text-xs font-semibold tracking-wide text-teal-700 uppercase">
        Verify your account · {roleWords(entries)}
      </p>
      <h1 className="mt-1 font-display text-2xl font-semibold text-ink-900">
        {waiting ? "Thanks, your verification is requested" : "Your verification"}
      </h1>
      <div className="mt-4 space-y-3">
        {entries.map((entry) => (
          <RoleStatus key={entry.role} entry={entry} />
        ))}
      </div>
      {canPost(entries) && (
        <Notice tone="teal" className="mt-4">
          <b>Now:</b> record or confirm a tenancy, add rent, write reviews. They show as &ldquo;Not
          verified yet&rdquo; until the review. <b>After review:</b> your profile gets the green
          tick &ldquo;Verified by {BRAND.name}&rdquo;.
        </Notice>
      )}
      {next && (
        <Button asChild className="mt-4">
          <Link to={next}>Back to where you were</Link>
        </Button>
      )}
    </section>
  )
}

/**
 * The verification stepper (D-120, PLAN W8): `/verify`, `/verify/profile`, `/verify/phone`,
 * `/verify/id`, `/verify/rental`, `/verify/submit`. Every step saves through its own endpoint, so
 * leaving and coming back resumes at the first open step. `?next=` rides along and brings the user
 * back to the page they came from (D-119).
 */
export default function VerifyPage() {
  const verification = useVerification()
  const identity = useIdentity()
  const entries = verification.data

  let body
  if (verification.isPending)
    body = (
      <p role="status" className="py-16 text-center text-sm text-ink-500">
        Loading your verification…
      </p>
    )
  else if (verification.isError)
    body = <ErrorState error={verification.error} onRetry={() => void verification.refetch()} />
  else if (!entries || entries.length === 0)
    body = (
      <EmptyState
        icon="shield-check"
        title="Nothing to verify"
        description="Verification is for tenants and landlords. Add a role in settings first."
        action={
          <Button asChild>
            <Link to="/settings">Settings</Link>
          </Button>
        }
      />
    )
  else
    body = (
      <Routes>
        <Route index element={<VerifyHome entries={entries} />} />
        <Route path="profile" element={<ProfileStep entries={entries} />} />
        <Route path="phone" element={<PhoneStep entries={entries} identity={identity.data} />} />
        <Route path="id" element={<IdStep entries={entries} identity={identity.data} />} />
        <Route path="rental" element={<RentalStep entries={entries} />} />
        <Route path="submit" element={<SubmitStep entries={entries} identity={identity.data} />} />
        <Route path="*" element={<Navigate to="/verify" replace />} />
      </Routes>
    )

  return (
    <main>
      <Container className="max-w-[760px] py-10 nav:py-14">{body}</Container>
    </main>
  )
}
