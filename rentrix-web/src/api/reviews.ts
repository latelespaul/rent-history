import type { CreateReviewRequest, Page, Review, ReviewStatus } from "@/types"
import { api } from "./client"

export const reviewsApi = {
  listByFlat: (flatId: number, page = 0, size = 10): Promise<Page<Review>> =>
    api.get(`/flats/${flatId}/reviews`, { params: { page, size } }).then((r) => r.data),

  create: (flatId: number, req: CreateReviewRequest): Promise<Review> =>
    api.post(`/flats/${flatId}/reviews`, req).then((r) => r.data),

  mine: (): Promise<Page<Review>> => api.get("/users/me/reviews").then((r) => r.data),

  pending: (): Promise<Page<Review>> =>
    api.get("/admin/reviews", { params: { status: "PENDING" } }).then((r) => r.data),

  moderate: (id: number, status: Exclude<ReviewStatus, "PENDING">): Promise<Review> =>
    api.patch(`/admin/reviews/${id}`, { status }).then((r) => r.data),
}