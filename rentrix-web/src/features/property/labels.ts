import type { Furnishing, HomeSize, PropertyType, RatingSummary } from "@/types/api"

/** Display words for the API's enums and numbers. No number here is invented: each formats API data. */

const TYPE_LABELS: Record<PropertyType, string> = {
  APARTMENT: "Apartment",
  INDEPENDENT_HOUSE: "Independent house",
  VILLA: "Villa",
  STUDIO: "Studio",
  ROOM: "Room",
  PG: "PG",
}

const FURNISHING_LABELS: Record<Furnishing, string> = {
  UNFURNISHED: "Unfurnished",
  SEMI: "Semi-furnished",
  FULL: "Fully furnished",
}

export function typeLabel(type: PropertyType): string {
  return TYPE_LABELS[type] ?? type
}

export function furnishingLabel(furnishing: Furnishing | null | undefined): string | null {
  return furnishing ? (FURNISHING_LABELS[furnishing] ?? furnishing) : null
}

/** The home-size chips, in order (captain 2026-11-23 17:18). */
export const HOME_SIZES: Array<{ value: HomeSize; label: string }> = [
  { value: "RK1", label: "1 RK" },
  { value: "BHK1", label: "1 BHK" },
  { value: "BHK2", label: "2 BHK" },
  { value: "BHK3", label: "3 BHK" },
  { value: "BHK4", label: "4 BHK" },
  { value: "BHK5_PLUS", label: "5+ BHK" },
  { value: "STUDIO", label: "Studio" },
  { value: "OTHER", label: "Other" },
]

/** "2 BHK", "1 RK", or the user's own words for "Other"; null when not given. */
export function homeSizeLabel(
  size: HomeSize | null | undefined,
  other?: string | null,
): string | null {
  if (!size) return null
  if (size === "OTHER") return other?.trim() || null
  return HOME_SIZES.find((s) => s.value === size)?.label ?? null
}

/**
 * The size fact of a home: its home size (1 RK, 2 BHK, "2.5 BHK"…), else its bedroom count; null
 * when unknown or when it only repeats the type ("Studio").
 */
export function sizeLabel(home: {
  propertyType: PropertyType
  bedrooms: number | null
  homeSize?: HomeSize
  homeSizeOther?: string
}): string | null {
  const size = homeSizeLabel(home.homeSize, home.homeSizeOther) ?? bedroomsLabel(home.bedrooms)
  return size === typeLabel(home.propertyType) ? null : size
}

/** "2 BHK" for flats and houses; "Studio" has no bedroom count of its own. */
export function bedroomsLabel(bedrooms: number | null | undefined): string | null {
  if (bedrooms === null || bedrooms === undefined) return null
  return bedrooms === 0 ? "No separate bedroom" : `${bedrooms} BHK`
}

const number = new Intl.NumberFormat("en-IN")

export function sqftLabel(areaSqft: number | null | undefined): string | null {
  return areaSqft ? `${number.format(areaSqft)} sq ft` : null
}

/** "₹36,500" in Indian digit grouping. */
export function rupees(amount: number): string {
  return `₹${number.format(amount)}`
}

/** "1 review", "4 reviews". */
export function reviewCount(n: number): string {
  return `${n} ${n === 1 ? "review" : "reviews"}`
}

/** True when the API sent an average (3+ reviews, D-52); otherwise the UI shows "New". */
export function hasAverage(rating: RatingSummary | null | undefined): rating is RatingSummary & {
  average: number
} {
  return rating?.average !== null && rating?.average !== undefined
}

/** The building line of a card: building name, else street, else the locality. */
export function propertyTitle(p: {
  buildingName: string | null
  street: string | null
  locality: string
}): string {
  return p.buildingName?.trim() || p.street?.trim() || p.locality
}

/** "Koramangala, Bangalore · 560034" */
export function placeLine(p: {
  locality: string
  city: { name: string }
  pincode?: string | null
}) {
  return [`${p.locality}, ${p.city.name}`, p.pincode].filter(Boolean).join(" · ")
}

/** "May 2024" from an ISO date or a `YYYY-MM` month (others see month precision only, D-57). */
export function monthLabel(value: string | null | undefined): string | null {
  if (!value) return null
  const match = /^(\d{4})-(\d{2})/.exec(value)
  if (!match) return value
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, 1))
  return date.toLocaleDateString("en-IN", { month: "short", year: "numeric", timeZone: "UTC" })
}

/**
 * "Tower 4 · Wing B · Flat 1204": the home's place inside its society. A bare tower or wing name
 * ("4", "B") gets its word; one that already says it ("Tower 4", "Wing A", "Block C") is kept.
 * Empty when the home has no tower, wing or visible flat number.
 */
export function unitLine(
  location: { tower?: { name: string }; wing?: { name: string } } | undefined,
  unitLabel?: string | null,
): string {
  const tower = location?.tower?.name.trim()
  const wing = location?.wing?.name.trim()
  const unit = unitLabel?.trim()
  return [
    tower && (/\b(tower|block)\b/i.test(tower) ? tower : `Tower ${tower}`),
    wing && (/\bwing\b/i.test(wing) ? wing : `Wing ${wing}`),
    unit && (/\b(flat|house|villa|unit)\b/i.test(unit) ? unit : `Flat ${unit}`),
  ]
    .filter(Boolean)
    .join(" · ")
}

/** True when any level of the home's place is a new name the team has not reviewed yet. */
export function locationPending(
  location: Partial<Record<"area" | "society" | "tower" | "wing", { status: string }>> | undefined,
): boolean {
  if (!location) return false
  return (["area", "society", "tower", "wing"] as const).some(
    (level) => location[level]?.status === "PENDING",
  )
}
