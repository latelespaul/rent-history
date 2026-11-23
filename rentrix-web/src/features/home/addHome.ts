import {
  EMPTY_DRAFT,
  hasArea,
  toLocationInput,
  type LocationDraft,
} from "@/features/landlord/locationDraft"
import { wholeNumber } from "@/features/tenancy/labels"
import type { AddHomeInput, HomeSize, PropertyType } from "@/types/api"
import type { HomeMatchQuery } from "@/api/homes"

/**
 * "Add your home" (D-139), one stepped form: where it is, how you are related to it, the tenancy
 * (renters only), the owner (renters only). An optional review follows once it is saved.
 */
export type HomeStep = "address" | "relation" | "tenancy" | "owner"

/** The city select's value for a city that is not in the list (D-143). */
export const OTHER_CITY = "OTHER"

export interface HomeDraft {
  /** A live city's code, or {@link OTHER_CITY} with `otherCity` / `otherState` typed in. */
  city: string
  otherCity: string
  otherState: string
  /** The locality, typed only for a new city (it has no areas yet). */
  locality: string
  location: LocationDraft
  /** The building of a standalone home (no society); required for an apartment. */
  buildingName: string
  unitLabel: string
  pincode: string
  /** True once the user typed the PIN code: a later area change no longer refills it. */
  pincodeTyped?: boolean
  /** A PIN code the picked area does not list, kept after the warning (firstmate 2026-11-23 14:50). */
  keptPincode?: string
  propertyType: PropertyType
  /** Older drafts only: the form now asks for the home size, and the API derives bedrooms from it. */
  bedrooms: string
  /** 1 RK … 5+ BHK, studio or other (captain 2026-11-23 17:18); "" when not picked. */
  homeSize: HomeSize | ""
  /** The size in the user's words, for "Other". */
  homeSizeOther: string
  /** A published home picked from "Is it this one?"; the address fields are then ignored. */
  propertyId: string | null
  relation: "RENT" | "OWN" | null
  startDate: string
  stillLiving: boolean
  endDate: string
  rent: string
  deposit: string
  ownerName: string
  ownerEmail: string
  ownerPhone: string
}

export const EMPTY_HOME: HomeDraft = {
  city: "",
  otherCity: "",
  otherState: "",
  locality: "",
  location: EMPTY_DRAFT,
  buildingName: "",
  unitLabel: "",
  pincode: "",
  propertyType: "APARTMENT",
  bedrooms: "",
  homeSize: "",
  homeSizeOther: "",
  propertyId: null,
  relation: null,
  startDate: "",
  stillLiving: true,
  endDate: "",
  rent: "",
  deposit: "",
  ownerName: "",
  ownerEmail: "",
  ownerPhone: "",
}

export const STEP_TITLES: Record<HomeStep, string> = {
  address: "Where is it?",
  relation: "Your role here",
  tenancy: "Your tenancy",
  owner: "The owner",
}

/** The steps this draft goes through: an owner skips the tenancy and owner steps. */
export function stepsFor(draft: HomeDraft): HomeStep[] {
  return draft.relation === "OWN"
    ? ["address", "relation"]
    : ["address", "relation", "tenancy", "owner"]
}

export type HomeErrors = Partial<Record<string, string>>

const PIN = /^[1-9]\d{5}$/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** True when the home is not in a society, so it is named by its building (captain 2026-11-23 11:35). */
export function standalone(draft: HomeDraft): boolean {
  if (!draft.city) return false
  if (draft.city === OTHER_CITY) return true
  return hasArea(draft.location) && draft.location.society.kind === "none"
}

