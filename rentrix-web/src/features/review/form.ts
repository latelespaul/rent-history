import type {
  HomeCriterion,
  LandlordCriterion,
  ReviewCriterion,
  TenantCriterion,
  ReviewFormInput,
  ReviewFormPart,
} from "@/types/api"

/**
 * The review form (captain 2026-11-23 21:29, SYSTEM_DESIGN D-157): what each screen asks, how the
 * overall rating is worked out, and the draft kept on this device. Pure functions, so they are
 * tested without the page.
 */

export interface CriterionText {
  key: ReviewCriterion
  label: string
  hint: string
}

export const HOME_CRITERIA: (CriterionText & { key: HomeCriterion })[] = [
  { key: "WATER_SUPPLY", label: "Water supply", hint: "Steady supply, pressure and quality" },
  { key: "POWER_BACKUP", label: "Power backup and electricity", hint: "Power cuts and backup" },
  { key: "MAINTENANCE", label: "Maintenance and repairs", hint: "How fast things get fixed" },
  {
    key: "CLEANLINESS",
    label: "Cleanliness and common areas",
    hint: "Stairs, lobby, lift and garbage",
  },
  { key: "SECURITY", label: "Security", hint: "Guards, gates and safety at night" },
  { key: "PARKING", label: "Parking", hint: "Space for your car or bike" },
  { key: "NOISE", label: "Noise", hint: "5 stars is quiet" },
  {
    key: "NEIGHBOURHOOD",
    label: "Neighbourhood and connectivity",
    hint: "Shops, transport, schools and offices",
  },
  { key: "VALUE_FOR_RENT", label: "Value for rent", hint: "Worth what you pay" },
]

export const LANDLORD_CRITERIA: (CriterionText & { key: LandlordCriterion })[] = [
  { key: "RESPONSIVENESS", label: "Responsiveness", hint: "Answers and acts on your messages" },
  {
    key: "DEPOSIT_RETURN",
    label: "Deposit returned fairly and on time",
    hint: "Skip it if you have not moved out",
  },
  {
    key: "TRANSPARENCY",
    label: "Transparency on rent hikes and charges",
    hint: "No surprise fees",
  },
  { key: "PRIVACY", label: "Respects your privacy", hint: "Asks before a visit" },
  { key: "BEHAVIOUR", label: "Behaviour", hint: "Polite and fair" },
]

/** The owner's five questions about the tenant (landlord-adds-tenant LT-6). */
export const TENANT_CRITERIA: (CriterionText & { key: TenantCriterion })[] = [
  { key: "RENT_ON_TIME", label: "Paid rent on time", hint: "Every month, without reminders" },
  { key: "CARE_OF_HOME", label: "Took care of the home", hint: "Kept it clean, reported repairs" },
  { key: "COMMUNICATION", label: "Easy to reach and clear", hint: "Answers and says things early" },
  {
    key: "HOUSE_RULES",
    label: "Respected the society rules and neighbours",
    hint: "No complaints from the society",
  },
  {
    key: "MOVE_OUT",
    label: "Moved out as agreed (notice, keys, condition)",
    hint: "Skip it if they still live there",
  },
]

const LABELS = new Map<ReviewCriterion, string>(
  [...HOME_CRITERIA, ...LANDLORD_CRITERIA, ...TENANT_CRITERIA].map((c) => [c.key, c.label]),
)

export function criterionLabel(key: ReviewCriterion): string {
  return LABELS.get(key) ?? key
}

/** The longest texts the server takes (ReviewContentRules). */
export const COMMENT_MAX = 2000
export const PROS_CONS_MAX = 1000

export type Ratings = Partial<Record<ReviewCriterion, number>>

/**
 * The overall rating the form fills in: the average of the rated parameters, rounded half up to
 * whole stars. Null when nothing is rated, so the user must pick one.
 */
export function workedOutOverall(ratings: Ratings): number | null {
  const values = Object.values(ratings).filter((v): v is number => typeof v === "number")
  if (values.length === 0) return null
  const average = values.reduce((sum, v) => sum + v, 0) / values.length
  return Math.min(5, Math.max(1, Math.floor(average + 0.5)))
}

/** One part (home, landlord or tenant) as the user fills it. `ownOverall` null means "worked out". */
export interface PartDraft {
  ratings: Ratings
  comment: string
  ownOverall: number | null
  recommend: boolean | null
}

export interface ReviewDraft {
  version: 1
  step: number
  home: PartDraft
  landlord: PartDraft
  /** The owner's review of the tenant; absent in drafts saved before it existed. */
  tenant?: PartDraft
  pros: string
  cons: string
  savedAt?: string
}

function emptyPart(): PartDraft {
  return { ratings: {}, comment: "", ownOverall: null, recommend: null }
}

export function emptyDraft(): ReviewDraft {
  return {
    version: 1,
    step: 0,
    home: emptyPart(),
    landlord: emptyPart(),
    tenant: emptyPart(),
    pros: "",
    cons: "",
  }
}

