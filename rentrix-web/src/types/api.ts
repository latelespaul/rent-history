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
  /** The steps still to do on `422 verification_incomplete` (e.g. `["profile", "method"]`). */
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
  /** The header's Tenant | Owner mode the user last picked; null or absent if never. */
  preferredMode?: "TENANT" | "OWNER" | null
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

/** The review form's parameters (D-157): nine about the home, five about the landlord. */
export type HomeCriterion =
  | "WATER_SUPPLY"
  | "POWER_BACKUP"
  | "MAINTENANCE"
  | "CLEANLINESS"
  | "SECURITY"
  | "PARKING"
  | "NOISE"
  | "NEIGHBOURHOOD"
  | "VALUE_FOR_RENT"
export type LandlordCriterion =
  "RESPONSIVENESS" | "DEPOSIT_RETURN" | "TRANSPARENCY" | "PRIVACY" | "BEHAVIOUR"
/** The owner's questions about the tenant (landlord-adds-tenant LT-6). */
export type TenantCriterion =
  "RENT_ON_TIME" | "CARE_OF_HOME" | "COMMUNICATION" | "HOUSE_RULES" | "MOVE_OUT"
export type ReviewCriterion = HomeCriterion | LandlordCriterion | TenantCriterion

/** One parameter of a rating (D-158): `average` only from 3 ratings of it (D-52). */
export interface CriterionRating {
  criterion: ReviewCriterion
  ratingCount: number
  average?: number
}

/**
 * `GET /properties/{id}/rating` and `landlordRating` of `GET /users/{id}`: the summary plus the
 * review form's per-parameter figures and the "would you recommend" answers (D-158).
 */
export interface RatingDetail extends RatingSummary {
  distribution?: number[]
  criteria?: CriterionRating[]
  recommend?: { answered: number; yes: number }
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
  /** Absent when not given. */
  homeSize?: HomeSize
  /** The size in the user's words, with `homeSize` `OTHER`. */
  homeSizeOther?: string
  bathrooms: number | null
  areaSqft: number | null
  furnishing: Furnishing | null
  parking: boolean | null
  demo: boolean
  rating: RatingSummary
  /** "Owner", never the landlord's name (D-168), or "Not on Doorway Living yet". */
  landlordLabel: string
  /** The area, society, tower or wing is a new name the team has not reviewed yet; shown tagged. */
  locationPending?: boolean
  /** The home's green tick (D-148); anything but `VERIFIED` shows "Not verified yet" (D-162). */
  verification?: VerificationState
  /** Area, and society / tower / wing when in one: the card's title line (D-165). */
  location?: PropertyLocation
  /** The flat number, only when it is open to everyone (D-140); never a masked one (D-57). */
  unitLabel?: string
  /** Every published review the home's page lists, verified or not (D-165). */
  listedReviews?: number
}

/**
 * The three public verification badges of a reviewer (D-168): signed in with Google, any one of
 * phone, Aadhaar or LinkedIn verified, and a rental agreement behind the tenancy. `agreement` is
 * present only when that status exists.
 */
export interface TrustBadges {
  google: boolean
  id: boolean
  agreement?: boolean
}

/**
 * The owner as a property page shows them: `label` "Owner" (or "Not on Doorway Living yet"),
 * never the name; `shortName` only for the owner and admins (D-168). `id` (the landlord profile
 * link) comes only for signed-in callers (D-55).
 */
export interface LandlordSummary {
  id?: string
  label: string
  shortName?: string
  rating: RatingSummary
  /** The owner's three badges; null for an unclaimed home's owner. */
  badges?: TrustBadges | null
}

export type PropertyStatus = "DRAFT" | "PENDING_REVIEW" | "PUBLISHED" | "REJECTED" | "ARCHIVED"

/** How big a home is, as Indian listings say it (captain 2026-11-23 17:18). */
export type HomeSize = "RK1" | "BHK1" | "BHK2" | "BHK3" | "BHK4" | "BHK5_PLUS" | "STUDIO" | "OTHER"

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
  /** Absent when not given. */
  homeSize?: HomeSize
  /** The size in the user's words, with `homeSize` `OTHER`. */
  homeSizeOther?: string
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
  /** The caller added this home and may change or delete it (D-156). */
  editable?: boolean
}

/**
 * `PATCH /me/homes/{id}` (D-156): the corrected address and size; the whole set replaces the old
 * values. Without `location` the home keeps its place.
 */
