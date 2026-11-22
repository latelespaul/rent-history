import { describe, expect, it } from "vitest"
import { entry } from "@/test/verification"
import { doneSteps, nextStep, progressOf } from "./progress"

const phoneDone = { phone: "DONE" } as const

describe("account-menu progress row (D-121)", () => {
  it("asks for the phone first, whatever the application status", () => {
    expect(progressOf([entry({ status: "PENDING" })])).toMatchObject({
      kind: "phone",
      label: "Verify your phone to post",
      to: "/verify/phone",
    })
    expect(progressOf([entry({ status: "APPROVED" })])?.kind).toBe("phone")
  })

  it("counts the steps of a draft: profile, phone and rental when done; ID when done or pending", () => {
    expect(progressOf([entry({ steps: phoneDone })])).toMatchObject({
      kind: "draft",
      label: "Get verified: 2 of 4 steps",
      to: "/verify/id",
      hint: "Next: ID (optional)",
    })
    const linkedinPending = entry({ steps: { ...phoneDone, linkedin: "PENDING" } })
    expect(progressOf([linkedinPending])?.label).toBe("Get verified: 3 of 4 steps")
    const all = entry({ steps: { ...phoneDone, identity: "DONE", rentalAgreement: "DONE" } })
    expect(progressOf([all])).toMatchObject({
      label: "Get verified: 4 of 4 steps",
      to: "/verify/submit",
    })
  })

  it("says Waiting for review while pending and Changes needed when rejected", () => {
    expect(progressOf([entry({ status: "PENDING", steps: phoneDone })])).toMatchObject({
      kind: "pending",
      label: "Waiting for review",
    })
    expect(progressOf([entry({ status: "REJECTED", steps: phoneDone })])).toMatchObject({
      kind: "rejected",
      label: "Changes needed",
    })
  })

  it("shows the role that needs the user first: rejected, then draft, then pending", () => {
    const tenant = entry({ role: "TENANT", status: "PENDING", steps: phoneDone })
    const landlord = entry({ role: "LANDLORD", status: "DRAFT", steps: phoneDone })
    expect(progressOf([tenant, landlord])).toMatchObject({ kind: "draft", role: "LANDLORD" })
    const rejected = entry({ role: "LANDLORD", status: "REJECTED", steps: phoneDone })
    expect(progressOf([tenant, rejected])).toMatchObject({ kind: "rejected", role: "LANDLORD" })
  })

  it("is hidden when every role is approved with a verified phone, or there is no role", () => {
    expect(progressOf([entry({ status: "APPROVED", steps: phoneDone })])).toBeNull()
    expect(progressOf([])).toBeNull()
    expect(progressOf(undefined)).toBeNull()
  })
})

describe("stepper resume point (D-120)", () => {
  it("is the first required step still open, then the first optional one", () => {
    expect(nextStep(entry({ steps: { profile: "MISSING" } }))?.key).toBe("profile")
    expect(nextStep(entry())?.key).toBe("phone")
    expect(nextStep(entry({ steps: { ...phoneDone, identity: "PENDING" } }))?.key).toBe("rental")
    expect(
      nextStep(entry({ steps: { ...phoneDone, identity: "DONE", rentalAgreement: "DONE" } })),
    ).toBeNull()
  })

  it("never counts a rejected ID as done", () => {
    expect(doneSteps(entry({ steps: { identity: "REJECTED" } })).id).toBe(false)
  })
})
