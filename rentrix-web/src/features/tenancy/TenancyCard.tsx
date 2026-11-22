import { Link } from "react-router-dom"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { BuildingIcon, CalendarIcon, KeyIcon, LockIcon, RupeeIcon } from "@/components/ui/icons.gen"
import { rupees } from "@/features/property/labels"
import type { Tenancy } from "@/types/api"
import { counterparty, dateLabel, TENANCY_STATUS, tenancyHome } from "./labels"
import { TenancyActions } from "./TenancyActions"

/** The facts line of a tenancy: dates, rent, deposit. Private to the two parties and admins. */
export function TenancyFacts({ tenancy }: { tenancy: Tenancy }) {
  const rent = tenancy.currentRentInr ?? tenancy.initialRentInr
  return (
    <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-ink-700">
      <span className="inline-flex items-center gap-1.5">
        <CalendarIcon size={16} className="text-ink-500" />
        {tenancy.endDate
          ? `${dateLabel(tenancy.startDate)} – ${dateLabel(tenancy.endDate)}`
          : `Since ${dateLabel(tenancy.startDate)}`}
      </span>
      <span className="num inline-flex items-center gap-1.5">
        <RupeeIcon size={16} className="text-ink-500" />
        {rupees(rent)} / month
      </span>
      {tenancy.depositInr !== null && (
        <span className="num inline-flex items-center gap-1.5">
          <KeyIcon size={16} className="text-ink-500" />
          Deposit {rupees(tenancy.depositInr)}
        </span>
      )}
    </div>
  )
}

/** One tenancy in a list (`/me/tenancies`, landlord dashboard), with the actions allowed now. */
export function TenancyCard({ tenancy }: { tenancy: Tenancy }) {
  const status = TENANCY_STATUS[tenancy.status]
  return (
    <article className="space-y-3 rounded-xl border border-line bg-surface p-5 shadow-sm">
      <div className="flex flex-wrap items-start gap-4">
        <span className="rx-placeholder hidden size-16 shrink-0 place-items-center rounded-lg sm:grid">
          <BuildingIcon size={24} className="text-ink-300" />
        </span>
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <Link to={`/tenancies/${tenancy.id}`} className="text-lg font-semibold text-ink-900">
              {tenancyHome(tenancy)}
            </Link>
            <Badge variant={status.variant}>{status.label}</Badge>
            <Badge variant={tenancy.myRole === "LANDLORD" ? "landlord" : "tenant"}>
              {tenancy.myRole === "LANDLORD" ? "You are the landlord" : "You are the tenant"}
            </Badge>
          </div>
          <p className="inline-flex flex-wrap items-center gap-1.5 text-sm text-ink-500">
            {tenancy.property.locality}, {tenancy.property.city.name}
            {tenancy.property.unitLabel && (
              <>
                {" · Flat "}
                {tenancy.property.unitLabel}
                <LockIcon size={12} aria-label="private" />
              </>
            )}
            {" · "}
            {counterparty(tenancy)}
          </p>
          <TenancyFacts tenancy={tenancy} />
        </div>
        <Button asChild variant="outline" size="sm">
          <Link to={`/tenancies/${tenancy.id}`}>Open</Link>
        </Button>
      </div>
      <TenancyActions tenancy={tenancy} compact />
    </article>
  )
}
