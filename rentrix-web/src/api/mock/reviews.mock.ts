import type { CreateReviewRequest, Page, Review, ReviewStatus } from "@/types"
import { delay } from "./delay"
import { mockFlats, mockReviews } from "./db"

export async function mockListReviews(flatId: number, page = 0, size = 10): Promise<Page<Review>> {
  await delay()
  // Only show APPROVED reviews on the public flat page
  const all = mockReviews
    .filter((r) => r.flatId === flatId && r.status === "APPROVED")
    .sort((a, b) => b.reviewDate.localeCompare(a.reviewDate))

  const start = page * size
  return {
    content: all.slice(start, start + size),
    totalElements: all.length,
    totalPages: Math.ceil(all.length / size),
    number: page,
    size,
  }
}

export async function mockCreateReview(flatId: number, req: CreateReviewRequest): Promise<Review> {
  await delay()
  const flat = mockFlats.find((f) => f.id === flatId)
  const newReview: Review = {
    id: mockReviews.length + 1,
    userId: 1,
    userName: "You",
    flatId,
    flatAddress: flat?.address,
    title: req.title,
    content: req.content,
    rating: req.rating,
    reviewDate: new Date().toISOString().slice(0, 10),
    status: "PENDING", // goes to moderation
  }
  mockReviews.push(newReview)
  return newReview
}

export async function mockMyReviews(): Promise<Page<Review>> {
  await delay()
  const mine = mockReviews
    .filter((r) => r.userId === 1)
    .sort((a, b) => b.reviewDate.localeCompare(a.reviewDate))
  return {
    content: mine,
    totalElements: mine.length,
    totalPages: 1,
    number: 0,
    size: mine.length,
  }
}

export async function mockPendingReviews(): Promise<Page<Review>> {
  await delay()
  const pending = mockReviews
    .filter((r) => r.status === "PENDING")
    .sort((a, b) => b.reviewDate.localeCompare(a.reviewDate))
  return {
    content: pending,
    totalElements: pending.length,
    totalPages: 1,
    number: 0,
    size: pending.length,
  }
}

export async function mockModerateReview(
  id: number,
  status: Exclude<ReviewStatus, "PENDING">,
): Promise<Review> {
  await delay()
  const review = mockReviews.find((r) => r.id === id)
  if (!review) {
    throw { response: { status: 404, data: { message: "Review not found" } } }
  }
  review.status = status
  return review
}
