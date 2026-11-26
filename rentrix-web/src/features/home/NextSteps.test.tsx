// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import type { Tenancy } from "@/types/api"
import { NextSteps } from "./NextSteps"

const tenancy = {
  id: "t1",
  myRole: "TENANT",
  status: "ACTIVE",
  startDate: "2026-01-01",
  endDate: null,
  evidenceLevel: null,
  reviewed: false,
} as Tenancy

function writeReview(accent?: boolean) {
  render(
    <MemoryRouter>
      <NextSteps tenancy={tenancy} accent={accent} />
    </MemoryRouter>,
  )
  return screen.getByRole("link", { name: "Write a review" })
}

describe("NextSteps' Write a review (DESIGN.md §4.1)", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date("2026-11-23T10:00:00Z"))
  })
  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  it("stays teal where it is not the screen's one primary action", () => {
    expect(writeReview().dataset.variant).toBe("default")
  })

  it("takes the accent when the screen asks for it", () => {
    const link = writeReview(true)
    expect(link.dataset.variant).toBe("accent")
    expect(link.getAttribute("href")).toBe("/tenancies/t1/review")
  })
})
