import { describe, expect, it } from "vitest"
import {
  checkStep,
  EMPTY_HOME,
  fieldPlace,
  matchQuery,
  OTHER_CITY,
  placeFieldErrors,
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

describe("add your home form (D-139)", () => {
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

  it("checks the bedrooms on step 1: 0 to 20, or empty", () => {
    expect(checkStep("address", { ...address, bedrooms: "25" }, TODAY)).toEqual({
      bedrooms: "Enter 0 to 20 bedrooms, or leave it empty.",
    })
    expect(checkStep("address", { ...address, bedrooms: "3" }, TODAY)).toEqual({})
  })

  it("takes a one-letter tower or wing, and stops on step 1 for an empty one (#53 bug 1)", () => {
    const inTower = (tower: string, wing: string): HomeDraft => ({
      ...address,
      location: {
        ...address.location,
        tower: { kind: "new", name: tower, extra: { towerKind: "TOWER" } },
        wing: { kind: "new", name: wing, extra: {} },
      },
    })
    expect(checkStep("address", inTower("4", "B"), TODAY)).toEqual({})
    expect(checkStep("address", inTower(" ", "-"), TODAY)).toEqual({
      tower: "Type the tower or block name, e.g. 4 or C.",
      wing: "Type the wing name, e.g. A or B.",
    })
  })

  it("puts every API field error by its field on its step (#53 bug 1)", () => {
    expect(fieldPlace("location.newWing.name")).toEqual({ step: "address", key: "wing" })
    expect(fieldPlace("home.location.areaId")).toEqual({ step: "address", key: "area" })
    expect(fieldPlace("location.newSociety.kind")).toEqual({ step: "address", key: "society" })
    expect(fieldPlace("home.pincode")).toEqual({ step: "address", key: "pincode" })
    expect(fieldPlace("monthlyRentInr")).toEqual({ step: "tenancy", key: "rent" })
    expect(fieldPlace("owner.email")).toEqual({ step: "owner", key: "ownerEmail" })
    expect(fieldPlace("home.bedrooms")).toEqual({ step: "address", key: "bedrooms" })
    expect(fieldPlace("home.propertyType")).toEqual({ step: "address", key: "propertyType" })
    expect(fieldPlace("home.street")).toEqual({ step: "address", key: "form" })
    const steps = stepsFor({ ...address, relation: "RENT" })
    expect(
      placeFieldErrors(
        [
          { field: "owner.email", message: "Not an email." },
          { field: "location.newWing.name", message: "Must be 1 to 60 characters." },
        ],
        steps,
      ),
    ).toEqual({
      step: 0,
      errors: { ownerEmail: "Not an email.", wing: "Must be 1 to 60 characters." },
    })
    expect(placeFieldErrors([], steps)).toBeNull()
  })

  it("checks the tenancy: dates in order, not in the future, a rent in rupees", () => {
    const rent = { ...address, relation: "RENT" as const }
    expect(checkStep("tenancy", { ...rent, startDate: "2026-12-01", rent: "abc" }, TODAY)).toEqual({
      startDate: "The move-in date cannot be in the future.",
      rent: "Enter the monthly rent in rupees, or leave it empty.",
    })
    // The rent is optional: an empty one is "not shared" (captain 2026-11-23 09:55).
    expect(checkStep("tenancy", { ...rent, startDate: "2026-08-01", rent: "" }, TODAY)).toEqual({})
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

  it("a city not in the list goes with its name, state and a typed locality (D-143)", () => {
    const other: HomeDraft = {
      ...EMPTY_HOME,
      city: OTHER_CITY,
      otherCity: "Mysuru",
      otherState: "Karnataka",
      pincode: "570002",
      unitLabel: "12",
    }
    // Not in a society: an apartment there needs its building name too.
    expect(checkStep("address", other, TODAY)).toEqual({
      locality: "Type the area or locality.",
      buildingName: "Enter the building name.",
    })
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

  it("names a standalone apartment by its building, and sends no rent when it is not shared", () => {
    const flat: HomeDraft = {
      ...address,
      location: { ...address.location, society: { kind: "none" } },
    }
    expect(checkStep("address", flat, TODAY)).toEqual({ buildingName: "Enter the building name." })
    expect(checkStep("address", { ...flat, propertyType: "INDEPENDENT_HOUSE" }, TODAY)).toEqual({})
    const body = toAddHomeBody({
      ...flat,
      buildingName: " Sai Residency ",
      relation: "RENT",
      startDate: "2026-03-01",
      rent: "",
      ownerName: "Ravi",
    })
    expect(body.home?.buildingName).toBe("Sai Residency")
    expect(body).not.toHaveProperty("monthlyRentInr")
    expect(body.owner).toEqual({ name: "Ravi", email: undefined, phone: undefined })
  })
})
