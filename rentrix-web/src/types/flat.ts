import type { AddressDto } from "./common"

export type PropertyType = "APARTMENT" | "INDEPENDENT_HOUSE" | "PG" | "ROOM" | "STUDIO" | "VILLA"

/**
 * Lighter shape returned by GET /flats (list view).
 * Address fields are flattened; description/timestamps are omitted.
 */
export interface FlatSummary {
  id: number
  rent: number
  numberOfRooms: number
  area?: number
  furnished?: boolean
  bathrooms?: number
  parking?: boolean
  availableFrom?: string
  propertyType: PropertyType
  available: boolean

  // Flattened address bits
  addressLine?: string
  city?: string
  state?: string

  // Ratings (null until v0.1.2 batch compute)
  averageRating?: number | null
  reviewCount?: number | null
}

/**
 * Full shape returned by GET /flats/{id} (detail view).
 */
export interface Flat {
  id: number
  rent: number
  numberOfRooms: number
  area?: number
  floorNumber?: number
  totalFloors?: number
  furnished?: boolean
  bathrooms?: number
  parking?: boolean
  availableFrom?: string
  propertyType: PropertyType
  description?: string
  available: boolean

  address: AddressDto

  ownerId?: number
  ownerName?: string

  averageRating?: number | null
  reviewCount?: number | null

  createdAt: string
  updatedAt: string
}

export interface FlatFilters {
  city?: string
  state?: string
  minRent?: number
  maxRent?: number
  minRooms?: number
  maxRooms?: number
  furnished?: boolean
  parking?: boolean
  propertyType?: PropertyType
  available?: boolean
  q?: string
  page?: number
  size?: number
  sort?: string
}

export interface CreateFlatRequest {
  rent: number
  numberOfRooms: number
  area?: number
  floorNumber?: number
  totalFloors?: number
  furnished?: boolean
  bathrooms?: number
  parking?: boolean
  availableFrom?: string
  propertyType: PropertyType
  description?: string
  available?: boolean
  address: AddressDto
}

export type UpdateFlatRequest = Partial<CreateFlatRequest>