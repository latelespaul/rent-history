import type {
  AddHomeInput,
  AddHomeResult,
  PropertyCard,
  PropertyReview,
  ReviewInput,
} from "@/types/api"
import { api } from "./client"

/** What "Is it this one?" searches by (D-138): PIN code, society and flat number. */
export interface HomeMatchQuery {
  city: string
  pincode: string
  society?: string
  societyName?: string
  unit: string
}

/** `GET /me/homes/matches`: published homes that look like the one being added. */
export function fetchHomeMatches(query: HomeMatchQuery, signal?: AbortSignal) {
  return api.get<PropertyCard[]>("/me/homes/matches", { query: { ...query }, signal })
}

/** `POST /me/homes`: the home, the caller's tenancy and rent, and the owner's contact (D-138). */
export function addHome(body: AddHomeInput): Promise<AddHomeResult> {
  return api.post<AddHomeResult>("/me/homes", body)
}

/** `POST /tenancies/{id}/reviews` (D-27): published at once, without the green tick (D-132). */
export function writeReview(tenancyId: string, body: ReviewInput): Promise<PropertyReview> {
  return api.post<PropertyReview>(`/tenancies/${encodeURIComponent(tenancyId)}/reviews`, body)
}
