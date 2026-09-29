export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ApiError {
  message: string
  status: number
  timestamp?: string
}
