// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { ApiError } from "@/api/errors"
import type { ProblemDetails } from "@/types/api"
import { ActionError } from "./ApprovalPrompt"

const overlap = (conflict: ProblemDetails["conflict"]) =>
  new ApiError({
    status: 409,
    code: "tenancy_overlap",
    detail: "This home already has a current tenancy since 2024-04.",
    conflict,
  })

function renderError(error: unknown, startDate?: string) {
  render(
    <MemoryRouter>
      <ActionError error={error} startDate={startDate} />
    </MemoryRouter>,
  )
}

afterEach(cleanup)

describe("ActionError on 409 tenancy_overlap", () => {
  it("names the clashing period and lets the landlord end the current tenancy first", () => {
    renderError(overlap({ from: "2024-04", status: "ACTIVE", tenancyId: "t-1" }))

    expect(screen.getByText("This home already has a tenancy for these dates")).toBeTruthy()
    expect(screen.getByText(/A confirmed tenancy runs Apr 2024 – now\./)).toBeTruthy()
    const link = screen.getByRole("link", { name: "End the current tenancy first" })
    expect(link.getAttribute("href")).toBe("/tenancies/t-1")
  })

  it("offers no end link to someone who is not that tenancy's landlord", () => {
    renderError(overlap({ from: "2024-04", status: "ACTIVE" }))

    expect(screen.getByText(/Apr 2024 – now/)).toBeTruthy()
    expect(screen.queryByRole("link")).toBeNull()
  })

  it("offers no end link when the clash is with a tenancy that already ended", () => {
    renderError(overlap({ from: "2023-01", to: "2024-12", status: "ENDED", tenancyId: "t-2" }))

    expect(screen.getByText(/Jan 2023 – Dec 2024/)).toBeTruthy()
    expect(screen.queryByRole("link")).toBeNull()
  })

  it("asks an earlier range to end before the clashing tenancy starts", () => {
    renderError(overlap({ from: "2023-01", to: "2024-12", status: "ENDED" }), "2022-06-01")

    expect(screen.getByText(/so the new one must end before Jan 2023\./)).toBeTruthy()
  })

  it("asks a later or inside range to start after the clashing tenancy ends", () => {
    renderError(overlap({ from: "2023-01", to: "2024-12", status: "ENDED" }), "2024-03-01")

    expect(screen.getByText(/so the new one must start after Dec 2024\./)).toBeTruthy()
  })

  it("names both ways out when the asked start is not known", () => {
    renderError(overlap({ from: "2023-01", to: "2024-12", status: "ENDED" }))

    expect(screen.getByText(/must end before Jan 2023 or start after Dec 2024\./)).toBeTruthy()
  })

  it("shows the server's message for a same-tenant overlap that names no home tenancy", () => {
    renderError(
      new ApiError({
        status: 409,
        code: "tenancy_overlap",
        detail: "This tenant already has a tenancy on this home for overlapping dates.",
      }),
    )

    expect(
      screen.getByText("This tenant already has a tenancy on this home for overlapping dates."),
    ).toBeTruthy()
    expect(screen.queryByText("This home already has a tenancy for these dates")).toBeNull()
  })
})