/** What is missing or wrong on one step, by field; empty when the step can go on. */
export function checkStep(step: HomeStep, draft: HomeDraft, today: string): HomeErrors {
  const errors: HomeErrors = {}
  if (step === "address" && !draft.propertyId) {
    if (!draft.city) errors.city = "Pick the city."
    else if (draft.city === OTHER_CITY) {
      if (!draft.otherCity.trim()) errors.otherCity = "Type the city."
      if (!draft.otherState.trim()) errors.otherState = "Type the state."
      if (!draft.locality.trim()) errors.locality = "Type the area or locality."
    } else if (!hasArea(draft.location)) errors.area = "Pick or type the area."
    else Object.assign(errors, checkNewNames(draft))
    if (
      standalone(draft) &&
      draft.propertyType === "APARTMENT" &&
      !(draft.buildingName ?? "").trim()
    )
      errors.buildingName = "Enter the building name."
    if (!PIN.test(draft.pincode.trim())) errors.pincode = "Enter the 6-digit PIN code."
    if (!draft.unitLabel.trim()) errors.unitLabel = "Enter the flat or house number."
  }
  if (step === "address" && !draft.propertyId) {
    const bedrooms = wholeNumber(draft.bedrooms)
    if (bedrooms !== undefined && (!Number.isFinite(bedrooms) || bedrooms < 0 || bedrooms > 20))
      errors.bedrooms = "Enter 0 to 20 bedrooms, or leave it empty."
    if (draft.homeSize === "OTHER" && !draft.homeSizeOther.trim())
      errors.homeSizeOther = "Type the size, e.g. 2.5 BHK."
  }
  if (step === "relation" && !draft.relation) errors.relation = "Choose one."
  if (step === "relation" && draft.relation === "OWN" && draft.propertyId)
    errors.relation = "This home is already listed. Claiming it is checked by an admin."
  if (step === "tenancy") {
    if (!draft.startDate) errors.startDate = "Enter when you moved in."
    else if (draft.startDate > today) errors.startDate = "The move-in date cannot be in the future."
    if (!draft.stillLiving) {
      if (!draft.endDate) errors.endDate = "Enter when you moved out, or tick “I still live here”."
      else if (draft.startDate && draft.endDate < draft.startDate)
        errors.endDate = "The move-out date is before the move-in date."
    }
    const rent = wholeNumber(draft.rent)
    if (rent !== undefined && (!Number.isFinite(rent) || rent < 1))
      errors.rent = "Enter the monthly rent in rupees, or leave it empty."
    const deposit = wholeNumber(draft.deposit)
    if (deposit !== undefined && (!Number.isFinite(deposit) || deposit < 0))
      errors.deposit = "Enter the deposit in rupees, or leave it empty."
  }
  if (step === "owner" && draft.ownerEmail.trim() && !EMAIL.test(draft.ownerEmail.trim()))
    errors.ownerEmail = "Enter a valid email, or leave it empty."
  return errors
}

/** Rules of the API's `LocationService`: societies 2+ characters, towers and wings 1+ with a letter or digit. */
const LABEL = /[\p{L}\p{N}]/u

/** Errors on the society, tower and wing names typed in step 1, keyed by level. */
function checkNewNames(draft: HomeDraft): HomeErrors {
  const errors: HomeErrors = {}
  const { society, tower, wing } = draft.location
  if (society.kind === "new" && society.name.trim().length < 2)
    errors.society = "Type the society name (2 or more characters)."
  if (tower.kind === "new" && !LABEL.test(tower.name))
    errors.tower = "Type the tower or block name, e.g. 4 or C."
  if (wing.kind === "new" && !LABEL.test(wing.name))
    errors.wing = "Type the wing name, e.g. A or B."
  return errors
}

/**
 * Where an API field error belongs: its step and the form field that shows it. The API names
 * fields by request path (`home.pincode`, `location.newWing.name`, `owner.email`); every one of
 * them maps here, so no field error is left as a bare "invalid fields" message.
 */
export function fieldPlace(field: string): { step: HomeStep; key: string } {
  const path = field.replace(/^home\./, "")
  const location = /^location\.(?:new)?(area|society|tower|wing)/i.exec(path)
  if (location) return { step: "address", key: location[1].toLowerCase() }
  if (path === "relation") return { step: "relation", key: "relation" }
  if (path === "startDate" || path === "endDate") return { step: "tenancy", key: path }
  if (path === "monthlyRentInr") return { step: "tenancy", key: "rent" }
  if (path === "depositInr") return { step: "tenancy", key: "deposit" }
  const owner = /^owner\.(name|email|phone)$/.exec(path)
  if (owner) return { step: "owner", key: `owner${owner[1][0].toUpperCase()}${owner[1].slice(1)}` }
  if (path.startsWith("newCity"))
    return { step: "address", key: path === "newCity.state" ? "otherState" : "otherCity" }
  const address: Record<string, string> = { home: "city", location: "area", propertyId: "form" }
  const keys = [
    "city",
    "buildingName",
    "unitLabel",
    "pincode",
    "propertyType",
    "bedrooms",
    "homeSize",
    "homeSizeOther",
    "locality",
  ]
  return { step: "address", key: address[path] ?? (keys.includes(path) ? path : "form") }
}

