import { describe, expect, it } from "vitest"
import { listedReviewCount } from "./labels"

describe("the count beside a review list", () => {
  it("is the rating's verified count until the list loads", () => {
    expect(listedReviewCount(undefined, 0)).toBe("0 reviews")
    expect(listedReviewCount(undefined, 1)).toBe("1 review")
  })

  it("adds how many are verified when some listed reviews are not", () => {
    expect(listedReviewCount(1, 0)).toBe("1 review · 0 verified")
    expect(listedReviewCount(4, 3)).toBe("4 reviews · 3 verified")
  })

  it("stays plain when every listed review is verified", () => {
    expect(listedReviewCount(3, 3)).toBe("3 reviews")
    expect(listedReviewCount(0, 0)).toBe("0 reviews")
  })
})
