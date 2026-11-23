import { describe, expect, it } from "vitest"
import { detail } from "@/test/properties"
import type { Tenancy } from "@/types/api"
import { checkHomeEdit, homeEditDraft, homeEditErrors, toHomeUpdate } from "./homeEdit"
import { checkTenancyDraft, tenancyDraft, tenancyErrorKey, toTenancyUpdate } from "./tenancyEdit"
import { needsWarning, VERIFIED_EDIT_WARNING } from "./verified"

const TODAY = "2026-11-23"

const own: Tenancy = {
  id: "t1",
  property: {
    id: "p1",
    buildingName: "Edit Court",
    locality: "Baner",
    city: { code: "PNQ", name: "Pune" },
    unitLabel: "E-1",
    propertyType: "APARTMENT",
  },
  myRole: "TENANT",
  landlord: { id: "", shortName: "Not on Doorway Living yet" },
  tenant: { id: "u1", shortName: "Asha T." },
  inviteEmail: null,
  startDate: "2026-05-01",
  endDate: null,
  initialRentInr: null,
  currentRentInr: null,
  currentRentFrom: null,
  depositInr: 50000,
  depositRefundedInr: null,
  status: "ACTIVE",
  initiatedBy: "TENANT",
  declineReason: null,
  evidenceLevel: null,
  createdAt: "2026-05-02T00:00:00Z",
  expiresAt: null,
  confirmedAt: null,
  endedAt: null,
  actions: {
    confirm: false,
    decline: false,
    cancel: false,
    end: true,
    addRentRecord: true,
    edit: true,
  },
  verificationStatus: "UNVERIFIED",
}

describe("edit a tenancy (D-156)", () => {
  it("starts from the saved values and sends the whole set back", () => {
    const draft = tenancyDraft(own)
    expect(draft).toEqual({
      startDate: "2026-05-01",
      stillLiving: true,
      endDate: "",
      rent: "",
      deposit: "50000",
    })
    expect(toTenancyUpdate({ ...draft, rent: "26,000" })).toEqual({
      startDate: "2026-05-01",
      endDate: null,
      initialRentInr: 26000,
      depositInr: 50000,
    })
    expect(toTenancyUpdate({ ...draft, stillLiving: false, endDate: "2026-10-31" }).endDate).toBe(
      "2026-10-31",
    )
  })

  it("reuses the add-home tenancy checks; the rent is required unless it is the tenant's own record", () => {
    expect(checkTenancyDraft(tenancyDraft(own), own, TODAY)).toEqual({})
    expect(
      checkTenancyDraft({ ...tenancyDraft(own), startDate: "2026-12-01" }, own, TODAY),
    ).toEqual({
      startDate: "The move-in date cannot be in the future.",
    })
    expect(
      checkTenancyDraft({ ...tenancyDraft(own), stillLiving: false, endDate: "" }, own, TODAY)
        .endDate,
    ).toMatch(/moved out/)
    const confirmed = { ...own, confirmedAt: "2026-06-01T00:00:00Z" }
    expect(checkTenancyDraft(tenancyDraft(confirmed), confirmed, TODAY)).toEqual({
      rent: "Enter the monthly rent.",
    })
  })

  it("shows each API field error on its own field", () => {
    expect(tenancyErrorKey("initialRentInr")).toBe("rent")
    expect(tenancyErrorKey("depositInr")).toBe("deposit")
    expect(tenancyErrorKey("startDate")).toBe("startDate")
  })

  it("warns only before changing a verified item", () => {
    expect(needsWarning("VERIFIED")).toBe(true)
    expect(needsWarning("UNVERIFIED")).toBe(false)
    expect(needsWarning("REJECTED")).toBe(false)
    expect(needsWarning(undefined)).toBe(false)
    expect(VERIFIED_EDIT_WARNING).toBe(
      "Saving will remove the Verified by Doorway Living tick until our team checks it again.",
    )
  })
})

describe("edit a home (D-156)", () => {
  const inSociety = detail({
    buildingName: "Palm Grove",
    street: null,
    unitLabel: "A-101",
    pincode: "411045",
    homeSize: "BHK2",
    location: {
      area: { id: "a1", name: "Baner", status: "ACTIVE" },
      society: { id: "s1", name: "Palm Grove", status: "ACTIVE" },
    },
    editable: true,
  })

  it("sends no location while the place is unchanged, and the society names the home", () => {
    const draft = homeEditDraft(inSociety)
    expect(draft.buildingName).toBe("")
    expect(toHomeUpdate({ ...draft, unitLabel: " A-102 " }, inSociety)).toEqual({
      pincode: "411045",
      unitLabel: "A-102",
      buildingName: "Palm Grove",
      homeSize: "BHK2",
    })
  })

  it("moves the home to a typed society", () => {
    const draft = homeEditDraft(inSociety)
    const moved = {
      ...draft,
      location: {
        ...draft.location,
        society: {
          kind: "new" as const,
          name: "Palm Grove Phase 2",
          extra: { societyKind: "GATED_COMMUNITY" as const },
        },
      },
    }
    expect(toHomeUpdate(moved, inSociety).location).toEqual({
      areaId: "a1",
      newSociety: { name: "Palm Grove Phase 2", kind: "GATED_COMMUNITY" },
    })
  })

  it("uses the add-home step 1 checks", () => {
    const draft = homeEditDraft(inSociety)
    expect(checkHomeEdit(draft, inSociety)).toEqual({})
    expect(
      checkHomeEdit({ ...draft, pincode: "41104", unitLabel: " ", homeSize: "OTHER" }, inSociety),
    ).toEqual({
      pincode: "Enter the 6-digit PIN code.",
      unitLabel: "Enter the flat or house number.",
      homeSizeOther: "Type the size, e.g. 2.5 BHK.",
    })
    const standalone = {
      ...draft,
      location: { ...draft.location, society: { kind: "none" as const } },
    }
    expect(checkHomeEdit(standalone, inSociety).buildingName).toBe("Enter the building name.")
  })

  it("maps the API's field errors to the form's fields", () => {
    expect(
      homeEditErrors([
        { field: "location.newSociety.name", message: "Too short." },
        { field: "pincode", message: "Not in Pune." },
        { field: "street", message: "Too long." },
      ]),
    ).toEqual({ society: "Too short.", pincode: "Not in Pune.", street: "Too long." })
  })
})
