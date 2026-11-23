import type { HomeErrors } from "@/features/home/addHome"
import { fieldPlace } from "@/features/home/addHome"
import {
  draftFromLocation,
  hasArea,
  sameDraft,
  toLocationInput,
  type LocationDraft,
} from "@/features/landlord/locationDraft"
import type { HomeSize, HomeUpdate, PropertyDetail } from "@/types/api"

/** The edit-home form (D-156): the address fields and the size of step 1 of "Add your home". */
export interface HomeEditDraft {
  location: LocationDraft
  buildingName: string
  street: string
  unitLabel: string
  pincode: string
  homeSize: HomeSize | ""
  homeSizeOther: string
}

export function homeEditDraft(p: PropertyDetail): HomeEditDraft {
  return {
    location: draftFromLocation(p.location),
    buildingName: p.location?.society ? "" : (p.buildingName ?? ""),
    street: p.street ?? "",
    unitLabel: p.unitLabel ?? "",
    pincode: p.pincode ?? "",
    homeSize: p.homeSize ?? "",
    homeSizeOther: p.homeSizeOther ?? "",
  }
}

const PIN = /^[1-9]\d{5}$/
const LABEL = /[\p{L}\p{N}]/u

/** Not in a society: then the building names the home, and an apartment needs one. */
export function standaloneEdit(draft: HomeEditDraft, placed: boolean): boolean {
  return !placed || (hasArea(draft.location) && draft.location.society.kind === "none")
}

/** The add-home step 1 checks, for the fields this form has. */
export function checkHomeEdit(draft: HomeEditDraft, p: PropertyDetail): HomeErrors {
  const errors: HomeErrors = {}
  const placed = Boolean(p.location)
  if (placed && !hasArea(draft.location)) errors.area = "Pick or type the area."
  const { society, tower, wing } = draft.location
  if (society.kind === "new" && society.name.trim().length < 2)
    errors.society = "Type the society name (2 or more characters)."
  if (tower.kind === "new" && !LABEL.test(tower.name))
    errors.tower = "Type the tower or block name, e.g. 4 or C."
  if (wing.kind === "new" && !LABEL.test(wing.name))
    errors.wing = "Type the wing name, e.g. A or B."
  if (standaloneEdit(draft, placed) && p.propertyType === "APARTMENT" && !draft.buildingName.trim())
    errors.buildingName = "Enter the building name."
  if (!PIN.test(draft.pincode.trim())) errors.pincode = "Enter the 6-digit PIN code."
  if (!draft.unitLabel.trim()) errors.unitLabel = "Enter the flat or house number."
  if (draft.homeSize === "OTHER" && !draft.homeSizeOther.trim())
    errors.homeSizeOther = "Type the size, e.g. 2.5 BHK."
  return errors
}

/**
 * The `PATCH /me/homes/{id}` body. Inside a society its name is the home's building name, as when
 * the home was added; `location` is sent only when the place changed.
 */
export function toHomeUpdate(draft: HomeEditDraft, p: PropertyDetail): HomeUpdate {
  const placed = Boolean(p.location)
  const society = draft.location.society
  const buildingName =
    placed && society.kind !== "none" ? society.name.trim() : draft.buildingName.trim()
  const body: HomeUpdate = {
    pincode: draft.pincode.trim(),
    unitLabel: draft.unitLabel.trim(),
    ...(buildingName ? { buildingName } : {}),
    ...(draft.street.trim() ? { street: draft.street.trim() } : {}),
  }
  if (draft.homeSize) {
    body.homeSize = draft.homeSize
    if (draft.homeSize === "OTHER") body.homeSizeOther = draft.homeSizeOther.trim().slice(0, 40)
  }
  if (placed && !sameDraft(draft.location, draftFromLocation(p.location)))
    body.location = toLocationInput(draft.location, draft.pincode)
  return body
}

/** The API's field errors by this form's keys (the add-home names: `location.newSociety.name` → society). */
export function homeEditErrors(fieldErrors: Array<{ field: string; message: string }>): HomeErrors {
  const errors: HomeErrors = {}
  for (const { field, message } of fieldErrors) {
    const key = field === "street" ? "street" : fieldPlace(field).key
    errors[key] ??= message
  }
  return errors
}
