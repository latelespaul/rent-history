import type {
  Furnishing,
  PropertyDetail,
  PropertyInput,
  PropertyPatch,
  PropertyStatus,
  PropertyType,
} from "@/types/api"
import { wholeNumber } from "@/features/tenancy/labels"
import {
  draftFromLocation,
  EMPTY_DRAFT,
  hasArea,
  sameDraft,
  toLocationInput,
  type LocationDraft,
} from "./locationDraft"

/** The property form's fields as typed (strings), plus the location draft. */
export interface PropertyFormValues {
  city: string
  pincode: string
  buildingName: string
  street: string
  unitLabel: string
  propertyType: PropertyType
  bedrooms: string
  bathrooms: string
  areaSqft: string
  furnishing: Furnishing | ""
  parking: "" | "yes" | "no"
  description: string
  location: LocationDraft
}

export const EMPTY_FORM: PropertyFormValues = {
  city: "",
  pincode: "",
  buildingName: "",
  street: "",
  unitLabel: "",
  propertyType: "APARTMENT",
  bedrooms: "",
  bathrooms: "",
  areaSqft: "",
  furnishing: "",
  parking: "",
  description: "",
  location: EMPTY_DRAFT,
}

/** Location fields change only in `DRAFT` or `REJECTED` (D-92). */
export function locationEditable(status: PropertyStatus | undefined): boolean {
  return !status || status === "DRAFT" || status === "REJECTED"
}

const num = (n: number | null | undefined) => (n === null || n === undefined ? "" : String(n))

export function formFromProperty(p: PropertyDetail): PropertyFormValues {
  return {
    city: p.city.code,
    pincode: p.pincode ?? "",
    buildingName: p.buildingName ?? "",
    street: p.street ?? "",
    unitLabel: p.unitLabel ?? "",
    propertyType: p.propertyType,
    bedrooms: num(p.bedrooms),
    bathrooms: num(p.bathrooms),
    areaSqft: num(p.areaSqft),
    furnishing: p.furnishing ?? "",
    parking: p.parking === null ? "" : p.parking ? "yes" : "no",
    description: p.description ?? "",
    location: draftFromLocation(p.location),
  }
}

/** Client checks before the API's own (which stay the source of truth, D-60). */
export function checkForm(values: PropertyFormValues): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!values.city) errors.city = "Pick a city."
  if (!/^\d{6}$/.test(values.pincode.trim())) errors.pincode = "Enter the 6-digit PIN code."
  if (!hasArea(values.location)) errors.location = "Pick or suggest the area."
  const { society, tower, wing } = values.location
  for (const level of [society, tower, wing]) {
    if (level.kind === "new" && level.name.trim().length < 2) {
      errors.location = "Give each new society, tower or wing a name."
    }
  }
  for (const field of ["bedrooms", "bathrooms", "areaSqft"] as const) {
    if (Number.isNaN(wholeNumber(values[field]))) errors[field] = "Enter a whole number."
  }
  return errors
}

function common(values: PropertyFormValues) {
  return {
    propertyType: values.propertyType,
    bedrooms: wholeNumber(values.bedrooms) ?? null,
    bathrooms: wholeNumber(values.bathrooms) ?? null,
    areaSqft: wholeNumber(values.areaSqft) ?? null,
    furnishing: values.furnishing || null,
    parking: values.parking === "" ? null : values.parking === "yes",
    description: values.description.trim(),
    unitLabel: values.unitLabel.trim(),
  }
}

export function toCreateBody(values: PropertyFormValues): PropertyInput {
  return {
    city: values.city,
    pincode: values.pincode.trim(),
    buildingName: values.buildingName.trim(),
    street: values.street.trim(),
    ...common(values),
    location: toLocationInput(values.location, values.pincode),
  }
}

/**
 * The `PATCH` body: only changed fields. Location fields (city, PIN code, building, street,
 * place) are left out when the listing is locked, so an unchanged lock never fails (D-92).
 */
export function toPatchBody(
  initial: PropertyFormValues,
  values: PropertyFormValues,
  status: PropertyStatus,
): PropertyPatch {
  const before = common(initial)
  const after = common(values)
  const patch: PropertyPatch = {}
  for (const key of Object.keys(after) as Array<keyof typeof after>) {
    if (after[key] !== before[key]) Object.assign(patch, { [key]: after[key] })
  }
  if (locationEditable(status)) {
    if (values.city !== initial.city) patch.city = values.city
    if (values.pincode.trim() !== initial.pincode) patch.pincode = values.pincode.trim()
    if (values.buildingName.trim() !== initial.buildingName)
      patch.buildingName = values.buildingName.trim()
    if (values.street.trim() !== initial.street) patch.street = values.street.trim()
    if (!sameDraft(initial.location, values.location) || patch.city) {
      patch.location = toLocationInput(values.location, values.pincode)
    }
  }
  return patch
}
