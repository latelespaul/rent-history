import type {
  GroupedPage,
  Page,
  PropertyCard,
  PropertyDetail,
  PropertyInput,
  PropertyPatch,
  PropertyReview,
  RentHistory,
  RentHistoryRow,
} from "@/types/api"
import { api, type Query } from "./client"
import { isApiError } from "./errors"

/** The query `GET /properties` takes (§12.2); every field is optional. */
export interface PropertySearch {
  city?: string
  locality?: string
  /** Any of these property types (repeated `type=` params). */
  type?: string[]
  bedrooms?: number
  /** Home size (captain 2026-11-23 17:18), e.g. `BHK2`. */
  homeSize?: string[]
  minRating?: number
  q?: string
  /** One society's homes, and one tower of it (D-112): the society page's list. */
  society?: string
  tower?: string
  sort?: string
  /** Zero-based page number (D-59). */
  page?: number
  size?: number
}

export const propertyKeys = {
  all: ["properties"] as const,
  search: (search: PropertySearch) => ["properties", "search", search] as const,
  grouped: (search: PropertySearch, viewer: string | null) =>
    ["properties", "grouped", search, viewer] as const,
  localities: (city: string) => ["properties", "localities", city] as const,
  detail: (id: string, viewer: string | null) => ["properties", "detail", id, viewer] as const,
  rentHistory: (id: string, viewer: string | null) =>
    ["properties", "rent-history", id, viewer] as const,
  reviews: (id: string, viewer: string | null) => ["properties", "reviews", id, viewer] as const,
  mine: (viewer: string | null, page: number) => ["properties", "mine", viewer, page] as const,
}

/** `GET /properties`: published homes, D-59 envelope. */
export function searchProperties(
  search: PropertySearch,
  signal?: AbortSignal,
): Promise<Page<PropertyCard>> {
  return api.get<Page<PropertyCard>>("/properties", { query: search as Query, signal })
}

/**
 * `GET /properties/grouped` (D-166): the same filters, with a society that has 2+ matching homes
 * as one row. `society` and `tower` narrow to one society, as on its page.
 */
export function searchGrouped(search: PropertySearch, signal?: AbortSignal): Promise<GroupedPage> {
  return api.get<GroupedPage>("/properties/grouped", { query: search as Query, signal })
}

/** `GET /properties/{id}`: fields per D-56; more when the caller is signed in. */
export function fetchProperty(id: string, signal?: AbortSignal): Promise<PropertyDetail> {
  return api.get<PropertyDetail>(`/properties/${encodeURIComponent(id)}`, { signal })
}

/** The rows of a list and how many there are in all, across every page. */
export interface Listed<T> {
  items: T[]
  total: number
}

/** Rows and total of a list endpoint that may answer with a bare array or a D-59 page. */
function rowsOf<T>(body: T[] | Page<T> | undefined): Listed<T> {
  if (Array.isArray(body)) return { items: body, total: body.length }
  const items = body?.items ?? []
  return { items, total: body?.page?.totalItems ?? items.length }
}

/**
 * Lists the API may not serve yet (rent history comes with B5, reviews with B6). A `404` or
 * `405` means "not available", so the page shows its empty state instead of an error.
 */
async function optionalList<T>(path: string, signal?: AbortSignal): Promise<Listed<T> | null> {
  try {
    return rowsOf(await api.get<T[] | Page<T>>(path, { signal }))
  } catch (error) {
    if (isApiError(error) && (error.status === 404 || error.status === 405)) return null
    throw error
  }
}

/**
 * `GET /properties/{id}/rent-history` (D-26, signed-in only): the `rows` of the B5 body. Null
 * when the API does not serve it (404 / 405).
 */
export async function fetchRentHistory(
  id: string,
  signal?: AbortSignal,
): Promise<RentHistoryRow[] | null> {
  try {
    const body = await api.get<RentHistory>(`/properties/${encodeURIComponent(id)}/rent-history`, {
      signal,
    })
    return body?.rows ?? []
  } catch (error) {
    if (isApiError(error) && (error.status === 404 || error.status === 405)) return null
    throw error
  }
}

/** `GET /properties/{id}/reviews` (public, full text, D-161); null until the API serves it. */
export function fetchPropertyReviews(id: string, signal?: AbortSignal) {
  return optionalList<PropertyReview>(`/properties/${encodeURIComponent(id)}/reviews`, signal)
}

/** `POST /reviews/{id}/response`: the subject's one public reply (1–1000 characters, §5.7). */
export function respondToReview(id: string, body: string) {
  return api.post<PropertyReview>(`/reviews/${encodeURIComponent(id)}/response`, { body })
}

/** `GET /me/properties`: the landlord's own listings, every state, with unit labels. */
export function fetchMyProperties(
  page = 0,
  size = 50,
  signal?: AbortSignal,
): Promise<Page<PropertyDetail>> {
  return api.get<Page<PropertyDetail>>("/me/properties", { query: { page, size }, signal })
}

/** `POST /properties`: a new `DRAFT` (D-21). */
export function createProperty(body: PropertyInput): Promise<PropertyDetail> {
  return api.post<PropertyDetail>("/properties", body)
}

/** `PATCH /properties/{id}`: owner edits; location fields lock after submit (D-92). */
export function updateProperty(id: string, body: PropertyPatch): Promise<PropertyDetail> {
  return api.patch<PropertyDetail>(`/properties/${encodeURIComponent(id)}`, body)
}

/** `POST /properties/{id}/submit`: `DRAFT` / `REJECTED` → `PENDING_REVIEW` (D-92). */
export function submitProperty(id: string): Promise<PropertyDetail> {
  return api.post<PropertyDetail>(`/properties/${encodeURIComponent(id)}/submit`)
}

/** `DELETE /properties/{id}`: archives a published listing, removes one never published. */
export function deleteProperty(id: string): Promise<void> {
  return api.delete(`/properties/${encodeURIComponent(id)}`)
}

/**
 * `PATCH /reviews/{id}`: the author changes ratings and text at any time (D-156). The whole set
 * replaces the old values, so send the sub-ratings the form does not change as they were.
 */
export function updateReview(
  id: string,
  body: {
    ratingOverall: number
    ratingA?: number
    ratingB?: number
    ratingC?: number
    body: string
  },
): Promise<PropertyReview> {
  return api.patch<PropertyReview>(`/reviews/${encodeURIComponent(id)}`, body)
}

/** `DELETE /reviews/{id}`: the author withdraws their review, at any time (D-41). */
export function deleteReview(id: string): Promise<void> {
  return api.delete<void>(`/reviews/${encodeURIComponent(id)}`)
}
