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

/**
 * `GET /cities`: an active city (D-20, D-82). The list is data; the web never names a city.
 * `region` (null for most) groups cities the copy shows together, e.g. Delhi NCR.
 */
export type City = Required<Omit<Schemas["CityResponse"], "region">> & { region?: string | null }

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

/** RFC 9457 Problem Details with the API's stable machine `code` (D-60). */
export interface ProblemDetails {
  type?: string
  title?: string
  status: number
  code: string
  detail?: string
  instance?: string
  errors?: ProblemFieldError[]
  traceId?: string
  /** Unused since open by default (D-132): the API no longer answers `403 approval_required`. */
  approval?: { role: Role; status: VerificationStatus }
  /**
   * The steps still to do: on `422 verification_incomplete` (e.g. `["profile", "phone"]`) and
   * `["phone"]` on `403 id_verification_required` (D-117).
   */
  missing?: string[]
  /**
   * On `409 tenancy_overlap` only: the tenancy already on the home, as months (`to` absent while
   * it is current); `tenancyId` only for that home's landlord.
   */
  conflict?: { from: string; to?: string; status: TenancyStatus; tenancyId?: string }
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
  /** The public short name ("Asha R."), from the API's one rule; show it as-is. */
  shortName: string
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

/** Property kinds (SYSTEM_DESIGN §5.3). */
export type PropertyType = "APARTMENT" | "INDEPENDENT_HOUSE" | "VILLA" | "STUDIO" | "ROOM" | "PG"

/** Furnishing level (§5.3). */
export type Furnishing = "UNFURNISHED" | "SEMI" | "FULL"

/** A city as a property carries it: code and display name. */
export interface CityRef {
  code: string
  name: string
}

/**
 * A stored aggregate as cards and pages show it (§9): `average` (one decimal) is null under
 * 3 reviews, so the UI shows "New · n reviews" instead of stars (D-52).
 */
export interface RatingSummary {
  reviewCount: number
  average: number | null
}

/** One `GET /properties` result: public fields only (D-56, D-57). */
export interface PropertyCard {
  id: string
  city: CityRef
  locality: string
  buildingName: string | null
  street: string | null
  propertyType: PropertyType
  bedrooms: number | null
  bathrooms: number | null
  areaSqft: number | null
  furnishing: Furnishing | null
  parking: boolean | null
  demo: boolean
  rating: RatingSummary
  landlordName: string
}

/**
 * The owner as a property page shows them. Anonymous visitors get the short name and rating
 * only; `id` (the landlord profile link) comes only for signed-in callers (D-55).
 */
export interface LandlordSummary {
  id?: string
  shortName: string
  rating: RatingSummary
}

export type PropertyStatus = "DRAFT" | "PENDING_REVIEW" | "PUBLISHED" | "REJECTED" | "ARCHIVED"

/** `GET /properties/{id}`: `unitLabel` only for the owner and admins (D-56, D-57). */
export interface PropertyDetail {
  id: string
  city: CityRef
  locality: string
  buildingName: string | null
  street: string | null
  pincode: string | null
  unitLabel?: string
  propertyType: PropertyType
  bedrooms: number | null
  bathrooms: number | null
  areaSqft: number | null
  furnishing: Furnishing | null
  parking: boolean | null
  description: string | null
  status: PropertyStatus
  demo: boolean
  rating: RatingSummary
  landlord: LandlordSummary
  createdAt: string
  updatedAt: string
  /** Place in the hierarchy (D-107); absent for a home placed by free-text locality only. */
  location?: PropertyLocation
  /** A tenant added it while its landlord is not on the platform (D-139). */
  addedByTenant?: boolean
  /** The flat number is shown to everyone who may see the home (D-140); else only to the owner and admins. */
  unitLabelPublic?: boolean
  /** The green tick (D-134). */
  verification?: VerificationState
  /** The owner as the adding tenant named them; only for that tenant and admins (D-140). */
  ownerContact?: OwnerContact
}

/** The owner's details a tenant typed when adding a home (D-139); private (D-140). */
export interface OwnerContact {
  name?: string
  email?: string
  phone?: string
}

/** Green tick state of a property, tenancy or review (D-134). */
export type VerificationState = "UNVERIFIED" | "VERIFIED" | "REJECTED"

/** `POST /me/homes` (D-139): a new home or a picked one, and how the caller is related to it. */
export interface AddHomeInput {
  propertyId?: string
  home?: PropertyInput
  relation: "RENT" | "OWN"
  startDate?: string
  endDate?: string
  monthlyRentInr?: number
  depositInr?: number
  owner?: OwnerContact
  /** A city not in the list (D-143): created pending admin approval; `home.city` is then ignored. */
  newCity?: { name: string; state: string }
}

/** The answer of `POST /me/homes`; `accessToken` when the step added the landlord role. */
export interface AddHomeResult {
  property: PropertyDetail
  tenancy?: Tenancy
  accessToken?: string
}

/** `POST /tenancies/{id}/reviews` (D-27, D-43). */
export interface ReviewInput {
  direction: "TENANT_TO_LANDLORD" | "LANDLORD_TO_TENANT" | "TENANT_TO_PROPERTY"
  ratingOverall: number
  body: string
}

/** Location row state (§5.17): `PENDING` is a landlord's proposal, shown "Pending review" (D-109). */
export type LocationStatus = "ACTIVE" | "PENDING" | "HIDDEN"

/** One level of a property's place: its `public_id`, name and state. */
export interface LocationLevel {
  id: string
  name: string
  status: LocationStatus
}

/** `PropertyDetail.location`: the area always, society / tower / wing inside a society. */
export interface PropertyLocation {
  area: LocationLevel
  society?: LocationLevel
  tower?: LocationLevel
  wing?: LocationLevel
}

export type SocietyKind = "GATED_COMMUNITY" | "APARTMENT_COMPLEX"
export type TowerKind = "TOWER" | "BLOCK"

/**
 * `location` of `POST` / `PATCH /properties` (D-109, D-110): at each level an existing row's id or
 * an inline proposal, never both. Area required; society, tower and wing optional, each needing
 * the level above.
 */
export interface LocationInput {
  areaId?: string
  newArea?: { name: string; pincode: string }
  societyId?: string
  newSociety?: { name: string; kind: SocietyKind; developer?: string }
  towerId?: string
  newTower?: { name: string; kind?: TowerKind }
  wingId?: string
  newWing?: { name: string }
}

/** One `GET /areas` card (D-108). */
export interface AreaCard {
  id: string
  city: CityRef
  name: string
  aliases: string[]
  pincodes: string[]
  status: LocationStatus
  societyCount: number
  publishedHomes: number
}

/** One `GET /societies` card (D-112). */
export interface SocietyCard {
  id: string
  name: string
  developer: string | null
  kind: SocietyKind
  area: { id: string; name: string }
  city: CityRef
  street: string | null
  status: LocationStatus
  towerCount: number
  publishedHomes: number
}

/** `GET /societies/{id}/towers`: towers with their wings, for the form pickers. */
export interface TowerView {
  id: string
  name: string
  kind: TowerKind
  status: LocationStatus
  publishedHomes: number
  wings: Array<{ id: string; name: string; status: LocationStatus; publishedHomes: number }>
}

/** `POST /properties` body (§12.2). With `location` the locality is copied from the area. */
export interface PropertyInput {
  city: string
  pincode: string
  locality?: string
  buildingName?: string
  street?: string
  unitLabel?: string
  propertyType: PropertyType
  bedrooms?: number | null
  bathrooms?: number | null
  areaSqft?: number | null
  furnishing?: Furnishing | null
  parking?: boolean | null
  description?: string
  location?: LocationInput
}

/** `PATCH /properties/{id}`: absent fields stay; `status` only `ARCHIVED` (D-21). */
export type PropertyPatch = Partial<PropertyInput> & { status?: "ARCHIVED" }

/**
 * One row of `GET /properties/{id}/rent-history` (D-26): months as `YYYY-MM`, no tenant
 * identity; `toMonth` absent while current; `share` on room / PG homes.
 */
export interface RentHistoryRow {
  fromMonth: string
  toMonth?: string | null
  /** Absent where the tenancy's rent is masked (D-140). */
  monthlyRentInr?: number | null
  maintenanceInr?: number | null
  share: boolean
  /** False for a tenant's own rent on an unclaimed home, which nobody confirmed (D-139). */
  confirmed?: boolean
}

/** `GET /properties/{id}/rent-history`. */
export interface RentHistory {
  propertyId: string
  rows: RentHistoryRow[]
}

export type TenancyStatus =
  "PENDING_CONFIRMATION" | "ACTIVE" | "ENDED" | "DECLINED" | "EXPIRED" | "CANCELLED" | "VOIDED"

export type TenancyParty = "LANDLORD" | "TENANT"

/** The other side of a tenancy: public id and short name only (D-57). */
export interface PartyRef {
  id: string
  shortName: string
}

/** What the caller may do next, by role and state (§6); the approval gate is on the write. */
export interface TenancyActions {
  confirm: boolean
  decline: boolean
  cancel: boolean
  end: boolean
  addRentRecord: boolean
}

/** `GET /tenancies/{id}` and the `/me/tenancies` items (§12.2). Dates and deposit are private. */
export interface Tenancy {
  id: string
  property: {
    id: string
    buildingName: string | null
    locality: string
    city: CityRef
    unitLabel: string | null
    propertyType: PropertyType
  }
  myRole: TenancyParty | null
  landlord: PartyRef
  /** Null for the landlord until a landlord-started request is confirmed. */
  tenant: PartyRef | null
  /** Masked for the landlord (`p***@g***.com`). */
  inviteEmail: string | null
  startDate: string
  endDate: string | null
  initialRentInr: number
  currentRentInr: number | null
  /** The date the current rent applies from; null until the tenancy is confirmed. */
  currentRentFrom: string | null
  depositInr: number | null
  depositRefundedInr: number | null
  status: TenancyStatus
  initiatedBy: TenancyParty
  declineReason: string | null
  evidenceLevel: "MUTUAL" | "DOCUMENT" | null
  createdAt: string
  expiresAt: string | null
  confirmedAt: string | null
  endedAt: string | null
  actions: TenancyActions
}

/** `POST /tenancies` (§12.2): the landlord gives `counterpartyEmail`; a tenant does not. */
export interface TenancyInput {
  propertyId: string
  counterpartyEmail?: string
  startDate: string
  endDate?: string
  initialRentInr: number
  depositInr?: number
}

export type RentRecordStatus = "PROPOSED" | "CONFIRMED" | "DISPUTED"

/** One rent record of a tenancy (D-25); `canRespond` when the caller must confirm or dispute it. */
export interface RentRecord {
  id: string
  effectiveFrom: string
  monthlyRentInr: number
  maintenanceInr: number | null
  status: RentRecordStatus
  addedBy: PartyRef
  addedByMe: boolean
  canRespond: boolean
  createdAt: string
}

/** `POST /tenancies/{id}/rent-records`: a rent change from a month on. */
export interface RentRecordInput {
  effectiveFrom: string
  monthlyRentInr: number
  maintenanceInr?: number
}

/**
 * One review as `GET /properties/{id}/reviews` returns it (the server's `ReviewView`, absent fields
 * omitted): `body` is missing while a report hides the text (`textHidden`), and `author.id` only for
 * landlord authors (D-55, D-57).
 */
export interface PropertyReview {
  id: string
  direction?: "TENANT_TO_LANDLORD" | "LANDLORD_TO_TENANT" | "TENANT_TO_PROPERTY"
  status?: string
  ratingOverall?: number
  ratingA?: number
  ratingB?: number
  ratingC?: number
  body?: string
  textHidden?: boolean
  author?: {
    id?: string
    shortName: string
    badges?: { idVerified: boolean; linkedinVerified: boolean }
  }
  /** The reviewed home; no unit label (D-57). */
  property?: { id: string; buildingName: string; locality: string; city: CityRef }
  period?: { from: string; to?: string }
  publishedAt?: string
  editedAt?: string
  /** The subject's one public reply. */
  response?: { body: string; authorShortName?: string; createdAt?: string; editUntil?: string }
  /** What the caller may do now; the approval gate is checked on the write (D-91). */
  actions?: { edit: boolean; withdraw: boolean; respond: boolean; editResponse: boolean }
  /** The green tick (D-134): "Not verified yet" until an admin decides. */
  verification?: VerificationState
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

/** One step of the verification stepper on `GET /me/verification` (D-120). */
export type StepStatus = "DONE" | "PENDING" | "MISSING" | "REJECTED" | "SKIPPED" | "NOT_APPLICABLE"

/** `GET /me/verification`: one entry per held TENANT / LANDLORD role (D-89, D-120). */
export interface VerificationEntry {
  /** The application's id; absent until the first "Request verification". */
  id?: string
  role: Exclude<Role, "ADMIN">
  status: VerificationStatus
  revision: number
  steps: {
    profile: StepStatus
    phone: StepStatus
    identity: StepStatus
    linkedin: StepStatus
    rentalAgreement: StepStatus
  }
  submittedAt?: string
  /** The last decision: code and note for `REJECTED`, only the date for `APPROVED`. */
  decision?: { code?: string; note?: string; decidedAt: string }
}

/** `TRUECALLER` is the phone verified by Truecaller; it counts like `PHONE_OTP` (either one is the phone). */
export type IdentityMethod = "AADHAAR" | "PAN" | "PHONE_OTP" | "LINKEDIN" | "TRUECALLER"
export type IdentityStatus = "PENDING" | "VERIFIED" | "REJECTED" | "REVOKED"

/** One method on `GET /me/identity`: masked values only (D-106). */
export interface IdentityMethodView {
  method: IdentityMethod
  /** Null when never started. */
  status?: IdentityStatus | null
  /** PAN `XXXXXX234F`, Aadhaar `XXXX XXXX 1234`, phone `+91 XXXXXX3210`, or the LinkedIn URL. */
  masked?: string | null
  checkedBy?: "ADMIN" | "OTP" | "PROVIDER" | null
  verifiedAt?: string | null
  reject?: { code: string; note?: string | null } | null
  /** Whether the method can be started now (phone needs an SMS sender; PAN / Aadhaar need B12). */
  available: boolean
}

/** `GET /me/identity` (D-100, D-106). `idVerified` is the PAN / Aadhaar badge, not the phone. */
export interface IdentityView {
  idVerified: boolean
  linkedinVerified: boolean
  methods: IdentityMethodView[]
}

/** `POST /me/identity/truecaller/start`: open `deepLink` on the phone, then poll `requestId`. */
export interface TruecallerStart {
  requestId: string
  deepLink: string
  expiresAt: string
}

/** Where a Truecaller request stands; `VERIFIED`, `REJECTED`, `FAILED` and `EXPIRED` are final. */
export type TruecallerStatus =
  "PENDING" | "INVOKED" | "VERIFYING" | "VERIFIED" | "REJECTED" | "FAILED" | "EXPIRED"

/** `GET /me/identity/truecaller/{requestId}`. */
export interface TruecallerRequest {
  requestId: string
  status: TruecallerStatus
  /** The verified number, masked (`+91 XXXXXX3210`). */
  masked?: string | null
  /** Why it did not verify, e.g. `user_rejected`, `profile_unavailable`, `not_indian_mobile`. */
  reason?: string | null
}

/** `POST /me/identity/phone/start`: the code was sent (D-101). */
export interface PhoneChallenge {
  challengeId: string
  expiresAt: string
  /**
   * `SMS`: a provider sent it. `TEAM`: the preview-only admin relay (D-137), where our team passes
   * the code on by WhatsApp or SMS.
   */
  delivery?: "SMS" | "TEAM"
}
