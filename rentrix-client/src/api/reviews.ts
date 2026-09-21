import type { CreateReviewRequest, Page, Review, ReviewStatus } from "@/types"
import { api } from "./client"
import { USE_MOCKS } from "./config"
import {
  mockCreateReview,
  mockListReviews,
  mockModerateReview,
  mockMyReviews,
  mockPendingReviews,
} from "./mock/reviews.mock"

export const reviewsApi = {
  listByFlat: (flatId: number, page = 0, size = 10): Promise<Page<Review>> =>
    USE_MOCKS
      ? mockListReviews(flatId, page, size)
      : api.get(`/flats/${flatId}/reviews`, { params: { page, size } }).then((r) => r.data),

  create: (flatId: number, req: CreateReviewRequest): Promise<Review> =>
    USE_MOCKS
      ? mockCreateReview(flatId, req)
      : api.post(`/flats/${flatId}/reviews`, req).then((r) => r.data),

  mine: (): Promise<Page<Review>> =>
    USE_MOCKS ? mockMyReviews() : api.get("/users/me/reviews").then((r) => r.data),

  pending: (): Promise<Page<Review>> =>
    USE_MOCKS
      ? mockPendingReviews()
      : api.get("/admin/reviews", { params: { status: "PENDING" } }).then((r) => r.data),

  moderate: (id: number, status: Exclude<ReviewStatus, "PENDING">): Promise<Review> =>
    USE_MOCKS
      ? mockModerateReview(id, status)
      : api.patch(`/admin/reviews/${id}`, { status }).then((r) => r.data),
}
