// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import type { PropertyCard } from "@/types/api"
import { PropertyFacts } from "./PropertyFacts"

type Facts = Parameters<typeof PropertyFacts>[0]["property"]

const card = (facts: Partial<PropertyCard>): Facts => ({
  propertyType: "APARTMENT",
  bedrooms: null,
  bathrooms: null,
  areaSqft: null,
  furnishing: null,
  parking: null,
  ...facts,
})

const shown = () => screen.getAllByRole("listitem").map((item) => item.textContent)

afterEach(cleanup)

describe("a card's size fact", () => {
  it("shows the chosen home size over the bedroom count", () => {
    render(<PropertyFacts property={card({ homeSize: "RK1", bedrooms: 0 })} />)
    expect(shown()).toEqual(["Apartment", "1 RK"])
  })

  it("shows the user's own words for Other", () => {
    render(<PropertyFacts property={card({ homeSize: "OTHER", homeSizeOther: "2.5 BHK" })} />)
    expect(shown()).toEqual(["Apartment", "2.5 BHK"])
  })

  it("falls back to the bedroom count when no size is set", () => {
    render(<PropertyFacts property={card({ bedrooms: 2 })} />)
    expect(shown()).toEqual(["Apartment", "2 BHK"])
  })

  it("does not repeat Studio for a studio", () => {
    render(
      <PropertyFacts
        property={card({ propertyType: "STUDIO", homeSize: "STUDIO", bedrooms: 0 })}
      />,
    )
    expect(shown()).toEqual(["Studio"])
  })
})
