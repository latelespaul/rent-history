import type { AgreementFields, RentalAgreement } from "@/types/api"
import { wholeNumber } from "./labels"

/** What the API accepts (D-67, D-131): PDF, JPEG, PNG or HEIC, at most 10 MB. */
export const MAX_FILE_BYTES = 10 * 1024 * 1024
export const FILE_ACCEPT =
  "application/pdf,image/jpeg,image/png,image/heic,.pdf,.jpg,.jpeg,.png,.heic"
const FILE_EXTENSION = /\.(pdf|jpe?g|png|heic)$/i

/** The consent the uploader ticks before the file is sent (D-131). */
export const CONSENT_TEXT =
  "I agree that Doorway Living stores this agreement encrypted, shows it only to the other party of this tenancy and to the Doorway Living team, uses it to verify this tenancy, and deletes the file 30 days after the team's check and the other party's confirmation (or 90 days after upload if the team has not checked it by then)."

/** The form as typed: every field a string, so nothing jumps while the user types. */
export interface AgreementForm {
  landlordName: string
  tenantName: string
  addressText: string
  pincode: string
  monthlyRent: string
  deposit: string
  startDate: string
  endDate: string
  anchorNumber: string
}

export const EMPTY_AGREEMENT_FORM: AgreementForm = {
  landlordName: "",
  tenantName: "",
  addressText: "",
  pincode: "",
  monthlyRent: "",
  deposit: "",
  startDate: "",
  endDate: "",
  anchorNumber: "",
}

/** Errors keyed like the API's field errors: `file`, `consent`, `fields.<name>`. */
export type AgreementErrors = Partial<Record<string, string>>

/** The plain-word checks the API also makes, so each field says what is wrong before sending. */
export function checkAgreementForm(
  form: AgreementForm,
  file: File | null,
  consent: boolean,
  needFile = true,
): AgreementErrors {
  const errors: AgreementErrors = {}
  if (needFile) {
    if (!file) errors.file = "Choose the agreement file: a PDF, or a JPEG, PNG or HEIC photo."
    else if (file.size > MAX_FILE_BYTES) errors.file = "The file is larger than 10 MB."
    else if (
      !FILE_EXTENSION.test(file.name) &&
      !/^(application\/pdf|image\/(jpeg|png|heic))$/.test(file.type)
    )
      errors.file = "Upload a PDF, or a JPEG, PNG or HEIC photo."
    if (!consent) errors.consent = "Tick the box to agree before you upload."
  }
  if (!form.landlordName.trim())
    errors["fields.landlordName"] = "Type the owner's name as the agreement shows it."
  if (!form.tenantName.trim())
    errors["fields.tenantName"] = "Type the tenant's name as the agreement shows it."
  const rent = wholeNumber(form.monthlyRent)
  if (rent === undefined || Number.isNaN(rent) || rent <= 0)
    errors["fields.monthlyRentInr"] = "Enter the monthly rent from the agreement."
  const deposit = wholeNumber(form.deposit)
  if (deposit !== undefined && (Number.isNaN(deposit) || deposit < 0))
    errors["fields.depositInr"] = "Enter the deposit in rupees, or leave it empty."
  if (form.pincode.trim() && !/^[1-9][0-9]{5}$/.test(form.pincode.trim()))
    errors["fields.pincode"] = "Enter a 6-digit PIN code."
  if (!form.startDate) errors["fields.startDate"] = "Enter the start date from the agreement."
  if (form.startDate && form.endDate && form.endDate < form.startDate)
    errors["fields.endDate"] = "The end date must be on or after the start date."
  return errors
}

/** The form as the API's `fields` object. */
export function toFields(form: AgreementForm): AgreementFields {
  const blank = (s: string) => (s.trim() ? s.trim() : null)
  return {
    landlordName: form.landlordName.trim(),
    tenantName: form.tenantName.trim(),
    addressText: blank(form.addressText),
    pincode: blank(form.pincode),
    monthlyRentInr: wholeNumber(form.monthlyRent) ?? 0,
    depositInr: wholeNumber(form.deposit) ?? null,
    startDate: form.startDate,
    endDate: form.endDate || null,
    anchorNumber: blank(form.anchorNumber),
  }
}

/** A stored agreement back into the form, to correct it. */
export function toForm(agreement: RentalAgreement): AgreementForm {
  const f = agreement.fields
  return {
    landlordName: f.landlordName ?? "",
    tenantName: f.tenantName ?? "",
    addressText: f.addressText ?? "",
    pincode: f.pincode ?? "",
    monthlyRent: f.monthlyRentInr ? String(f.monthlyRentInr) : "",
    deposit: f.depositInr != null ? String(f.depositInr) : "",
    startDate: f.startDate ?? "",
    endDate: f.endDate ?? "",
    anchorNumber: f.anchorNumber ?? "",
  }
}
