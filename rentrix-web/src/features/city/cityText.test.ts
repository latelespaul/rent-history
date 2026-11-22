import type { City } from "@/types/api"
import { describe, expect, it } from "vitest"
import { LAUNCH_CITIES, NINE_CITIES, SEED_STATS, byName } from "@/test/cities"
import {
  filterCities,
  highlight,
  launchCopy,
  launchNames,
  sortForCards,
  statsByCity,
} from "./cityText"

const names = (cities: { name: string }[]) => cities.map((c) => c.name)

describe("filterCities", () => {
  it("finds a city by an alias prefix and a state by a word prefix", () => {
    expect(names(filterCities(NINE_CITIES, "beng"))).toEqual(["Bangalore", "Kolkata"])
  })

  it("matches code, name and state case-insensitively", () => {
    expect(names(filterCities(NINE_CITIES, "pnq"))).toEqual(["Pune"])
    expect(names(filterCities(NINE_CITIES, "PUN"))).toEqual(["Pune"])
    expect(names(filterCities(NINE_CITIES, "maharash"))).toEqual(["Mumbai", "Pune"])
    expect(names(filterCities(NINE_CITIES, "tamil nadu"))).toEqual(["Chennai"])
  })

  it("matches only at the start of a word", () => {
    expect(filterCities(NINE_CITIES, "galore")).toEqual([])
  })

  it("finds the cities of a region by the region's name", () => {
    const ncr: City[] = [
      { code: "DEL", name: "Delhi", aliases: ["New Delhi"], state: "Delhi", region: "Delhi NCR" },
      {
        code: "GGN",
        name: "Gurugram",
        aliases: ["Gurgaon"],
        state: "Haryana",
        region: "Delhi NCR",
      },
      { code: "HYD", name: "Hyderabad", aliases: [], state: "Telangana", region: null },
      { code: "NOI", name: "Noida", aliases: [], state: "Uttar Pradesh", region: "Delhi NCR" },
    ]
    expect(filterCities(ncr, "ncr").map((c) => c.code)).toEqual(["DEL", "GGN", "NOI"])
    expect(filterCities(ncr, "Delhi NCR").map((c) => c.code)).toEqual(["DEL", "GGN", "NOI"])
  })

  it("returns every city for a blank query and none for an unknown one", () => {
    expect(filterCities(NINE_CITIES, "  ")).toHaveLength(9)
    expect(filterCities(NINE_CITIES, "Goa")).toEqual([])
  })
})

describe("highlight", () => {
  it("marks the matched word prefix", () => {
    expect(highlight("Calcutta · West Bengal", "beng")).toEqual([
      { text: "Calcutta · West ", match: false },
      { text: "Beng", match: true },
      { text: "al", match: false },
    ])
  })

  it("leaves text unmarked without a word-start match", () => {
    expect(highlight("Bangalore", "galore")).toEqual([{ text: "Bangalore", match: false }])
  })
})

describe("launchCopy", () => {
  it("names up to six cities or regions and counts beyond", () => {
    const [blr, pnq] = LAUNCH_CITIES
    expect(launchCopy([])).toBe("Cities coming soon")
    expect(launchCopy([blr])).toBe("Live in Bangalore; other cities on request")
    expect(launchCopy(LAUNCH_CITIES)).toBe("Live in Bangalore and Pune; other cities on request")
    expect(launchCopy(byName([...LAUNCH_CITIES, NINE_CITIES[0]]))).toBe(
      "Live in Ahmedabad, Bangalore and Pune; other cities on request",
    )
    expect(launchCopy(NINE_CITIES)).toBe("Live in 9 cities; other cities on request")
    expect(launchCopy([pnq, blr])).not.toMatch(/only/i)
  })

  it("groups a region's cities under the region, at its first city", () => {
    const ncr = (code: string, name: string): City => ({
      code,
      name,
      aliases: [],
      state: "Test",
      region: "Delhi NCR",
    })
    const seven = byName([
      ...LAUNCH_CITIES,
      ncr("DEL", "Delhi"),
      ncr("GGN", "Gurugram"),
      ncr("NOI", "Noida"),
      { code: "HYD", name: "Hyderabad", aliases: [], state: "Telangana", region: null },
      { code: "MUM", name: "Mumbai", aliases: ["Bombay"], state: "Maharashtra" },
    ])
    expect(launchNames(seven)).toEqual([
      "Bangalore",
      "Delhi NCR (Delhi · Gurugram · Noida)",
      "Hyderabad",
      "Mumbai",
      "Pune",
    ])
    expect(launchCopy(seven)).toBe(
      "Live in Bangalore, Delhi NCR (Delhi · Gurugram · Noida), Hyderabad, Mumbai and Pune; other cities on request",
    )
  })
})

describe("sortForCards", () => {
  it("orders by published properties, then by name", () => {
    const counts = statsByCity([
      ...SEED_STATS.cities,
      { city: "HYD", publishedProperties: 9, confirmedTenancies: 0, publishedReviews: 0 },
    ])
    expect(names(sortForCards(NINE_CITIES, counts)).slice(0, 4)).toEqual([
      "Hyderabad",
      "Bangalore",
      "Pune",
      "Ahmedabad",
    ])
  })

  it("orders by name when there are no stats", () => {
    expect(names(sortForCards([...NINE_CITIES].reverse(), new Map()))).toEqual(names(NINE_CITIES))
  })
})
