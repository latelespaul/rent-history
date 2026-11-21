// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { ApiError } from "@/api/errors"
import { Badge, DemoBadge, VerifiedBadge } from "./badge"
import { Icon } from "./icon"
import { ICON_PATHS } from "./icon-paths.gen"
import { ShieldCheckIcon, StarIcon } from "./icons.gen"
import { Notice } from "./notice"
import { EmptyState, ErrorState } from "./states"

afterEach(cleanup)

describe("Icon", () => {
  it("draws the set's paths, stroke only, decorative by default", () => {
    const { container } = render(<Icon name="tenancy" />)
    const svg = container.querySelector("svg")!
    expect(svg.getAttribute("aria-hidden")).toBe("true")
    expect(svg.getAttribute("stroke")).toBe("currentColor")
    expect(svg.getAttribute("fill")).toBe("none")
    expect(svg.getAttribute("stroke-width")).toBe("1.75")
    expect(svg.getAttribute("width")).toBe("20")
    expect([...svg.querySelectorAll("path")].map((p) => p.getAttribute("d"))).toEqual(
      ICON_PATHS.tenancy,
    )
  })

  it("is a labelled image when given a label", () => {
    render(<StarIcon label="Rating" size={16} />)
    const img = screen.getByRole("img", { name: "Rating" })
    expect(img.getAttribute("width")).toBe("16")
    expect(img.getAttribute("data-icon")).toBe("star")
  })
})

describe("badges", () => {
  it('"ID verified" carries the shield-check icon and a word', () => {
    const { container } = render(<VerifiedBadge />)
    expect(screen.getByText("ID verified")).toBeTruthy()
    expect(container.querySelector('svg[data-icon="shield-check"]')).toBeTruthy()
  })

  it('"Demo data" uses the demo tone', () => {
    render(<DemoBadge />)
    expect(screen.getByText("Demo data").className).toContain("bg-warn-100")
  })

  it("maps role and state variants to their 600-on-100 pairs", () => {
    render(
      <>
        <Badge variant="tenant">Tenant</Badge>
        <Badge variant="landlord">Landlord</Badge>
        <Badge>Ended</Badge>
      </>,
    )
    expect(screen.getByText("Tenant").className).toContain("text-tenant-600")
    expect(screen.getByText("Landlord").className).toContain("bg-landlord-100")
    expect(screen.getByText("Ended").className).toContain("bg-sunk")
  })
})

describe("Notice", () => {
  it("announces danger notices and shows the tone icon", () => {
    const { container } = render(<Notice tone="danger">Not approved</Notice>)
    expect(screen.getByRole("alert").textContent).toBe("Not approved")
    expect(container.querySelector('svg[data-icon="alert"]')).toBeTruthy()
  })

  it("keeps info notices quiet", () => {
    render(<Notice icon="lock">Flat number visible only to the landlord and tenants</Notice>)
    expect(screen.queryByRole("alert")).toBeNull()
  })
})

describe("EmptyState", () => {
  it("says what is missing and offers the next step", () => {
    render(
      <EmptyState
        title="No properties yet"
        description="Nothing is listed here."
        action={<button>Search</button>}
      />,
    )
    const status = screen.getByRole("status")
    expect(status.textContent).toContain("No properties yet")
    expect(status.textContent).toContain("Nothing is listed here.")
    expect(screen.getByRole("button", { name: "Search" })).toBeTruthy()
  })
})

describe("ErrorState", () => {
  it("explains a server failure in plain words, with its reference, and retries", async () => {
    const onRetry = vi.fn()
    render(
      <ErrorState
        error={new ApiError({ status: 503, code: "unavailable", traceId: "abc123" })}
        onRetry={onRetry}
      />,
    )
    const alert = screen.getByRole("alert")
    expect(alert.textContent).toContain("Something went wrong")
    expect(alert.textContent).toContain("The server had a problem")
    expect(alert.textContent).toContain("Reference: abc123")
    await userEvent.click(screen.getByRole("button", { name: "Try again" }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it("names a network failure and shows the API's own detail otherwise", () => {
    render(<ErrorState error={new ApiError({ status: 0, code: "network_error" })} />)
    expect(screen.getByRole("alert").textContent).toContain("could not reach the server")
    cleanup()

    render(
      <ErrorState
        title="Not allowed"
        error={
          new ApiError({ status: 403, code: "approval_required", detail: "Wait for approval." })
        }
      />,
    )
    expect(screen.getByRole("alert").textContent).toContain("Wait for approval.")
    expect(screen.queryByRole("button")).toBeNull()
  })
})

it("exports one named component per icon", () => {
  expect(ShieldCheckIcon.displayName).toBe("Icon(shield-check)")
})
