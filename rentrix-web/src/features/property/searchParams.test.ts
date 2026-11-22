import { describe, expect, it } from "vitest"
import { PAGE_SIZE, activeFilterCount, searchFromParams, withFilters } from "./searchParams"

const params = (qs: string) => new URLSearchParams(qs)

describe("searchFromParams", () => {
  it("maps URL filters to the API query, page 1-based to 0-based", () => {
    expect(
      searchFromParams(
        params("city=blr&locality=Kora&type=apartment&bedrooms=2&minRating=4&sort=newest&page=3"),
      ),
    ).toEqual({
      city: "BLR",
      locality: "Kora",
      q: undefined,
      type: "APARTMENT",
      bedrooms: 2,
      minRating: 4,
      sort: "newest",
      page: 2,
      size: PAGE_SIZE,
    })
  })

  it("drops unknown and out-of-range values instead of sending them", () => {
    const search = searchFromParams(
      params("type=castle&bedrooms=-1&minRating=9&sort=cheapest&page=0&locality=%20%20"),
    )
    expect(search).toEqual({
      city: undefined,
      locality: undefined,
      q: undefined,
      type: undefined,
      bedrooms: undefined,
      minRating: undefined,
      sort: undefined,
      page: undefined,
      size: PAGE_SIZE,
    })
  })

  it("leaves the default sort and page 1 out of the query", () => {
    const search = searchFromParams(params("sort=top_rated&page=1"))
    expect(search.sort).toBeUndefined()
    expect(search.page).toBeUndefined()
  })
})

describe("withFilters", () => {
  it("resets the page when a filter changes", () => {
    const next = withFilters(params("city=BLR&page=4"), { type: "VILLA" })
    expect(next.toString()).toBe("city=BLR&type=VILLA")
  })

  it("keeps filters when only the page changes, and drops page 1", () => {
    expect(withFilters(params("city=PNQ&bedrooms=2"), { page: 2 }).toString()).toBe(
      "city=PNQ&bedrooms=2&page=2",
    )
    expect(withFilters(params("city=PNQ&page=2"), { page: 1 }).toString()).toBe("city=PNQ")
  })

  it("removes a filter set to undefined or blank and drops the default sort", () => {
    const next = withFilters(params("city=BLR&locality=HSR&sort=newest"), {
      locality: "",
      sort: "top_rated",
    })
    expect(next.toString()).toBe("city=BLR")
  })
})

describe("activeFilterCount", () => {
  it("counts filters beyond the city", () => {
    expect(activeFilterCount(searchFromParams(params("city=BLR")))).toBe(0)
    expect(activeFilterCount(searchFromParams(params("city=BLR&type=STUDIO&bedrooms=1")))).toBe(2)
  })
})
