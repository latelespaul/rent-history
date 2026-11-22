import type {
  Page,
  PropertyCard,
  PropertyDetail,
  PropertyReview,
  RentHistoryRow,
} from "@/types/api"
import { api, type Query } from "./client"
import { isApiError } from "./errors"

/** The query `GET /properties` takes (§12.2); every field is optional. */
export interface PropertySearch {
  city?: string
  locality?: string
  type?: string
  bedrooms?: number
  minRating?: number
  q?: string
  sort?: string
  /** Zero-based page number (D-59). */
  page?: number
  size?: number
}

export const propertyKeys = {
  all: ["properties"] as const,
  search: (search: PropertySearch) => ["properties", "search", search] as const,
  localities: (city: string) => ["properties", "localities", city] as const,
  detail: (id: string, viewer: string | null) => ["properties", "detail", id, viewer] as const,
  rentHistory: (id: string, viewer: string | null) =>
    ["properties", "rent-history", id, viewer] as const,
  reviews: (id: string, viewer: string | null) => ["properties", "reviews", id, viewer] as const,
}

/** `GET /properties`: published homes, D-59 envelope. */
export function searchProperties(
  search: PropertySearch,
  signal?: AbortSignal,
): Promise<Page<PropertyCard>> {
  return api.get<Page<PropertyCard>>("/properties", { query: search as Query, signal })
}

/** `GET /properties/{id}`: fields per D-56; more when the caller is signed in. */
export function fetchProperty(id: string, signal?: AbortSignal): Promise<PropertyDetail> {
  return api.get<PropertyDetail>(`/properties/${encodeURIComponent(id)}`, { signal })
}

/** Rows of a list endpoint that may answer with a bare array or a D-59 page. */
function rowsOf<T>(body: T[] | Page<T> | undefined): T[] {
  if (Array.isArray(body)) return body
  return body?.items ?? []
}

/**
 * Lists the API may not serve yet (rent history comes with B5, reviews with B6). A `404` or
 * `405` means "not available", so the page shows its empty state instead of an error.
 */
async function optionalList<T>(path: string, signal?: AbortSignal): Promise<T[] | null> {
  try {
    return rowsOf(await api.get<T[] | Page<T>>(path, { signal }))
  } catch (error) {
    if (isApiError(error) && (error.status === 404 || error.status === 405)) return null
    throw error
  }
}

/** `GET /properties/{id}/rent-history` (D-26, signed-in only); null until the API serves it. */
export function fetchRentHistory(id: string, signal?: AbortSignal) {
  return optionalList<RentHistoryRow>(`/properties/${encodeURIComponent(id)}/rent-history`, signal)
}

/** `GET /properties/{id}/reviews` (text only when signed in, D-56); null until the API serves it. */
export function fetchPropertyReviews(id: string, signal?: AbortSignal) {
  return optionalList<PropertyReview>(`/properties/${encodeURIComponent(id)}/reviews`, signal)
}
