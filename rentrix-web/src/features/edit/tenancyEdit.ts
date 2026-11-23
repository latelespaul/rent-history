import { checkStep, EMPTY_HOME, type HomeErrors } from "@/features/home/addHome"
import { wholeNumber } from "@/features/tenancy/labels"
import type { Tenancy, TenancyUpdate } from "@/types/api"

/** The edit form's fields, as the add-home tenancy step names them. */
export interface TenancyDraft {
  startDate: string
  stillLiving: boolean
  endDate: string
  rent: string
  deposit: string
  /** "I rent / rented it" or "I own it", as picked at add-home. */
  relation: "RENT" | "OWN"
}

export function tenancyDraft(t: Tenancy): TenancyDraft {
  return {
    relation: t.myRole === "LANDLORD" ? "OWN" : "RENT",
    startDate: t.startDate,
    stillLiving: !t.endDate,
    endDate: t.endDate ?? "",
    rent: t.initialRentInr === null ? "" : String(t.initialRentInr),
    deposit: t.depositInr === null ? "" : String(t.depositInr),
  }
}

/** A tenant's own record may leave the rent out ("Rent not shared", D-142); every other one needs it. */
export function rentOptional(t: Tenancy): boolean {
  return t.initiatedBy === "TENANT" && !t.confirmedAt
}

/** The add-home tenancy step's checks (D-156 reuses them), plus the rent when it is required. */
export function checkTenancyDraft(draft: TenancyDraft, t: Tenancy, today: string): HomeErrors {
  const errors = checkStep("tenancy", { ...EMPTY_HOME, ...draft, relation: "RENT" }, today)
  if (!errors.rent && !rentOptional(t) && wholeNumber(draft.rent) === undefined)
    errors.rent = "Enter the monthly rent."
  return errors
}

export function toTenancyUpdate(draft: TenancyDraft): TenancyUpdate {
  return {
    startDate: draft.startDate,
    endDate: draft.stillLiving || !draft.endDate ? null : draft.endDate,
    initialRentInr: wholeNumber(draft.rent) ?? null,
    depositInr: wholeNumber(draft.deposit) ?? null,
    relation: draft.relation,
  }
}

/** Whether the draft switches the caller's role on the home (D-156): it then removes the tenancy. */
export function switchesRole(draft: TenancyDraft, t: Tenancy): boolean {
  return draft.relation !== tenancyDraft(t).relation
}

/** The API names the fields of `PATCH /tenancies/{id}`; the form shows them by its own keys. */
export function tenancyErrorKey(field: string): string {
  if (field === "initialRentInr") return "rent"
  if (field === "depositInr") return "deposit"
  if (field === "startDate" || field === "endDate") return field
  return "form"
}
