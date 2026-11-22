import type { ProblemFieldError } from "@/types/api"
import { wholeNumber } from "./labels"

/** The request fields of the tenancy form, as the API names them in field errors. */
export type TenancyField =
  "propertyId" | "counterpartyEmail" | "startDate" | "endDate" | "initialRentInr" | "depositInr"

export type TenancyFieldErrors = Partial<Record<TenancyField, string>>

export interface TenancyFormValues {
  /** The landlord invites by email; a tenant sends no email. */
  asLandlord: boolean
  email: string
  startDate: string
  endDate: string
  rent: string
  deposit: string
}

const RENT_NUMBER = "Enter the rent as a whole number of rupees, like 25000."
const DEPOSIT_NUMBER = "Enter the deposit as a whole number of rupees, or leave it empty."

/** Client checks before sending; the API's own checks stay the source of truth (D-60). */
export function checkTenancyForm(values: TenancyFormValues): TenancyFieldErrors {
  const errors: TenancyFieldErrors = {}
  if (values.asLandlord && !values.email.trim()) {
    errors.counterpartyEmail = "Enter the tenant's email address."
  }
  if (!values.startDate) errors.startDate = "Enter the start date."
  if (values.startDate && values.endDate && values.endDate < values.startDate) {
    errors.endDate = "The end date must be on or after the start date."
  }
  const rent = wholeNumber(values.rent)
  if (rent === undefined) errors.initialRentInr = "Enter the monthly rent."
  else if (Number.isNaN(rent)) errors.initialRentInr = RENT_NUMBER
  else if (rent < 1) errors.initialRentInr = "Enter a monthly rent above ₹0."
  const deposit = wholeNumber(values.deposit)
  if (deposit !== undefined && (Number.isNaN(deposit) || deposit < 0)) {
    errors.depositInr = DEPOSIT_NUMBER
  }
  return errors
}

/** Plain words for the bean-validation codes the API sends per field (`not_null`, `email`, …). */
const FIELD_COPY: Record<TenancyField, { missing: string; invalid: string }> = {
  propertyId: { missing: "Pick the home.", invalid: "Pick the home again." },
  counterpartyEmail: {
    missing: "Enter the tenant's email address.",
    invalid: "Enter a valid email address, like name@example.com.",
  },
  startDate: { missing: "Enter the start date.", invalid: "Enter a valid start date." },
  endDate: {
    missing: "Enter the end date.",
    invalid: "Enter a valid end date, or leave it empty.",
  },
  initialRentInr: { missing: "Enter the monthly rent.", invalid: RENT_NUMBER },
  depositInr: { missing: DEPOSIT_NUMBER, invalid: DEPOSIT_NUMBER },
}

const MISSING_CODES = new Set(["not_null", "not_blank", "not_empty", "required"])
const VALIDATION_CODES = new Set([
  ...MISSING_CODES,
  "email",
  "size",
  "min",
  "max",
  "positive",
  "positive_or_zero",
  "past",
  "past_or_present",
  "pattern",
  "invalid",
])

/**
 * The message to show under a field for an API field error. Bean-validation messages ("must not
 * be null") are replaced by the form's own words; a rule the API explains itself (e.g.
 * `not_allowed`) keeps the API's message.
 */
export function friendlyFieldError(error: ProblemFieldError): string {
  const copy = FIELD_COPY[error.field as TenancyField]
  if (!copy || !VALIDATION_CODES.has(error.code)) return error.message
  return MISSING_CODES.has(error.code) ? copy.missing : copy.invalid
}
