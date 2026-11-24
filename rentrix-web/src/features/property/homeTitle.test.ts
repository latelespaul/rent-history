import { describe, expect, it } from "vitest"
import { homeTitle } from "./labels"

const level = (name: string) => ({ id: name, name, status: "ACTIVE" })
const base = { buildingName: null, street: null, locality: "Whitefield" }

describe("a home card's title line (D-165)", () => {
  it("names the society, tower and an open flat number", () => {
    expect(
      homeTitle({
        ...base,
        location: {
          society: level("Prestige Lakeside"),
          tower: level("B"),
        },
        unitLabel: "1204",
      }),
    ).toBe("Prestige Lakeside · Tower B · Flat 1204")
  })

  it("leaves the flat number out when the API did not send it (masked, D-57)", () => {
    expect(
      homeTitle({
        ...base,
        location: {
          society: level("Demo Greens"),
          tower: level("Tower 1"),
        },
      }),
    ).toBe("Demo Greens · Tower 1")
  })

  it("falls back to the building, street or locality outside a society", () => {
    expect(homeTitle({ ...base, buildingName: "Rose Villa", unitLabel: "House 7" })).toBe(
      "Rose Villa · House 7",
    )
    expect(homeTitle({ ...base, street: "12th Main" })).toBe("12th Main")
    expect(homeTitle(base)).toBe("Whitefield")
  })
})
