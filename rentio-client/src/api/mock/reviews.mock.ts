import type { CreateReviewRequest, Page, Review } from "@/types"
import { delay } from "./delay"
import { mockReviews } from "./db"

export async function mockListReviews(flatId: number, page = 0, size = 10): Promise<Page<Review>> {
  await delay()
  const all = mockReviews
    .filter((r) => r.flatId === flatId)
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
  const newReview: Review = {
    id: mockReviews.length + 1,
    userId: 1,
    userName: "You",
    flatId,
    title: req.title,
    content: req.content,
    rating: req.rating,
    reviewDate: new Date().toISOString().slice(0, 10),
  }
  mockReviews.push(newReview)
  return newReview
}

export async function mockMyReviews(): Promise<Page<Review>> {
  await delay()
  const mine = mockReviews.filter((r) => r.userId === 1)
  return {
    content: mine,
    totalElements: mine.length,
    totalPages: 1,
    number: 0,
    size: mine.length,
  }
}
