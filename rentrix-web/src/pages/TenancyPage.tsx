import { Link, useParams } from "react-router-dom"
import { isApiError } from "@/api/errors"
import { Container } from "@/components/layout/Container"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { LockIcon } from "@/components/ui/icons.gen"
import { Notice } from "@/components/ui/notice"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { rupees } from "@/features/property/labels"
import { RentRecords } from "@/features/tenancy/RentRecords"
import { TenancyActions } from "@/features/tenancy/TenancyActions"
import { TenancyFacts } from "@/features/tenancy/TenancyCard"
import {
  counterparty,
  dateLabel,
  monthlyRent,
  TENANCY_STATUS,
  tenancyHome,
} from "@/features/tenancy/labels"
import { NextSteps } from "@/features/tenancy/NextSteps"
import { useTenancy } from "@/features/tenancy/useTenancies"
import { BRAND } from "@/lib/brand"
import type { Tenancy } from "@/types/api"

function StatusNote({ tenancy }: { tenancy: Tenancy }) {
  if (tenancy.status === "PENDING_CONFIRMATION") {
    const mine = tenancy.initiatedBy === tenancy.myRole
    return (
      <Notice tone="info" icon="clock">
        {mine
          ? `Waiting for the ${tenancy.myRole === "LANDLORD" ? "tenant" : "landlord"} to confirm.`
          : "Check the dates and rent. Confirm only if they are right; decline if you never had this tenancy."}
        {tenancy.expiresAt && ` The request expires on ${dateLabel(tenancy.expiresAt)}.`}
      </Notice>
    )
  }
  if (tenancy.status === "DECLINED" && tenancy.declineReason) {
    return <Notice tone="warn">Declined: {tenancy.declineReason}</Notice>
  }
  if (tenancy.status === "ACTIVE" || tenancy.status === "ENDED") {
    if (tenancy.verificationStatus === "VERIFIED") {
      return (
        <Notice tone="teal" data-testid="tenancy-verified">
          Verified by {BRAND.name}: the team checked this tenancy from its rental agreement.
        </Notice>
      )
    }
    // A tenant's own record (D-142) stands without the owner; the team verifies it later.
    return tenancy.confirmedAt ? (
      <Notice tone="teal">
        Confirmed on {dateLabel(tenancy.confirmedAt)}. Its confirmed rent shows in the home&apos;s
        public rent history, with no names.
      </Notice>
    ) : (
      <Notice tone="teal">
        Recorded by you. It shows on the home&apos;s page, with no names, marked &ldquo;Not verified
        yet&rdquo; until the {BRAND.name} team verifies it.
      </Notice>
    )
  }
  return null
}

/**
 * Tenancy detail (`/tenancies/:id`, the "tenancy detail" screen of DESIGN.md §3): parties, dates,
 * rent, the confirm / decline view, and rent records. Parties and admins only; others get 404.
 */
export default function TenancyPage() {
  const { id = "" } = useParams()
  const query = useTenancy(id)

  if (query.isPending) {
    return (
      <main>
        <Container className="py-12">
          <p className="text-sm text-ink-500">Loading the tenancy…</p>
        </Container>
      </main>
    )
  }
  if (query.isError) {
    const missing =
      isApiError(query.error) && (query.error.status === 404 || query.error.status === 400)
    return (
      <main>
        <Container className="py-12">
          {missing ? (
            <EmptyState
              icon="tenancy"
              title="Tenancy not found"
              description="Only the landlord and tenant of a tenancy can open it."
              action={
                <Button asChild variant="outline">
                  <Link to="/me/tenancies">My tenancies</Link>
                </Button>
              }
            />
          ) : (
            <ErrorState
              title="The tenancy could not be loaded"
              error={query.error}
              onRetry={() => void query.refetch()}
            />
          )}
        </Container>
      </main>
    )
  }

  const tenancy = query.data
  const status = TENANCY_STATUS[tenancy.status]
  return (
    <main>
      <Container className="max-w-[860px] space-y-6 py-8">
        <div className="space-y-2">
          <Link to="/me/tenancies" className="text-sm text-ink-700">
            ← My tenancies
          </Link>
          <h1 className="flex flex-wrap items-center gap-3 font-display text-3xl font-semibold">
            {tenancyHome(tenancy)}
            <Badge variant={status.variant}>{status.label}</Badge>
          </h1>
          <p className="text-ink-700">
            <Link to={`/p/${tenancy.property.id}`}>
              {tenancy.property.locality}, {tenancy.property.city.name}
            </Link>
            {tenancy.property.unitLabel && (
              <span className="ml-1 inline-flex items-center gap-1 text-ink-500">
                · Flat {tenancy.property.unitLabel} <LockIcon size={12} />
              </span>
            )}
          </p>
        </div>

        <section
          aria-label="Tenancy"
          className="space-y-4 rounded-xl border border-line bg-surface p-5 shadow-sm"
        >
          <dl className="grid gap-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-ink-500">Other side</dt>
              <dd className="font-semibold text-ink-900">{counterparty(tenancy)}</dd>
            </div>
            <div>
              <dt className="text-ink-500">Requested by</dt>
              <dd className="font-semibold text-ink-900">
                {tenancy.initiatedBy === tenancy.myRole
                  ? "You"
                  : tenancy.initiatedBy === "LANDLORD"
                    ? "The landlord"
                    : "The tenant"}{" "}
                on {dateLabel(tenancy.createdAt)}
              </dd>
            </div>
            <div>
              <dt className="text-ink-500">Starting rent</dt>
              <dd className="num font-semibold text-ink-900">
                {monthlyRent(tenancy.initialRentInr)}
              </dd>
            </div>
            {tenancy.depositRefundedInr !== null && (
              <div>
                <dt className="text-ink-500">Deposit refunded</dt>
                <dd className="num font-semibold text-ink-900">
                  {rupees(tenancy.depositRefundedInr)}
                </dd>
              </div>
            )}
          </dl>
          <TenancyFacts tenancy={tenancy} />
          <p className="inline-flex items-center gap-1.5 text-xs text-ink-500">
            <LockIcon size={12} />
            Exact dates, deposit and flat number are private to the two of you.
          </p>
          <StatusNote tenancy={tenancy} />
          <NextSteps tenancy={tenancy} />
          <TenancyActions tenancy={tenancy} />
        </section>

        {(tenancy.status === "ACTIVE" || tenancy.status === "ENDED") && (
          <RentRecords tenancy={tenancy} />
        )}
      </Container>
    </main>
  )
}
