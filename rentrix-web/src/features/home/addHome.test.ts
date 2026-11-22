import { describe, expect, it } from "vitest"
import {
  checkStep,
  EMPTY_HOME,
  matchQuery,
  OTHER_CITY,
  stepsFor,
  toAddHomeBody,
  type HomeDraft,
} from "./addHome"

const TODAY = "2026-11-22"

const address: HomeDraft = {
  ...EMPTY_HOME,
  city: "BLR",
  location: {
    area: { kind: "pick", id: "a1", name: "Koramangala", status: "ACTIVE" },
    society: { kind: "new", name: " Lakeside Habitat ", extra: { societyKind: "GATED_COMMUNITY" } },
    tower: { kind: "none" },
    wing: { kind: "none" },
  },
  unitLabel: " C-1204 ",
  pincode: "560034",
}

describe("add your home form (D-138)", () => {
  it("asks for the area, PIN code and flat number before going on", () => {
    expect(checkStep("address", { ...EMPTY_HOME, city: "BLR" }, TODAY)).toEqual({
      area: "Pick or type the area.",
      pincode: "Enter the 6-digit PIN code.",
      unitLabel: "Enter the flat or house number.",
    })
    expect(checkStep("address", address, TODAY)).toEqual({})
    // A home picked from "Is it this one?" needs no address.
    expect(checkStep("address", { ...EMPTY_HOME, propertyId: "p1" }, TODAY)).toEqual({})
  })

  it("checks the tenancy: dates in order, not in the future, a rent in rupees", () => {
    const rent = { ...address, relation: "RENT" as const }
    expect(checkStep("tenancy", { ...rent, startDate: "2026-12-01", rent: "abc" }, TODAY)).toEqual({
      startDate: "The move-in date cannot be in the future.",
      rent: "Enter the monthly rent in rupees.",
    })
    expect(
      checkStep(
        "tenancy",
        {
          ...rent,
          startDate: "2026-03-01",
          stillLiving: false,
          endDate: "2026-01-01",
          rent: "42,000",
        },
        TODAY,
      ),
    ).toEqual({ endDate: "The move-out date is before the move-in date." })
    expect(
      checkStep("tenancy", { ...rent, startDate: "2026-03-01", rent: "42000" }, TODAY),
    ).toEqual({})
  })

  it("an owner skips the tenancy and owner steps and cannot pick a listed home", () => {
    expect(stepsFor({ ...address, relation: "OWN" })).toEqual(["address", "relation"])
    expect(stepsFor({ ...address, relation: "RENT" })).toHaveLength(4)
    expect(
      checkStep("relation", { ...EMPTY_HOME, propertyId: "p1", relation: "OWN" }, TODAY).relation,
    ).toMatch(/already listed/)
  })

  it("a city not in the list goes with its name, state and a typed locality (D-142)", () => {
    const other: HomeDraft = {
      ...EMPTY_HOME,
      city: OTHER_CITY,
      otherCity: "Mysuru",
      otherState: "Karnataka",
      pincode: "570002",
      unitLabel: "12",
    }
    expect(checkStep("address", other, TODAY)).toEqual({ locality: "Type the area or locality." })
    expect(matchQuery({ ...other, locality: "Gokulam" })).toBeNull()
    expect(toAddHomeBody({ ...other, locality: " Gokulam ", relation: "OWN" })).toEqual({
      relation: "OWN",
      home: {
        city: OTHER_CITY,
        pincode: "570002",
        locality: "Gokulam",
        unitLabel: "12",
        propertyType: "APARTMENT",
        bedrooms: null,
      },
      newCity: { name: "Mysuru", state: "Karnataka" },
    })
  })

  it("searches for duplicates by PIN code, society and flat number", () => {
    expect(matchQuery(address)).toEqual({
      city: "BLR",
      pincode: "560034",
      unit: "C-1204",
      societyName: "Lakeside Habitat",
    })
    expect(matchQuery({ ...address, unitLabel: "" })).toBeNull()
  })

  it("sends the home, the tenancy and the owner contact in one body", () => {
    const body = toAddHomeBody({
      ...address,
      relation: "RENT",
      startDate: "2026-03-01",
      rent: "42,000",
      deposit: "",
      ownerName: " Ravi ",
      ownerEmail: "",
      ownerPhone: "98450 00001",
    })
    expect(body).toEqual({
      relation: "RENT",
      home: {
        city: "BLR",
        pincode: "560034",
        buildingName: "Lakeside Habitat",
        unitLabel: "C-1204",
        propertyType: "APARTMENT",
        bedrooms: null,
        location: {
          areaId: "a1",
          newSociety: { name: "Lakeside Habitat", kind: "GATED_COMMUNITY" },
        },
      },
      startDate: "2026-03-01",
      monthlyRentInr: 42000,
      owner: { name: "Ravi", email: undefined, phone: "98450 00001" },
    })
    expect(toAddHomeBody({ ...address, propertyId: "p1", relation: "OWN" })).toEqual({
      relation: "OWN",
      propertyId: "p1",
    })
  })
})
