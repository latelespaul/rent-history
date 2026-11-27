import type { PropertySearch } from "@/api/properties"
import type { City, PropertyType } from "@/types/api"

/**
 * The search page keeps every filter in the URL (`/search?city=BLR,PNQ&type=APARTMENT&page=2`), so a
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

/**
 * Filter chips for type. There is no Studio chip: a studio is a home size (every `STUDIO` type home also has
 * the `STUDIO` size, V14), and an old `type=STUDIO` link is read as that size.
 */
export const TYPE_OPTIONS: ReadonlyArray<{ value: PropertyType; label: string }> = [
  { value: "APARTMENT", label: "Apartment" },
  { value: "INDEPENDENT_HOUSE", label: "House" },
  { value: "VILLA", label: "Villa" },
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
const LEGACY_STUDIO_TYPE = "STUDIO"
const SORT_VALUES = new Set<string>(SORTS.map((s) => s.value))

/**
 * A multi-value filter from the URL: a comma list (`type=APARTMENT,VILLA`) or repeated params, upper-cased,
 * unknown values dropped, repeats removed, in the order given. Empty when nothing valid is left.
 */
function listParam(values: string[], allowed: Set<string>): string[] {
  const picked = values
    .flatMap((v) => v.split(","))
    .map((v) => v.trim().toUpperCase())
    .filter((v) => allowed.has(v))
  return [...new Set(picked)]
}

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

/** How many cities one search may name; the API ignores any beyond the same bound. */
export const MAX_CITIES = 20

/**
 * The cities a `city=` value names (captain 2026-11-27): comma-separated, so `?city=BLR,PNQ` finds
 * homes in either; blanks and repeats are dropped, at most `MAX_CITIES`. Empty means every city.
 */
export function cityTokens(value: string | null | undefined): string[] {
  const seen = new Set<string>()
  for (const raw of (value ?? "").split(",")) {
    const token = raw.trim().slice(0, 40)
    if (token && !seen.has(token.toUpperCase())) seen.add(token.toUpperCase())
  }
  return [...seen].slice(0, MAX_CITIES)
}

/**
 * The known city codes among `tokens`, in their order. A token is a city's code, name or alias in
 * any case (`bangalore`, `Bengaluru` and `BLR` are one city); an unknown token is ignored, never an error.
 */
export function knownCities(tokens: string[], cities: City[]): string[] {
  const codes: string[] = []
  for (const token of tokens) {
    const t = token.toLocaleLowerCase()
    const city = cities.find(
      (c) =>
        c.code.toLowerCase() === t ||
        c.name.toLocaleLowerCase() === t ||
        c.aliases.some((alias) => alias.toLocaleLowerCase() === t),
    )
    if (city && !codes.includes(city.code)) codes.push(city.code)
  }
  return codes
}

/** The URL's query string with its commas left readable (`?city=BLR,PNQ`, not `BLR%2CPNQ`). */
export function searchString(params: URLSearchParams): string {
  const text = params.toString().replace(/%2C/gi, ",")
  return text ? `?${text}` : ""
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
  const rawTypes = params.getAll("type")
  // an old `type=STUDIO` link means the Studio home size, the only place Studio is offered
  const studioLink = listParam(rawTypes, new Set([LEGACY_STUDIO_TYPE])).length > 0
  const type = listParam(rawTypes, TYPES)
  const homeSize = listParam(
    [...params.getAll("homeSize"), ...(studioLink ? ["STUDIO"] : [])],
    SIZES,
  )
  const sort = params.get("sort")
  const page = intParam(params.get("page"), 1, 10_000)
  return {
    // the city tokens as typed; the page keeps only known cities (`knownCities`) before it searches
    city: cityTokens(params.get("city")).join(",") || undefined,
    locality: textParam(params.get("locality")),
    // a picked society (D-167): only an id-shaped value is ever sent
    society: /^[0-9a-f-]{36}$/i.test(params.get("society") ?? "")
      ? params.get("society")!
      : undefined,
    q: textParam(params.get("q")),
    type: type.length > 0 ? type : undefined,
    homeSize: homeSize.length > 0 ? homeSize : undefined,
    bedrooms: intParam(params.get("bedrooms"), 0, 20),
    minRating: ratingParam(params.get("minRating")),
    sort: sort && SORT_VALUES.has(sort) && sort !== DEFAULT_SORT ? sort : undefined,
    page: page && page > 1 ? page - 1 : undefined,
    size: PAGE_SIZE,
  }
}

/** The filters a person sets, as the URL holds them (page 1-based, defaults left out). */
export type SearchFilters = Partial<
  Record<(typeof KEYS)[number], string | number | string[] | undefined>
>

/**
 * Applies `changes` to the current URL params and returns the new ones. Any filter change resets
 * the page to 1 unless the change is the page itself; a value of `undefined` or `""` removes it.
 */
export function withFilters(current: URLSearchParams, changes: SearchFilters): URLSearchParams {
  const next = new URLSearchParams(current)
  for (const [key, value] of Object.entries(changes)) {
    // a list is written as one comma-separated param; an empty one removes it
    const text = Array.isArray(value) ? value.join(",") : value
    if (text === undefined || text === "") next.delete(key)
    else next.set(key, String(text))
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

/** `values` with `value` switched on or off, for a multi-select chip group; undefined when none is left. */
export function toggled(values: string[] | undefined, value: string): string[] | undefined {
  const current = values ?? []
  const next = current.includes(value) ? current.filter((v) => v !== value) : [...current, value]
  return next.length > 0 ? next : undefined
}