/** The API's field errors as form errors, with the first step that holds one; null when none. */
export function placeFieldErrors(
  fieldErrors: Array<{ field: string; message: string }>,
  steps: HomeStep[],
): { step: number; errors: HomeErrors } | null {
  if (fieldErrors.length === 0) return null
  const errors: HomeErrors = {}
  let step = steps.length - 1
  for (const { field, message } of fieldErrors) {
    const place = fieldPlace(field)
    errors[place.key] ??= message
    const index = steps.indexOf(place.step)
    if (index >= 0 && index < step) step = index
  }
  return { step, errors }
}

/** The "Is it this one?" query, or null while the address is not complete enough to search. */
export function matchQuery(draft: HomeDraft): HomeMatchQuery | null {
  const { city, pincode, unitLabel, location } = draft
  if (!city || city === OTHER_CITY || !PIN.test(pincode.trim()) || !unitLabel.trim()) return null
  const society = location.society
  return {
    city,
    pincode: pincode.trim(),
    unit: unitLabel.trim(),
    ...(society.kind === "pick" ? { society: society.id } : {}),
    ...(society.kind === "new" ? { societyName: society.name.trim() } : {}),
  }
}

/** The home size fields of the body, only when one is picked. */
function homeSizeBody(draft: HomeDraft): { homeSize?: HomeSize; homeSizeOther?: string } {
  if (!draft.homeSize) return {}
  if (draft.homeSize !== "OTHER") return { homeSize: draft.homeSize }
  return { homeSize: "OTHER", homeSizeOther: draft.homeSizeOther.trim().slice(0, 40) }
}

const trimmed = (value: string) => (value.trim() ? value.trim() : undefined)

/** The `POST /me/homes` body. */
export function toAddHomeBody(draft: HomeDraft): AddHomeInput {
  const body: AddHomeInput = { relation: draft.relation ?? "RENT" }
  if (draft.propertyId) body.propertyId = draft.propertyId
  else if (draft.city === OTHER_CITY) {
    body.home = {
      city: OTHER_CITY,
      pincode: draft.pincode.trim(),
      locality: draft.locality.trim(),
      ...((draft.buildingName ?? "").trim()
        ? { buildingName: (draft.buildingName ?? "").trim() }
        : {}),
      unitLabel: draft.unitLabel.trim(),
      propertyType: draft.propertyType,
      bedrooms: wholeNumber(draft.bedrooms) ?? null,
      ...homeSizeBody(draft),
    }
    body.newCity = { name: draft.otherCity.trim(), state: draft.otherState.trim() }
  } else {
    // The society names the home in search and on its page, as a building name does.
    const society = draft.location.society
    const buildingName =
      society.kind === "none"
        ? (draft.buildingName ?? "").trim() || undefined
        : society.name.trim() || undefined
    body.home = {
      city: draft.city,
      pincode: draft.pincode.trim(),
      ...(buildingName ? { buildingName } : {}),
      unitLabel: draft.unitLabel.trim(),
      propertyType: draft.propertyType,
      bedrooms: wholeNumber(draft.bedrooms) ?? null,
      ...homeSizeBody(draft),
      location: toLocationInput(draft.location, draft.pincode),
    }
  }
  if (body.relation === "RENT") {
    body.startDate = draft.startDate
    if (!draft.stillLiving && draft.endDate) body.endDate = draft.endDate
    const rent = wholeNumber(draft.rent)
    if (rent !== undefined) body.monthlyRentInr = rent
    const deposit = wholeNumber(draft.deposit)
    if (deposit !== undefined) body.depositInr = deposit
    const owner = {
      name: trimmed(draft.ownerName),
      email: trimmed(draft.ownerEmail),
      phone: trimmed(draft.ownerPhone),
    }
    if (owner.name || owner.email || owner.phone) body.owner = owner
  }
  return body
}
