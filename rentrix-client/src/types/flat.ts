export interface Flat {
  id: number
  address: string
  city: string
  state: string
  numberOfRooms: number
  area: number
  rent: number
  description: string
  isAvailable: boolean
  averageRating?: number
  reviewCount?: number
}

export interface FlatFilters {
  city?: string
  state?: string
  minRent?: number
  maxRent?: number
  numberOfRooms?: number
  available?: boolean
  page?: number
  size?: number
  sort?: string
}
