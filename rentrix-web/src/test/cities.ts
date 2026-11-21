import type { City, Stats } from "@/types/api"

/**
 * Test data only: mocked `GET /cities` and `GET /stats` responses. The two launch rows mirror
 * the seed; the other seven are illustrative, like the design v2 mockups. `src/test/` is the one
 * place under `src/` allowed to name a city (see `noCityLiterals.test.ts`).
 */
export const LAUNCH_CITIES: City[] = [
  { code: "BLR", name: "Bangalore", aliases: ["Bengaluru"], state: "Karnataka" },
  { code: "PNQ", name: "Pune", aliases: [], state: "Maharashtra" },
]

export const MORE_CITIES: City[] = [
  { code: "AMD", name: "Ahmedabad", aliases: [], state: "Gujarat" },
  { code: "MAA", name: "Chennai", aliases: ["Madras"], state: "Tamil Nadu" },
  { code: "DEL", name: "Delhi", aliases: ["New Delhi"], state: "Delhi" },
  { code: "HYD", name: "Hyderabad", aliases: [], state: "Telangana" },
  { code: "JAI", name: "Jaipur", aliases: [], state: "Rajasthan" },
  { code: "CCU", name: "Kolkata", aliases: ["Calcutta"], state: "West Bengal" },
  { code: "BOM", name: "Mumbai", aliases: ["Bombay"], state: "Maharashtra" },
]

/** `GET /cities` is ordered by name (D-82). */
export function byName(cities: City[]): City[] {
  return [...cities].sort((a, b) => a.name.localeCompare(b.name))
}

export const NINE_CITIES = byName([...LAUNCH_CITIES, ...MORE_CITIES])

/** Counts as the seed has them: 4 + 4 demo properties; every other city has none. */
export const SEED_STATS: Stats = {
  publishedProperties: 8,
  confirmedTenancies: 13,
  publishedReviews: 20,
  cities: [
    { city: "BLR", publishedProperties: 4, confirmedTenancies: 6, publishedReviews: 10 },
    { city: "PNQ", publishedProperties: 4, confirmedTenancies: 7, publishedReviews: 10 },
  ],
}
