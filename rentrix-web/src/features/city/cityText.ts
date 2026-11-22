import type { City, CityStats } from "@/types/api"

/**
 * Pure helpers behind the city picker, the launch copy and the Home city cards (D-82, D-85).
 * Every city name comes from the `GET /cities` list passed in; none is written here.
 */

/** How many cities or regions the launch copy names before it switches to a count (D-85). */
export const LAUNCH_COPY_MAX_NAMES = 6

/** How many city cards Home shows before "Show all N cities" (D-85). */
export const CITY_CARDS_VISIBLE = 8

const normalize = (value: string) => value.trim().toLocaleLowerCase()

/** The searchable text of a city: code, name, every alias, the state and the region (D-82). */
function fields(city: City): string[] {
  return [city.code, city.name, ...city.aliases, city.state, city.region].filter(
    (field): field is string => Boolean(field),
  )
}

/**
 * True when `query` is a case-insensitive prefix of a searchable field or of any word in one,
 * so a city is found by the start of its name, of any alias, or of any word of its state.
 * Same rule as the API's `?q=` (D-82). A blank query matches every city.
 */
export function cityMatches(city: City, query: string): boolean {
  const q = normalize(query)
  if (!q) return true
  return fields(city).some((field) => {
    const f = normalize(field)
    return f.startsWith(q) || f.split(/[^\p{L}\p{N}]+/u).some((word) => word.startsWith(q))
  })
}

export function filterCities(cities: City[], query: string): City[] {
  return cities.filter((city) => cityMatches(city, query))
}

/** One run of text, `match` when it is the part the query matched (for `<mark>`). */
export interface TextPart {
  text: string
  match: boolean
}

/** Splits `text` around the first place where `query` starts the text or a word in it. */
export function highlight(text: string, query: string): TextPart[] {
  const q = normalize(query)
  if (!q) return [{ text, match: false }]
  const lower = text.toLocaleLowerCase()
  let at = -1
  for (let i = lower.indexOf(q); i !== -1; i = lower.indexOf(q, i + 1)) {
    if (i === 0 || !/[\p{L}\p{N}]/u.test(lower[i - 1])) {
      at = i
      break
    }
  }
  if (at === -1) return [{ text, match: false }]
  return [
    { text: text.slice(0, at), match: false },
    { text: text.slice(at, at + q.length), match: true },
    { text: text.slice(at + q.length), match: false },
  ].filter((part) => part.text)
}

/** "A", "A and B", "A, B and C". */
function joinNames(names: string[]): string {
  if (names.length <= 1) return names.join("")
  return `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`
}

/**
 * The names the launch copy lists, in API order: a city on its own, or its region once, at its
 * first city, with every city of the region, e.g. "Delhi NCR (Delhi · Gurugram · Noida)".
 */
export function launchNames(cities: City[]): string[] {
  const regions = new Map<string, string[]>()
  const order: { name: string; region: boolean }[] = []
  for (const city of cities) {
    if (!city.region) {
      order.push({ name: city.name, region: false })
      continue
    }
    const members = regions.get(city.region)
    if (members) members.push(city.name)
    else {
      regions.set(city.region, [city.name])
      order.push({ name: city.region, region: true })
    }
  }
  return order.map(({ name, region }) =>
    region ? `${name} (${(regions.get(name) ?? []).join(" · ")})` : name,
  )
}

/**
 * The launch line built from the active list (D-85): names up to LAUNCH_COPY_MAX_NAMES (6)
 * entries from launchNames ("Live in A and B; other cities on request"), counts cities beyond
 * ("Live in 9 cities; other cities on request"). Never says "only".
 */
export function launchCopy(cities: City[]): string {
  if (cities.length === 0) return "Cities coming soon"
  const names = launchNames(cities)
  if (names.length > LAUNCH_COPY_MAX_NAMES)
    return `Live in ${cities.length} cities; other cities on request`
  return `Live in ${joinNames(names)}; other cities on request`
}

/** "Alias · State": aliases then state, for the second line of a picker option and card. */
export function cityDetail(city: City): string {
  return [...city.aliases, city.state].filter(Boolean).join(" · ")
}

export function statsByCity(stats: CityStats[] | undefined): Map<string, CityStats> {
  return new Map((stats ?? []).map((s) => [s.city, s]))
}

/**
 * Home card order (D-85): most published properties first, then by name. Without stats
 * (not loaded or not served yet) every city counts as 0, so the order is by name.
 */
export function sortForCards(cities: City[], stats: Map<string, CityStats>): City[] {
  const count = (city: City) => stats.get(city.code)?.publishedProperties ?? 0
  return [...cities].sort((a, b) => count(b) - count(a) || a.name.localeCompare(b.name))
}

export function plural(n: number, one: string, many = `${one}s`): string {
  return `${n} ${n === 1 ? one : many}`
}
