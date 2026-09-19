export interface Review {
  id: number
  userId: number
  userName: string
  flatId: number
  title: string
  content: string
  rating: number
  reviewDate: string
}

export interface CreateReviewRequest {
  title: string
  content: string
  rating: number
}
