// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest"
import {
  clearDraft,
  draftKey,
  emptyDraft,
  HOME_CRITERIA,
  LANDLORD_CRITERIA,
  loadDraft,
  missingOverall,
  overallOf,
  recommendShare,
  saveDraft,
  sectionOfField,
  sectionsFor,
  TENANT_CRITERIA,
  toRequest,
  workedOutOverall,
} from "./form"

afterEach(() => localStorage.clear())

describe("review form (D-157)", () => {
  it("asks the captain's nine home and five landlord parameters", () => {
    expect(HOME_CRITERIA.map((c) => c.key)).toEqual([
      "WATER_SUPPLY",
      "POWER_BACKUP",
      "MAINTENANCE",
      "CLEANLINESS",
      "SECURITY",
      "PARKING",
      "NOISE",
      "NEIGHBOURHOOD",
      "VALUE_FOR_RENT",
    ])
    expect(LANDLORD_CRITERIA.map((c) => c.key)).toEqual([
      "RESPONSIVENESS",
      "DEPOSIT_RETURN",
      "TRANSPARENCY",
      "PRIVACY",
      "BEHAVIOUR",
    ])
  })

  it("works the overall rating out as the rounded-half-up average, until the user picks one", () => {
    expect(workedOutOverall({})).toBeNull()
    expect(workedOutOverall({ WATER_SUPPLY: 4, NOISE: 3 })).toBe(4) // 3.5 rounds up
    expect(workedOutOverall({ WATER_SUPPLY: 2, NOISE: 3, PARKING: 3 })).toBe(3) // 2.67
    expect(workedOutOverall({ WATER_SUPPLY: 1, NOISE: 1, PARKING: 2 })).toBe(1) // 1.33
    const part = { ...emptyDraft().home, ratings: { WATER_SUPPLY: 5, NOISE: 4 } }
    expect(overallOf(part)).toBe(5)
    expect(overallOf({ ...part, ownOverall: 2 })).toBe(2)
  })

  it("shows the landlord screen only when the landlord can be reviewed", () => {
    expect(sectionsFor({ home: true, landlord: true })).toEqual([
      "stay",
      "home",
      "landlord",
      "overall",
      "check",
    ])
    expect(sectionsFor({ home: true, landlord: false })).toEqual([
      "stay",
      "home",
      "overall",
      "check",
    ])
  })

  it("sends every rated parameter, the overall, and pros and cons with the home review", () => {
    const draft = emptyDraft()
    draft.home = {
      ratings: { PARKING: 2, NOISE: 4 },
      comment: "  ",
      ownOverall: null,
      recommend: true,
    }
    draft.landlord = {
      ratings: { PRIVACY: 5 },
      comment: " Fair person. ",
      ownOverall: 4,
      recommend: null,
    }
    draft.pros = " Quiet lane "
    draft.cons = ""
    expect(toRequest(draft, { home: true, landlord: true })).toEqual({
      home: {
        ratingOverall: 3,
        criteria: { PARKING: 2, NOISE: 4 },
        wouldRecommend: true,
        pros: "Quiet lane",
      },
      landlord: { ratingOverall: 4, criteria: { PRIVACY: 5 }, body: "Fair person." },
    })
    expect(toRequest(draft, { home: false, landlord: true }).landlord?.pros).toBe("Quiet lane")
  })

  it("needs an overall rating for each part, and opens the screen of a server field error", () => {
    const draft = emptyDraft()
    expect(missingOverall(draft, { home: true, landlord: true })).toEqual({
      "home.ratingOverall": "Pick an overall rating for the home.",
      "landlord.ratingOverall": "Pick an overall rating for the landlord.",
    })
    draft.home.ratings = { NOISE: 3 }
    expect(missingOverall(draft, { home: true, landlord: false })).toEqual({})
    expect(sectionOfField("home.criteria.PARKING")).toBe("home")
    expect(sectionOfField("landlord.body")).toBe("landlord")
    expect(sectionOfField("landlord.ratingOverall")).toBe("overall")
    expect(sectionOfField("home.pros")).toBe("overall")
  })

  it("keeps the draft on this device per tenancy", () => {
    expect(loadDraft("t1")).toBeNull()
    const draft = { ...emptyDraft(), step: 2, pros: "Lift works" }
    expect(saveDraft("t1", draft)).toBe(true)
    expect(loadDraft("t1")).toEqual(draft)
    expect(loadDraft("t2")).toBeNull()
    clearDraft("t1")
    expect(loadDraft("t1")).toBeNull()
  })

  it("shows the recommend share from 3 answers (D-52)", () => {
    expect(recommendShare(undefined)).toBeNull()
    expect(recommendShare({ answered: 2, yes: 2 })).toBeNull()
    expect(recommendShare({ answered: 3, yes: 2 })).toBe("67% would recommend")
  })

  it("gives the owner one part, the tenant's five questions, with no pros or cons (D-170)", () => {
    expect(TENANT_CRITERIA.map((c) => c.key)).toEqual([
      "RENT_ON_TIME",
      "CARE_OF_HOME",
      "COMMUNICATION",
      "HOUSE_RULES",
      "MOVE_OUT",
    ])
    const parts = { home: false, landlord: false, tenant: true }
    expect(sectionsFor(parts)).toEqual(["stay", "tenant", "overall", "check"])

    const draft = emptyDraft()
    expect(missingOverall(draft, parts)).toEqual({
      "tenant.ratingOverall": "Pick an overall rating for the tenant.",
    })
    draft.tenant = {
      ratings: { RENT_ON_TIME: 5, MOVE_OUT: 4 },
      comment: " Always paid on time. ",
      ownOverall: null,
      recommend: true,
    }
    draft.pros = "Never sent"
    expect(toRequest(draft, parts)).toEqual({
      tenant: {
        ratingOverall: 5,
        criteria: { RENT_ON_TIME: 5, MOVE_OUT: 4 },
        wouldRecommend: true,
        body: "Always paid on time.",
      },
    })
    expect(sectionOfField("tenant.criteria.MOVE_OUT")).toBe("tenant")
    expect(sectionOfField("tenant.body")).toBe("tenant")
    expect(sectionOfField("tenant.ratingOverall")).toBe("overall")
  })

  it("keeps the owner's draft apart from the tenant's on one device", () => {
    expect(draftKey("t1")).toBe("t1")
    expect(draftKey("t1", "LANDLORD")).toBe("t1:LANDLORD")
    const owner = { ...emptyDraft(), step: 2 }
    saveDraft(draftKey("t1", "LANDLORD"), owner)
    expect(localStorage.getItem("rx-review-draft:t1:LANDLORD")).not.toBeNull()
    expect(loadDraft(draftKey("t1"))).toBeNull()
    expect(loadDraft(draftKey("t1", "LANDLORD"))?.step).toBe(2)
  })
})
