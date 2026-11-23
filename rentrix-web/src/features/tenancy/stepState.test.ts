import { describe, expect, it } from "vitest"
import type { Tenancy } from "@/types/api"
import { agreementStep, rejectionText, reviewStep, stepsLeft, stepsLeftLabel } from "./stepState"

const base = {
  id: "t1",
  myRole: "TENANT",
  status: "ACTIVE",
  startDate: "2026-01-01",
  endDate: null,
  verificationStatus: "UNVERIFIED",
  verificationReason: null,
  agreement: null,
} as unknown as Tenancy

const summary = (status: "DRAFT" | "SUBMITTED" | "DECIDED", uploadedByMe = true) => ({
  id: "a1",
  status,
  uploadedByMe,
  fileAvailable: true,
  uploadedAt: "2026-11-23T00:00:00Z",
})

describe("the two optional next steps", () => {
  it("follows the agreement from upload to the team's decision", () => {
    expect(agreementStep(base)).toEqual({ state: "todo" })
    expect(agreementStep({ ...base, agreement: summary("DRAFT") })).toEqual({ state: "draft" })
    // Another party's unconfirmed draft does not count as uploaded for this viewer.
    expect(agreementStep({ ...base, agreement: summary("DRAFT", false) })).toEqual({
      state: "todo",
    })
    expect(agreementStep({ ...base, agreement: summary("SUBMITTED") })).toEqual({
      state: "awaiting",
      byMe: true,
    })
    expect(
      agreementStep({ ...base, agreement: summary("DECIDED"), verificationStatus: "VERIFIED" }),
    ).toEqual({ state: "verified" })
    expect(
      agreementStep({
        ...base,
        agreement: summary("DECIDED"),
        verificationStatus: "REJECTED",
        verificationReason: "Unreadable",
      }),
    ).toEqual({ state: "rejected", reason: "Unreadable" })
    // A new upload after a rejection waits for the team again.
    expect(
      agreementStep({ ...base, agreement: summary("SUBMITTED"), verificationStatus: "REJECTED" }),
    ).toEqual({ state: "awaiting", byMe: true })
  })

  it("offers the review while its window is open and nothing is written", () => {
    const task = [{ tenancyId: "t1", direction: "TENANT_TO_PROPERTY" as const }]
    expect(reviewStep(base, task, "2026-11-23")).toEqual({ state: "todo" })
    expect(reviewStep({ ...base, reviewed: true }, [], "2026-11-23")).toEqual({ state: "done" })
    // Opening day: the browser's date already opens the window but the server's (UTC) does not list
    // the task yet. Nothing was written, so it is not "done".
    expect(reviewStep({ ...base, startDate: "2026-09-24" }, [], "2026-11-23")).toEqual({
      state: "none",
    })
    expect(reviewStep(base, undefined, "2026-11-23")).toEqual({ state: "none" })
    // D-39: the review opens 60 days after moving in.
    expect(reviewStep({ ...base, startDate: "2026-11-01" }, task, "2026-11-23")).toEqual({
      state: "later",
      opens: "2026-12-31",
    })
    expect(reviewStep({ ...base, myRole: "LANDLORD" }, task, "2026-11-23")).toEqual({
      state: "none",
    })
  })

  it("counts what is left", () => {
    expect(stepsLeft({ state: "todo" }, { state: "todo" })).toBe(2)
    expect(stepsLeft({ state: "awaiting", byMe: true }, { state: "todo" })).toBe(1)
    expect(stepsLeft({ state: "verified" }, { state: "done" })).toBe(0)
    expect(stepsLeft({ state: "todo" }, { state: "none" })).toBe(1)
    // A review that opens later is not a step the caller can act on yet.
    expect(stepsLeft({ state: "todo" }, { state: "later", opens: "2026-12-31" })).toBe(1)
    expect(
      stepsLeft({ state: "awaiting", byMe: true }, { state: "later", opens: "2026-12-31" }),
    ).toBe(0)
    expect(stepsLeftLabel(2)).toBe("2 optional steps left")
    expect(stepsLeftLabel(1)).toBe("1 optional step left")
    expect(stepsLeftLabel(0)).toBe("All optional steps done")
  })

  it("says the team's reason in plain words", () => {
    expect(rejectionText("CANNOT_CONFIRM: The pages are unreadable.")).toBe(
      "The team could not confirm the tenancy from the agreement: The pages are unreadable.",
    )
    expect(rejectionText("DETAILS_WRONG")).toBe("Some details do not match the agreement.")
    expect(rejectionText("Free text from an older decision")).toBe(
      "Free text from an older decision",
    )
    expect(rejectionText(null)).toBe("The team could not verify it.")
  })
})
