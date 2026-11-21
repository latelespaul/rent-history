import { describe, expect, it } from "vitest"
import { LAUNCH_CITIES, NINE_CITIES, SEED_STATS, byName } from "@/test/cities"
import { filterCities, highlight, launchCopy, sortForCards, statsByCity } from "./cityText"

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
  it("names up to three cities and counts beyond", () => {
    const [blr, pnq] = LAUNCH_CITIES
    expect(launchCopy([blr])).toBe("Starting in Bangalore, more cities coming")
    expect(launchCopy(LAUNCH_CITIES)).toBe("Starting in Bangalore and Pune, more cities coming")
    expect(launchCopy(byName([...LAUNCH_CITIES, NINE_CITIES[0]]))).toBe(
      "Starting in Ahmedabad, Bangalore and Pune, more cities coming",
    )
    expect(launchCopy(NINE_CITIES)).toBe("Live in 9 cities, more coming")
    expect(launchCopy([pnq, blr])).not.toMatch(/only/i)
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
