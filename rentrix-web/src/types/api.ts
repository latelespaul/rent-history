/**
 * API types the web builds against (D-61).
 *
 * `api.gen.ts` is generated from the backend OpenAPI snapshot in `openapi/openapi.json`
 * (`npm run gen:api`). The B1 backend only exposes `GET /cities` so far, so the shapes below
 * that the spec defines (SYSTEM_DESIGN.md §12-13) but no controller serves yet are written by hand.
 * TODO(B2+): refresh the snapshot (`npm run gen:api:fetch` against a dev API) and replace each
 * hand-written type with its generated `components["schemas"]` entry as the endpoints land.
 */
import type { components } from "./api.gen"

type Schemas = components["schemas"]

/** `GET /cities`: an active city (D-20, D-82). The list is data; the web never names a city. */
export type City = Required<Schemas["CityResponse"]>

/** Real counts for one active city inside `GET /stats` (D-03, D-85; hand-written until B4). */
export interface CityStats {
  /** City code, as in `City.code`. */
  city: string
  publishedProperties: number
  confirmedTenancies: number
  publishedReviews: number
}

/** `GET /stats`: real counts only, totals plus one row per active city (D-03). */
export interface Stats {
  publishedProperties: number
  confirmedTenancies: number
  publishedReviews: number
  cities: CityStats[]
}

/** One invalid input field inside a Problem Details body (D-60). */
export interface ProblemFieldError {
  field: string
  code: string
  message: string
}

/** RFC 9457 Problem Details with Rentrix's stable machine `code` (D-60). */
export interface ProblemDetails {
  type?: string
  title?: string
  status: number
  code: string
  detail?: string
  instance?: string
  errors?: ProblemFieldError[]
  traceId?: string
}

/** Pagination envelope of list endpoints (D-59). */
export interface Page<T> {
  items: T[]
  page: { number: number; size: number; totalItems: number; totalPages: number }
}

export type Role = "TENANT" | "LANDLORD" | "ADMIN"

/** The signed-in user as `/auth/*` and `/me` return it (hand-written until B3). */
export interface CurrentUser {
  id: string
  email: string
  displayName: string
  avatarUrl?: string | null
  roles: Role[]
  homeCity?: string | null
}

/** `POST /auth/google`, `/auth/dev-login` and `/auth/refresh` (D-62, D-63, D-70). */
export interface AuthSession {
  accessToken: string
  user?: CurrentUser
  needsOnboarding?: boolean
}
