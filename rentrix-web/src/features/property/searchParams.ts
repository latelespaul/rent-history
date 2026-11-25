import type { PropertySearch } from "@/api/properties"
import type { PropertyType } from "@/types/api"

/**
 * The search page keeps every filter in the URL (`/search?city=BLR&type=APARTMENT&page=2`), so a
 * result list can be shared, reloaded and walked back with the browser. These helpers are the
 * only translation between the URL and the `GET /properties` query.
 */

/** Results per page (D-59 allows up to 100; 10 keeps the cards readable). */
export const PAGE_SIZE = 10

export const SORTS = [
  { value: "top_rated", label: "Top rated" },
  { value: "most_reviewed", label: "Most reviewed" },
  { value: "newest", label: "Newest" },
] as const

export const DEFAULT_SORT = "top_rated"

/** Filter chips for type; `ROOM` and `PG` stay separate because the API filters one type. */
export const TYPE_OPTIONS: ReadonlyArray<{ value: PropertyType; label: string }> = [
  { value: "APARTMENT", label: "Apartment" },
  { value: "INDEPENDENT_HOUSE", label: "House" },
  { value: "VILLA", label: "Villa" },
  { value: "STUDIO", label: "Studio" },
  { value: "ROOM", label: "Room" },
  { value: "PG", label: "PG" },
]

/** Bedroom choices; the API matches an exact count (0 is a studio). Older links only: the panel offers home size. */
export const BEDROOM_OPTIONS = [1, 2, 3, 4] as const

/** Home-size filter values, as the API names them. */
const SIZES = new Set<string>([
  "RK1",
  "BHK1",
  "BHK2",
  "BHK3",
  "BHK4",
  "BHK5_PLUS",
  "STUDIO",
  "OTHER",
])

/** Minimum property rating; it only matches homes with 3+ reviews (D-52). */
export const RATING_OPTIONS = [3, 3.5, 4, 4.5] as const

/** The filter keys the URL may carry, in the order they are written. */
const KEYS = [
  "city",
  "locality",
  "society",
  "q",
  "type",
  "homeSize",
  "bedrooms",
  "minRating",
  "sort",
  "page",
] as const

const TYPES = new Set<string>(TYPE_OPTIONS.map((t) => t.value))
const SORT_VALUES = new Set<string>(SORTS.map((s) => s.value))

function intParam(value: string | null, min: number, max: number): number | undefined {
  if (value === null || !/^\d+$/.test(value)) return undefined
  const n = Number(value)
  return n >= min && n <= max ? n : undefined
}

function ratingParam(value: string | null): number | undefined {
  if (value === null || !/^\d(\.\d)?$/.test(value)) return undefined
  const n = Number(value)
  return n >= 1 && n <= 5 ? n : undefined
}

function textParam(value: string | null): string | undefined {
  const t = value?.trim()
  return t ? t.slice(0, 100) : undefined
}

/**
 * URL → API query. Unknown or out-of-range values are dropped (never sent), so a hand-edited
 * URL cannot turn into a `400`. The URL's `page` is 1-based for people; the API's is 0-based.
 */
export function searchFromParams(params: URLSearchParams): PropertySearch {
  const type = params.get("type")?.toUpperCase()
  const homeSize = params.get("homeSize")?.toUpperCase()
  const sort = params.get("sort")
  const page = intParam(params.get("page"), 1, 10_000)
  return {
    city: textParam(params.get("city"))?.toUpperCase(),
    locality: textParam(params.get("locality")),
    // a picked society (D-167): only an id-shaped value is ever sent
    society: /^[0-9a-f-]{36}$/i.test(params.get("society") ?? "")
      ? params.get("society")!
      : undefined,
    q: textParam(params.get("q")),
    type: type && TYPES.has(type) ? type : undefined,
    homeSize: homeSize && SIZES.has(homeSize) ? homeSize : undefined,
    bedrooms: intParam(params.get("bedrooms"), 0, 20),
    minRating: ratingParam(params.get("minRating")),
    sort: sort && SORT_VALUES.has(sort) && sort !== DEFAULT_SORT ? sort : undefined,
    page: page && page > 1 ? page - 1 : undefined,
    size: PAGE_SIZE,
  }
}

/** The filters a person sets, as the URL holds them (page 1-based, defaults left out). */
export type SearchFilters = Partial<Record<(typeof KEYS)[number], string | number | undefined>>

/**
 * Applies `changes` to the current URL params and returns the new ones. Any filter change resets
 * the page to 1 unless the change is the page itself; a value of `undefined` or `""` removes it.
 */
export function withFilters(current: URLSearchParams, changes: SearchFilters): URLSearchParams {
  const next = new URLSearchParams(current)
  for (const [key, value] of Object.entries(changes)) {
    if (value === undefined || value === "") next.delete(key)
    else next.set(key, String(value))
  }
  if (!("page" in changes)) next.delete("page")
  if (next.get("page") === "1") next.delete("page")
  if (next.get("sort") === DEFAULT_SORT) next.delete("sort")
  const ordered = new URLSearchParams()
  for (const key of KEYS) {
    const value = next.get(key)
    if (value !== null) ordered.set(key, value)
  }
  return ordered
}

/** How many filters beyond the city are set (for the "Clear" link and the mobile toggle). */
export function activeFilterCount(search: PropertySearch): number {
  return [
    search.locality,
    search.society,
    search.q,
    search.type,
    search.homeSize,
    search.bedrooms,
    search.minRating,
  ].filter((v) => v !== undefined).length
}
