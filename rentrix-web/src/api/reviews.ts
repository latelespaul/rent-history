import type { Page, PropertyReview, TenancyParty } from "@/types/api"
import { api } from "./client"

export const myReviewKeys = {
  all: ["my-reviews"] as const,
  written: (viewer: string | null, as: TenancyParty) =>
    ["my-reviews", "written", viewer, as] as const,
  received: (viewer: string | null, as: TenancyParty) =>
    ["my-reviews", "received", viewer, as] as const,
}

/** `GET /me/reviews/written?as=`: the caller's own reviews from one side, every state but withdrawn. */
export function fetchMyWrittenReviews(
  as: TenancyParty,
  signal?: AbortSignal,
): Promise<Page<PropertyReview>> {
  return api.get<Page<PropertyReview>>("/me/reviews/written", {
    query: { as, page: 0, size: 50 },
    signal,
  })
}

/**
 * `GET /me/reviews/received?as=`: published reviews about the caller in that capacity, plus bare
 * `AWAITING_REVEAL` entries for the ones waiting for the caller's own review (D-46).
 */
export function fetchMyReceivedReviews(
  as: TenancyParty,
  signal?: AbortSignal,
): Promise<Page<PropertyReview>> {
  return api.get<Page<PropertyReview>>("/me/reviews/received", {
    query: { as, page: 0, size: 50 },
    signal,
  })
}