/** The parts of one form: the tenant's (home, landlord) or the owner's (tenant). */
export type FormParts = { home: boolean; landlord: boolean; tenant?: boolean }
export type PartKey = "home" | "landlord" | "tenant"

/** A part of the draft, empty when the draft has none yet. */
export function partOf(draft: ReviewDraft, key: PartKey): PartDraft {
  return draft[key] ?? emptyPart()
}

/** The overall rating that will be sent: the user's own, else the worked-out one. */
export function overallOf(part: PartDraft): number | null {
  return part.ownOverall ?? workedOutOverall(part.ratings)
}

export type Section = "stay" | "home" | "landlord" | "tenant" | "overall" | "check"

/** The screens, one section each; the landlord one only when the landlord can be reviewed. */
export function sectionsFor(parts: FormParts): Section[] {
  return [
    "stay",
    ...(parts.home ? (["home"] as const) : []),
    ...(parts.landlord ? (["landlord"] as const) : []),
    ...(parts.tenant ? (["tenant"] as const) : []),
    "overall",
    "check",
  ]
}

function part(draft: PartDraft, extra?: { pros: string; cons: string }): ReviewFormPart {
  const overall = overallOf(draft)
  return {
    ratingOverall: overall ?? 0,
    criteria: { ...draft.ratings },
    ...(draft.recommend !== null && { wouldRecommend: draft.recommend }),
    ...(draft.comment.trim() && { body: draft.comment.trim() }),
    ...(extra?.pros.trim() && { pros: extra.pros.trim() }),
    ...(extra?.cons.trim() && { cons: extra.cons.trim() }),
  }
}

/**
 * The request body. Pros and cons are about the stay and go with the home review, which
 * publishes at once; they go with the landlord review only when the home is not part of the form.
 * The tenant part never takes them: the owner writes about the tenant in the comment.
 */
export function toRequest(draft: ReviewDraft, parts: FormParts): ReviewFormInput {
  const extra = { pros: draft.pros, cons: draft.cons }
  return {
    ...(parts.home && { home: part(draft.home, extra) }),
    ...(parts.landlord && { landlord: part(draft.landlord, parts.home ? undefined : extra) }),
    ...(parts.tenant && { tenant: part(partOf(draft, "tenant")) }),
  }
}

/**
 * The screen that holds a server field error (`home.criteria.PARKING`, `landlord.ratingOverall`,
 * `home.pros`), so the form can open it. Overall ratings, recommend, pros and cons are on the
 * overall screen.
 */
export function sectionOfField(field: string): Section {
  const [prefix, name] = field.split(".")
  if (name === "criteria" || name === "body")
    return prefix === "landlord" || prefix === "tenant" ? prefix : "home"
  return "overall"
}

/** Client checks before "Next": the overall rating of every part on the overall screen. */
export function missingOverall(draft: ReviewDraft, parts: FormParts): Record<string, string> {
  const errors: Record<string, string> = {}
  if (parts.home && overallOf(draft.home) === null)
    errors["home.ratingOverall"] = "Pick an overall rating for the home."
  if (parts.landlord && overallOf(draft.landlord) === null)
    errors["landlord.ratingOverall"] = "Pick an overall rating for the landlord."
  if (parts.tenant && overallOf(partOf(draft, "tenant")) === null)
    errors["tenant.ratingOverall"] = "Pick an overall rating for the tenant."
  return errors
}

const KEY = "rx-review-draft:"

/**
 * The draft's storage key: the tenant's keeps `rx-review-draft:<tenancyId>`, the owner's adds
 * `:LANDLORD`, so the two sides never share a draft on one device.
 */
export function draftKey(tenancyId: string, side: "TENANT" | "LANDLORD" = "TENANT"): string {
  return side === "LANDLORD" ? `${tenancyId}:LANDLORD` : tenancyId
}

/**
 * The saved draft of this tenancy's review, or null. Storage may be blocked: never throws.
 * `id` is a {@link draftKey}.
 */
export function loadDraft(id: string): ReviewDraft | null {
  try {
    const raw = localStorage.getItem(KEY + id)
    if (!raw) return null
    const draft = JSON.parse(raw) as ReviewDraft
    return draft?.version === 1 ? draft : null
  } catch {
    return null
  }
}

/** Keeps the draft on this device only; it never reaches the server (D-157). */
export function saveDraft(id: string, draft: ReviewDraft): boolean {
  try {
    localStorage.setItem(KEY + id, JSON.stringify(draft))
    return true
  } catch {
    return false
  }
}

export function clearDraft(id: string) {
  try {
    localStorage.removeItem(KEY + id)
  } catch {
    // storage blocked: nothing was saved
  }
}

/** "80% would recommend", from 3 answers on (D-52, D-158); null below that. */
export function recommendShare(recommend?: { answered: number; yes: number }): string | null {
  if (!recommend || recommend.answered < 3) return null
  return `${Math.round((recommend.yes / recommend.answered) * 100)}% would recommend`
}
