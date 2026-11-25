// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { card } from "@/test/properties"
import type { SocietyGroup } from "@/types/api"
import { SocietyResult } from "./SocietyResult"
import { flatsByTower, rentRangeLabel } from "./society"

afterEach(cleanup)

const group = (overrides: Partial<SocietyGroup> = {}): SocietyGroup => ({
  id: "s1",
  name: "Prestige Lakeside",
  area: { id: "a1", name: "Whitefield" },
  city: { code: "BLR", name: "Bangalore" },
  pending: false,
  flats: 3,
  listedReviews: 5,
  rating: { reviewCount: 4, average: 4.5 },
  ...overrides,
})

describe("a society card in the search results (D-166)", () => {
  it("names the society, its place, flat count and reviews, and opens the society page", () => {
    render(
      <MemoryRouter>
        <SocietyResult society={group({ rentRange: { min: 25000, max: 40000 } })} />
      </MemoryRouter>,
    )
    expect(screen.getByRole("link", { name: "Prestige Lakeside" }).getAttribute("href")).toBe(
      "/s/s1",
    )
    expect(screen.getByText("Whitefield, Bangalore")).toBeTruthy()
    expect(screen.getByTestId("society-flats").textContent).toBe("3 flats")
    expect(screen.getByTestId("card-reviews").textContent).toBe("4.5verified · 5 reviews")
    expect(screen.getByTestId("society-rent").textContent).toBe("₹25,000 – ₹40,000 a month")
  })

  it("shows no rent range when the API sent none (signed out, D-56)", () => {
    render(
      <MemoryRouter>
        <SocietyResult society={group()} />
      </MemoryRouter>,
    )
    expect(screen.queryByTestId("society-rent")).toBeNull()
  })

  it("names one amount when every home pays the same", () => {
    expect(rentRangeLabel({ min: 30000, max: 30000 })).toBe("₹30,000 a month")
  })
})

describe("the society page's flats by tower (D-166)", () => {
  const tower = (id: string, name: string) => ({ id, name, status: "ACTIVE" as const })
  it("groups flats in the society's tower order and lists flats outside a tower last", () => {
    const flats = [
      card({ id: "1", location: { area: tower("a", "Area"), tower: tower("tb", "Tower B") } }),
      card({ id: "2", location: { area: tower("a", "Area"), tower: tower("ta", "Tower A") } }),
      card({ id: "3", location: { area: tower("a", "Area") } }),
      card({ id: "4", location: { area: tower("a", "Area"), tower: tower("tb", "Tower B") } }),
    ]
    const groups = flatsByTower(
      [
        { id: "ta", name: "Tower A" },
        { id: "tb", name: "Tower B" },
        { id: "tc", name: "Tower C" },
      ],
      flats,
    )
    expect(groups.map((g) => [g.name, g.flats.map((f) => f.id)])).toEqual([
      ["Tower A", ["2"]],
      ["Tower B", ["1", "4"]],
      ["Other homes", ["3"]],
    ])
  })
})
