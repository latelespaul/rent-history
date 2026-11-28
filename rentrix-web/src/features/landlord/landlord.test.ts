import { describe, expect, it } from "vitest"
import { detail } from "@/test/properties"
import {
  cityAfterPick,
  draftFromLocation,
  EMPTY_DRAFT,
  pincodeAfterPick,
  sameDraft,
  setLevel,
  toLocationInput,
  type LocationDraft,
} from "./locationDraft"
import { checkForm, EMPTY_FORM, formFromProperty, toCreateBody, toPatchBody } from "./propertyForm"

const placed: LocationDraft = {
  area: { kind: "pick", id: "a1", name: "Koramangala", status: "ACTIVE" },
  society: { kind: "pick", id: "s1", name: "Demo Greens", status: "ACTIVE" },
  tower: { kind: "pick", id: "t1", name: "Tower 1", status: "ACTIVE" },
  wing: { kind: "pick", id: "w1", name: "Wing A", status: "ACTIVE" },
}

describe("location draft", () => {
  it("clears every lower level when a higher level changes", () => {
    const next = setLevel(placed, "society", {
      kind: "new",
      name: "Demo Park",
      extra: { societyKind: "GATED_COMMUNITY" },
    })
    expect(next.area).toEqual(placed.area)
    expect(next.society.kind).toBe("new")
    expect(next.tower).toEqual({ kind: "none" })
    expect(next.wing).toEqual({ kind: "none" })
    expect(setLevel(placed, "area", { kind: "none" })).toEqual(EMPTY_DRAFT)
  })

  it("sends an id or a proposal per level, and a new area takes the PIN code", () => {
    expect(toLocationInput(placed, "560034")).toEqual({
      areaId: "a1",
      societyId: "s1",
      towerId: "t1",
      wingId: "w1",
    })
    const proposal = setLevel(
      setLevel(EMPTY_DRAFT, "area", { kind: "new", name: " Demo Nagar ", extra: {} }),
      "society",
      { kind: "new", name: "Demo Park", extra: { societyKind: "APARTMENT_COMPLEX" } },
    )
    expect(toLocationInput(proposal, "560099")).toEqual({
      newArea: { name: "Demo Nagar", pincode: "560099" },
      newSociety: { name: "Demo Park", kind: "APARTMENT_COMPLEX" },
    })
  })

  it("a newly picked area fills its main PIN code and an empty city (captain 2026-11-28)", () => {
    const kora = setLevel(EMPTY_DRAFT, "area", {
      kind: "pick",
      id: "a1",
      name: "Koramangala",
      status: "ACTIVE",
      pincodes: ["560034", "560095"],
      city: "BLR",
    })
    expect(pincodeAfterPick(EMPTY_DRAFT, kora, "", false)).toBe("560034")
    // a PIN of the area stays; another one is replaced unless the user typed it
    expect(pincodeAfterPick(EMPTY_DRAFT, kora, "560095", false)).toBe("560095")
    expect(pincodeAfterPick(EMPTY_DRAFT, kora, "411045", false)).toBe("560034")
    expect(pincodeAfterPick(EMPTY_DRAFT, kora, "560999", true)).toBe("560999")
    expect(pincodeAfterPick(EMPTY_DRAFT, kora, " ", true)).toBe("560034")
    // the same area again, a society change or a new area name changes nothing
    expect(pincodeAfterPick(kora, kora, "560095", false)).toBe("560095")
    const proposal = setLevel(EMPTY_DRAFT, "area", { kind: "new", name: "Demo Nagar", extra: {} })
    expect(pincodeAfterPick(EMPTY_DRAFT, proposal, "560099", false)).toBe("560099")

    expect(cityAfterPick(EMPTY_DRAFT, kora, "")).toBe("BLR")
    expect(cityAfterPick(EMPTY_DRAFT, kora, "PNQ")).toBe("PNQ")
    expect(cityAfterPick(EMPTY_DRAFT, proposal, "")).toBe("")
  })

  it("reads a saved place back and compares drafts by what they send", () => {
    const draft = draftFromLocation({
      area: { id: "a1", name: "Koramangala", status: "ACTIVE" },
      society: { id: "s1", name: "Demo Greens", status: "PENDING" },
    })
    expect(draft.society).toEqual({
      kind: "pick",
      id: "s1",
      name: "Demo Greens",
      status: "PENDING",
    })
    expect(draft.tower).toEqual({ kind: "none" })
    expect(sameDraft(draft, { ...draft })).toBe(true)
    expect(sameDraft(draft, placed)).toBe(false)
    expect(draftFromLocation(undefined)).toEqual(EMPTY_DRAFT)
  })
})

describe("property form", () => {
  it("asks for the city, PIN code and area, and names for proposals", () => {
    expect(Object.keys(checkForm(EMPTY_FORM)).sort()).toEqual(["city", "location", "pincode"])
    const unnamed = {
      ...EMPTY_FORM,
      city: "BLR",
      pincode: "560034",
      location: setLevel(placed, "tower", { kind: "new", name: "", extra: { towerKind: "TOWER" } }),
    }
    expect(checkForm(unnamed).location).toMatch(/name/)
    expect(checkForm({ ...unnamed, location: placed, bedrooms: "two" }).bedrooms).toBeTruthy()
    expect(checkForm({ ...EMPTY_FORM, bedrooms: "3a" }, false)).toEqual({
      bedrooms: "Enter a whole number.",
    })
  })

  it("creates with the place and numbers, not the free-text locality", () => {
    const body = toCreateBody({
      ...EMPTY_FORM,
      city: "BLR",
      pincode: " 560034 ",
      bedrooms: "2",
      parking: "yes",
      location: placed,
    })
    expect(body).toMatchObject({
      city: "BLR",
      pincode: "560034",
      bedrooms: 2,
      parking: true,
      furnishing: null,
      location: { areaId: "a1", wingId: "w1" },
    })
    expect(body.locality).toBeUndefined()
  })

  it("patches only what changed and never the location of a locked listing", () => {
    const property = detail({
      status: "PUBLISHED",
      unitLabel: "A-101",
      location: { area: { id: "a1", name: "Koramangala", status: "ACTIVE" } },
    })
    const initial = formFromProperty(property)
    const edited = { ...initial, description: "New text", pincode: "560001", location: placed }
    expect(toPatchBody(initial, edited, "PUBLISHED")).toEqual({ description: "New text" })
    expect(toPatchBody(initial, edited, "DRAFT")).toEqual({
      description: "New text",
      pincode: "560001",
      location: { areaId: "a1", societyId: "s1", towerId: "t1", wingId: "w1" },
    })
    expect(toPatchBody(initial, initial, "DRAFT")).toEqual({})
  })
})
