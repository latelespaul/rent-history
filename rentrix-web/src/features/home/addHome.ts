import {
  EMPTY_DRAFT,
  hasArea,
  toLocationInput,
  type LocationDraft,
} from "@/features/landlord/locationDraft"
import { wholeNumber } from "@/features/tenancy/labels"
import type { AddHomeInput, PropertyType } from "@/types/api"
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
  unitLabel: string
  pincode: string
  propertyType: PropertyType
  bedrooms: string
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
  unitLabel: "",
  pincode: "",
  propertyType: "APARTMENT",
  bedrooms: "",
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
    if (!PIN.test(draft.pincode.trim())) errors.pincode = "Enter the 6-digit PIN code."
    if (!draft.unitLabel.trim()) errors.unitLabel = "Enter the flat or house number."
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
    if (rent === undefined || !Number.isFinite(rent) || rent < 1)
      errors.rent = "Enter the monthly rent in rupees."
    const deposit = wholeNumber(draft.deposit)
    if (deposit !== undefined && (!Number.isFinite(deposit) || deposit < 0))
      errors.deposit = "Enter the deposit in rupees, or leave it empty."
  }
  if (step === "owner" && draft.ownerEmail.trim() && !EMAIL.test(draft.ownerEmail.trim()))
    errors.ownerEmail = "Enter a valid email, or leave it empty."
  return errors
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
      unitLabel: draft.unitLabel.trim(),
      propertyType: draft.propertyType,
      bedrooms: wholeNumber(draft.bedrooms) ?? null,
    }
    body.newCity = { name: draft.otherCity.trim(), state: draft.otherState.trim() }
  } else {
    // The society names the home in search and on its page, as a building name does.
    const society = draft.location.society
    const buildingName = society.kind === "none" ? undefined : society.name.trim() || undefined
    body.home = {
      city: draft.city,
      pincode: draft.pincode.trim(),
      ...(buildingName ? { buildingName } : {}),
      unitLabel: draft.unitLabel.trim(),
      propertyType: draft.propertyType,
      bedrooms: wholeNumber(draft.bedrooms) ?? null,
      location: toLocationInput(draft.location, draft.pincode),
    }
  }
  if (body.relation === "RENT") {
    body.startDate = draft.startDate
    if (!draft.stillLiving && draft.endDate) body.endDate = draft.endDate
    body.monthlyRentInr = wholeNumber(draft.rent)
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
