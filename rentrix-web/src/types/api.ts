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

/** Approval state of one held role (D-89, D-91). */
export type VerificationStatus = "DRAFT" | "PENDING" | "APPROVED" | "REJECTED"

export type UserStatus = "ACTIVE" | "SUSPENDED" | "DELETED"

/**
 * The signed-in user's own account, as `/auth/*` and `/me` return it (B3 `UserView`; hand-written
 * until the OpenAPI snapshot is refreshed). Email and phone are shown only to the user (D-57).
 */
export interface CurrentUser {
  id: string
  displayName: string
  email?: string | null
  emailVerified: boolean
  avatarUrl?: string | null
  phone?: string | null
  roles: Role[]
  /** City code, as in `City.code`. */
  homeCity?: string | null
  status: UserStatus
  /** Seed user (D-68): the shell shows "Demo data". */
  demo: boolean
  /** True until the first-sign-in role pick is done (D-73). */
  needsOnboarding: boolean
  onboardedAt?: string | null
  /** Approval status per held TENANT / LANDLORD role. */
  verification: Partial<Record<Role, VerificationStatus>>
  createdAt: string
}

/** `POST /auth/google`, `/auth/dev-login` and `/auth/refresh` (D-62, D-63, D-70). */
export interface AuthSession {
  accessToken: string
  user?: CurrentUser
  needsOnboarding?: boolean
}

/** `GET /auth/config`: what the login page offers (D-76, D-77). */
export interface AuthConfig {
  /** Google OAuth client id; null hides the Google button. */
  googleClientId: string | null
  /** True only when the API runs dev login (dev/test profile + `DEV_LOGIN_ENABLED`). */
  devLogin: boolean
}

/** `POST /me/onboarding` (D-73). */
export interface OnboardingRequest {
  roles: Array<"TENANT" | "LANDLORD">
  homeCity?: string | null
  acceptTerms: boolean
}

/** `PATCH /me` (D-16): absent fields stay; an empty string clears `phone` / `homeCity`. */
export interface UpdateMeRequest {
  displayName?: string
  phone?: string
  homeCity?: string
  addRoles?: Array<"TENANT" | "LANDLORD">
}

/** `PATCH /me` and `POST /me/onboarding`: the account plus a fresh access token (D-62). */
export interface MeUpdate {
  user: CurrentUser
  accessToken: string
}