export interface HomeUpdate {
  buildingName?: string
  street?: string
  unitLabel?: string
  pincode: string
  homeSize?: HomeSize
  homeSizeOther?: string
  location?: LocationInput
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

/** One part of the review form (D-157): every parameter optional, the overall rating required. */
export interface ReviewFormPart {
  ratingOverall: number
  criteria: Partial<Record<ReviewCriterion, number>>
  wouldRecommend?: boolean
  body?: string
  pros?: string
  cons?: string
}

/**
 * `POST /tenancies/{id}/review-form`: the tenant's home and landlord reviews, saved together, or
 * the owner's review of the tenant (no pros or cons).
 */
export interface ReviewFormInput {
  home?: ReviewFormPart
  landlord?: ReviewFormPart
  tenant?: ReviewFormPart
}

export interface ReviewFormResult {
  home?: PropertyReview
  landlord?: PropertyReview
  tenant?: PropertyReview
}

/** `GET /me/review-tasks`: a review the caller can write now. */
export interface ReviewTask {
  tenancyId: string
  direction: "TENANT_TO_LANDLORD" | "LANDLORD_TO_TENANT" | "TENANT_TO_PROPERTY"
  property: { id: string; buildingName: string | null; locality: string; city: CityRef }
  /** `id` is null for a tenancy with no linked tenant yet ("Your tenant"). */
  counterparty?: { id: string | null; shortName: string }
  period: { from: string; to?: string }
  otherSideWaiting: boolean
}

/** `GET /users/{id}`: the landlord part for every signed-in caller (D-55). */
export interface UserProfile {
  id: string
  /** "Owner" or "Tenant", never the name (D-168). */
  label: string
  /** Only for the person themselves and admins. */
  shortName?: string
  memberSince: string
  badges?: TrustBadges
  landlordRating?: RatingDetail
  listedHomes?: number
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

/**
 * A society as search shows it when 2+ of its homes match, and the society page summary (D-166).
 * `rating` pools the homes' verified reviews (D-163); `listedReviews` counts every published one;
 * `rentRange` comes only for signed-in callers (D-56) and only from rents open to everyone.
 */
export interface SocietyGroup {
  id: string
  name: string
  area: { id: string; name: string }
  city: CityRef
  pending: boolean
  flats: number
  listedReviews: number
  rating: RatingSummary
  rentRange?: { min: number; max: number }
}

/** One row of `GET /properties/grouped` (D-166): a society with 2+ matching homes, or a home. */
export type SearchItem =
  { kind: "HOME"; home: PropertyCard } | { kind: "SOCIETY"; society: SocietyGroup }

/** `GET /properties/grouped`: the D-59 page over rows, plus every matching home (`totalHomes`). */
export interface GroupedPage extends Page<SearchItem> {
  totalHomes: number
}

/** `GET /societies/{id}`: the society page header and its towers (D-112). */
export interface SocietyDetail {
  id: string
  name: string
  developer: string | null
  kind: SocietyKind
  area: { id: string; name: string }
  city: CityRef
  street: string | null
  status: LocationStatus
  publishedHomes: number
  towers: TowerView[]
}

/** `GET /search/suggestions` (D-164, D-167): societies, areas, open flats and a picked society's towers. */
export interface SearchSuggestions {
  societies: Array<{
    id: string
    name: string
    area: { id: string; name: string }
    city: CityRef
    tower?: { id: string; name: string }
    publishedHomes: number
    pending: boolean
  }>
  areas: Array<{ id: string; name: string; city: CityRef }>
  homes: Array<{
    id: string
    society: { id: string; name: string }
    tower?: { id: string; name: string }
    wing?: { id: string; name: string }
    unitLabel: string
    locality: string
    city: CityRef
  }>
  towers: Array<{ id: string; name: string; publishedHomes: number }>
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
  homeSize?: HomeSize
  homeSizeOther?: string
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
  /** False for a tenant's own one-sided rent, which nobody confirmed (D-142). */
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

/** Review lifecycle (§7.2). */
export type ReviewStatus = "AWAITING_REVEAL" | "PUBLISHED" | "HIDDEN" | "WITHDRAWN"

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
  /** The caller added this tenancy and may change it (D-156). */
  edit?: boolean
  /** The caller added it and may delete it (the other side has not confirmed it). */
  delete?: boolean
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
  /** Null when the tenant did not share the rent ("Add your home"); shown "Rent not shared". */
  initialRentInr: number | null
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
  /** True once the caller wrote a review on this tenancy (the "Rate and review" next step). */
  reviewed?: boolean
  /** The caller's home review on this tenancy; only a PUBLISHED one shows on the home's page. */
  homeReviewStatus?: ReviewStatus | null
  /** The team's green tick on the tenancy (D-134, D-147). */
  verificationStatus?: VerificationState
  /** Set only when an edit gave the caller a new role ("I own it" makes a tenant a landlord). */
  accessToken?: string
}

/**
 * `PATCH /tenancies/{id}` (D-156): `endDate` null while it is current, `initialRentInr` null = not
 * shared. `relation` "OWN" on a tenant's own record of a home they added makes the home theirs and
 * removes the tenancy.
 */
export interface TenancyUpdate {
  startDate: string
  endDate: string | null
  initialRentInr: number | null
  depositInr: number | null
  relation?: "RENT" | "OWN"
}

/** `POST /me/change-requests` (D-156): the creator asks the team to change their own item. */
export interface ChangeRequestInput {
  targetType: "TENANCY" | "PROPERTY" | "REVIEW"
  targetId: string
  note: string
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
  /** The caller added it and may change or delete it (D-156); never the starting rent. */
  canEdit?: boolean
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
/** A tenant author's coarse stay length (D-171): "Stayed < 1 yr", "Stayed 1-2 yrs", "Stayed 2+ yrs". */
export type StayLength = "UNDER_ONE_YEAR" | "ONE_TO_TWO_YEARS" | "TWO_PLUS_YEARS"

export interface PropertyReview {
  id: string
  direction?: "TENANT_TO_LANDLORD" | "LANDLORD_TO_TENANT" | "TENANT_TO_PROPERTY"
  status?: string
  ratingOverall?: number
  ratingA?: number
  ratingB?: number
  ratingC?: number
  /** Written with the review form (D-157): the comment is optional on edit. */
  formReview?: boolean
  /** The review form's parameter ratings (D-157), in form order. */
  criteria?: Partial<Record<ReviewCriterion, number>>
  wouldRecommend?: boolean
  /** The text, or a form review's comment; absent when it has none. */
  body?: string
  pros?: string
  cons?: string
  textHidden?: boolean
  /**
   * Never the name (D-168) and never a year (D-169): `label` is "Verified tenant", "Tenant" or
   * "Owner"; `stayLength` is a tenant author's coarse stay bucket (D-171); `shortName` only for the
   * author and admins.
   */
  author?: {
    id?: string
    label: string
    stayLength?: StayLength
    shortName?: string
    badges?: TrustBadges
  }
  /** The reviewed person (landlord or tenant); absent on a home review. */
  subject?: { id?: string; label: string; shortName?: string }
  /** The reviewed home; no unit label (D-57). */
  property?: { id: string; buildingName: string; locality: string; city: CityRef }
  /** The stay, only for the author and admins (D-169): it would tell the landlord who wrote it. */
  period?: { from: string; to?: string }
  /** The year it was published (UTC): never the month, day or time, for any stay (D-169, D-171). */
  publishedYear?: number
  /** True once the author edited it; the date only on `editedAt`, for the author and admins. */
  edited?: boolean
  /** Exact times, only for the author and admins (D-169). */
  publishedAt?: string
  editedAt?: string
  /** The subject's one public reply. */
  response?: {
    body: string
    /** "Owner", "Verified tenant" or "Tenant" (D-168); `authorShortName` only for the responder and admins. */
    authorLabel?: string
    authorShortName?: string
    createdAt?: string
    editUntil?: string
  }
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
  preferredMode?: "TENANT" | "OWNER"
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
    /**
     * Any one posting method (D-144): phone, masked Aadhaar or LinkedIn. `DONE` when one is
     * verified, `PENDING` while only an admin-checked one waits, else `MISSING`.
     */
    method: StepStatus
    identity: StepStatus
    linkedin: StepStatus
    rentalAgreement: StepStatus
  }
  submittedAt?: string
  /** The last decision: code and note for `REJECTED`, only the date for `APPROVED`. */
  decision?: { code?: string; note?: string; decidedAt: string }
}

/**
 * `TRUECALLER` is the phone verified by Truecaller and `TELEGRAM` the phone verified by a code sent to
 * its Telegram (D-146); each counts like `PHONE_OTP` (any one is the phone).
 */
export type IdentityMethod =
  "AADHAAR" | "PAN" | "PHONE_OTP" | "LINKEDIN" | "TRUECALLER" | "TELEGRAM"
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
   * the code on by WhatsApp or SMS. `TELEGRAM`: Telegram shows it in the user's Telegram (D-146).
   */
  delivery?: "SMS" | "TEAM" | "TELEGRAM"
}
