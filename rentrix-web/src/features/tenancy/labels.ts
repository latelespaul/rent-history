import { rupees } from "@/features/property/labels"
import type { PropertyStatus, RentRecordStatus, Tenancy, TenancyStatus } from "@/types/api"

type Variant = "neutral" | "warn" | "success" | "danger" | "info" | "tenant"

/** Display words and badge colour per tenancy state (§6). */
export const TENANCY_STATUS: Record<TenancyStatus, { label: string; variant: Variant }> = {
  PENDING_CONFIRMATION: { label: "Waiting for confirmation", variant: "warn" },
  ACTIVE: { label: "Active", variant: "success" },
  ENDED: { label: "Ended", variant: "neutral" },
  DECLINED: { label: "Declined", variant: "danger" },
  EXPIRED: { label: "Expired", variant: "neutral" },
  CANCELLED: { label: "Cancelled", variant: "neutral" },
  VOIDED: { label: "Voided", variant: "danger" },
}

export const RENT_RECORD_STATUS: Record<RentRecordStatus, { label: string; variant: Variant }> = {
  PROPOSED: { label: "Waiting for confirmation", variant: "warn" },
  CONFIRMED: { label: "Confirmed", variant: "success" },
  DISPUTED: { label: "Disputed", variant: "danger" },
}

/** Listing states (D-21, D-92). */
export const PROPERTY_STATUS: Record<PropertyStatus, { label: string; variant: Variant }> = {
  DRAFT: { label: "Draft", variant: "neutral" },
  PENDING_REVIEW: { label: "Pending review", variant: "warn" },
  PUBLISHED: { label: "Published", variant: "success" },
  REJECTED: { label: "Rejected", variant: "danger" },
  ARCHIVED: { label: "Archived", variant: "neutral" },
}

/** The tabs of `/me/tenancies`, in order. */
export const TENANCY_GROUPS = [
  { key: "current", label: "Current", statuses: ["ACTIVE"] },
  { key: "pending", label: "Pending", statuses: ["PENDING_CONFIRMATION"] },
  { key: "ended", label: "Ended", statuses: ["ENDED"] },
  {
    key: "closed",
    label: "Declined / expired",
    statuses: ["DECLINED", "EXPIRED", "CANCELLED", "VOIDED"],
  },
] as const satisfies ReadonlyArray<{ key: string; label: string; statuses: TenancyStatus[] }>

export type TenancyGroupKey = (typeof TENANCY_GROUPS)[number]["key"]

export function groupOf(status: TenancyStatus): TenancyGroupKey {
  return (
    TENANCY_GROUPS.find((g) => (g.statuses as readonly TenancyStatus[]).includes(status))?.key ??
    "closed"
  )
}

/** "1 Nov 2026" from an ISO date or instant. */
export function dateLabel(value: string | null | undefined): string | null {
  if (!value) return null
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  if (!match) return value
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])))
  return date.toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  })
}

/** Today as `YYYY-MM-DD` in the viewer's time zone (dates are never in the future, D-36). */
export function today(): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, "0")
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/** "Demo Residency A" or, with no building name, the locality. */
export function tenancyHome(t: Pick<Tenancy, "property">): string {
  return t.property.buildingName?.trim() || t.property.locality
}

/** The other side of the tenancy, as the caller sees it. */
export function counterparty(t: Tenancy): string {
  if (t.myRole === "TENANT") return `Landlord ${t.landlord.shortName}`
  if (t.tenant) return `Tenant ${t.tenant.shortName}`
  return t.inviteEmail ? `Invited ${t.inviteEmail}` : "Tenant not linked yet"
}

/** A whole number from a form field, or undefined when empty. */
export function wholeNumber(value: string): number | undefined {
  const trimmed = value.trim().replace(/,/g, "")
  if (!trimmed) return undefined
  const n = Number(trimmed)
  return Number.isFinite(n) ? Math.round(n) : Number.NaN
}

/** "Current rent ₹34,000 / month from 1 Mar 2025"; the proposed rent while unconfirmed. */
export function rentLine(tenancy: Tenancy): string {
  if (tenancy.currentRentInr === null) return `${rupees(tenancy.initialRentInr)} / month`
  const label = tenancy.status === "ACTIVE" ? "Current rent" : "Last rent"
  const from = dateLabel(tenancy.currentRentFrom ?? tenancy.startDate)
  return `${label} ${rupees(tenancy.currentRentInr)} / month from ${from}`
}

/**
 * The months a rent change can start in: from the tenancy's start month to its end month, or to
 * this month while it runs. `initial` is the latest of those, so an ended tenancy opens on its
 * last month, not on this month (#44 re-test).
 */
export function rentChangeMonths(
  t: Pick<Tenancy, "startDate" | "endDate">,
  now: string = today(),
): { min: string; max: string; initial: string } {
  const min = t.startDate.slice(0, 7)
  const thisMonth = now.slice(0, 7)
  const end = t.endDate?.slice(0, 7)
  const last = end && end < thisMonth ? end : thisMonth
  const max = last < min ? min : last
  return { min, max, initial: max }
}
