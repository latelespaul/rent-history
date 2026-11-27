import type {
  Page,
  PropertyReview,
  RatingDetail,
  ReviewFormInput,
  ReviewFormResult,
  ReviewTask,
  TenancyParty,
  UserProfile,
} from "@/types/api"
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

export const reviewKeys = {
  tasks: ["reviews", "tasks"] as const,
  propertyRating: (id: string) => ["properties", "rating", id] as const,
  user: (id: string) => ["users", id] as const,
  userReviews: (id: string, as: TenancyParty = "LANDLORD") => ["users", id, "reviews", as] as const,
}

/** `POST /tenancies/{id}/review-form` (D-157): the home and the landlord review, both or neither. */
export function submitReviewForm(tenancyId: string, body: ReviewFormInput) {
  return api.post<ReviewFormResult>(`/tenancies/${encodeURIComponent(tenancyId)}/review-form`, body)
}

/** `GET /me/review-tasks`: the reviews the caller can write now (window open, not written). */
export function fetchReviewTasks(signal?: AbortSignal) {
  return api.get<ReviewTask[]>("/me/review-tasks", { signal })
}

/** `GET /properties/{id}/rating`: public, with the per-parameter averages (D-158). */
export function fetchPropertyRating(id: string, signal?: AbortSignal) {
  return api.get<RatingDetail>(`/properties/${encodeURIComponent(id)}/rating`, { signal })
}

/** `GET /users/{id}`: a person's profile (signed in; landlord D-55, tenant D-54 and D-175). */
export function fetchUserProfile(id: string, signal?: AbortSignal) {
  return api.get<UserProfile>(`/users/${encodeURIComponent(id)}`, { signal })
}

/**
 * `GET /users/{id}/reviews?as=`: the published reviews the person received as landlord, or as
 * tenant from their owners (only for the owners D-54 allows and admins, D-175).
 */
export function fetchUserReviews(id: string, as: TenancyParty = "LANDLORD", signal?: AbortSignal) {
  return api.get<Page<PropertyReview>>(`/users/${encodeURIComponent(id)}/reviews`, {
    query: { as, size: 50 },
    signal,
  })
}
