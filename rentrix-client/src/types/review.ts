export type ReviewStatus = "PENDING" | "APPROVED" | "REJECTED"

export interface Review {
  id: number
  userId: number
  userName: string
  flatId: number
  flatAddress?: string // for MyReviews + admin display
  title: string
  content: string
  rating: number
  reviewDate: string
  status: ReviewStatus
}

export interface CreateReviewRequest {
  title: string
  content: string
  rating: number
}
