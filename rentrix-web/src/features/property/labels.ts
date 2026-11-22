import type { Furnishing, PropertyType, RatingSummary } from "@/types/api"

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
