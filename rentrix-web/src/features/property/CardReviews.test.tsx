// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import { CardReviews } from "./Rating"

afterEach(cleanup)

describe("a home card's review line (D-165)", () => {
  it("counts every listed review and shows the verified average when there is one", () => {
    render(<CardReviews rating={{ reviewCount: 3, average: 4.2 }} listed={5} />)
    expect(screen.getByTestId("card-reviews").textContent).toBe("4.2verified · 5 reviews")
  })

  it("says how many are verified while there is no average", () => {
    render(<CardReviews rating={{ reviewCount: 0, average: null }} listed={2} />)
    expect(screen.getByTestId("card-reviews").textContent).toBe("2 reviews · 0 verified")
    cleanup()
    render(<CardReviews rating={{ reviewCount: 0, average: null }} listed={0} />)
    expect(screen.getByTestId("card-reviews").textContent).toBe("No reviews yet")
  })
})
