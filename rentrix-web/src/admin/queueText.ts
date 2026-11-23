import type { QueueAction, QueueItem, QueueType } from "./api"

/** Tab labels of the approvals queue, in the order the tabs show. Reports have their own page. */
export const QUEUE_TABS: { type: QueueType; label: string }[] = [
  { type: "USER", label: "Users" },
  { type: "IDENTITY", label: "ID checks" },
  { type: "PROPERTY", label: "Homes" },
  { type: "TENANCY", label: "Tenancies" },
  { type: "REVIEW", label: "Reviews" },
  { type: "CITY", label: "Cities" },
]

/** The type badge on a card. */
export const TYPE_LABEL: Record<QueueType, string> = {
  USER: "User",
  IDENTITY: "ID check",
  PROPERTY: "Home",
  TENANCY: "Tenancy",
  REVIEW: "Review",
  CITY: "New city",
  REPORT: "Report",
}

const REASON_LABEL: Record<string, string> = {
  CANNOT_CONFIRM: "Can't confirm",
  DETAILS_WRONG: "Details look wrong",
  DUPLICATE: "Duplicate",
  ABUSE: "Abuse",
  PERSONAL_DATA: "Personal data",
  OTHER: "Other",
  ID_UNREADABLE: "ID unreadable",
  ID_NAME_MISMATCH: "Name doesn't match",
  AADHAAR_NOT_MASKED: "Aadhaar not masked",
  LINKEDIN_MISMATCH: "LinkedIn doesn't match",
  NO_VERIFIED_ID: "No verified ID",
  DOCUMENT_MISSING: "Document missing",
  DUPLICATE_ACCOUNT: "Duplicate account",
  AADHAAR_MASKED_ON_INTAKE: "Aadhaar typed, masked",
  AGREEMENT_REUSED: "Agreement used on another tenancy",
}

/** Plain words for a reason code; unknown codes are shown as "Some code". */
export function reasonLabel(code: string): string {
  if (REASON_LABEL[code]) return REASON_LABEL[code]
  const words = code.replace(/_/g, " ").toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

/** Hiding is for abuse only (D-51), so it offers only the abuse reasons. */
const HIDE_REASONS = ["ABUSE", "PERSONAL_DATA", "OTHER"]

/** The reasons one answer offers: every reject code for a reject, the abuse ones for a hide. */
export function reasonsFor(item: QueueItem, action: Exclude<QueueAction, "APPROVE">): string[] {
  if (action === "REJECT")
    return item.rejectCodes.filter((c) => c !== "ABUSE" && c !== "PERSONAL_DATA")
  return HIDE_REASONS.filter((c) => item.rejectCodes.includes(c))
}

/** The approve button's words: a city goes live, a report is dismissed, everything else gets the tick. */
export function approveLabel(type: QueueType): string {
  if (type === "CITY") return "Set live"
  if (type === "REPORT") return "Dismiss"
  return "Approve"
}

/** The toast after an answer. */
export function doneMessage(type: QueueType, action: QueueAction, brand: string): string {
  if (action === "HIDE") return "Hidden from the site"
  if (action === "REJECT") return "Rejected · it stays visible with no tick"
  if (type === "CITY") return "City is live"
  if (type === "REPORT") return "Report dismissed"
  return `Approved · Verified by ${brand}`
}

/** "5 min ago", "3 h ago", "2 d ago". */
export function ago(iso: string | null, now: number): string {
  if (!iso) return ""
  const minutes = Math.max(0, Math.round((now - Date.parse(iso)) / 60_000))
  if (!Number.isFinite(minutes)) return ""
  if (minutes < 1) return "just now"
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  return `${Math.round(hours / 24)} d ago`
}
