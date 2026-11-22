import type { TenancyStatus } from "@/types/api"

/** The server's D-39 window: it opens 60 days after moving in (or at moving out), closes 180 days after. */
export const OPENS_AFTER_DAYS = 60
export const CLOSES_AFTER_DAYS = 180

export type ReviewWindow =
  | { state: "open"; closes: string | null }
  | { state: "not_yet"; opens: string }
  | { state: "closed"; closes: string }
  | { state: "none" }

function plusDays(date: string, days: number): string {
  const d = new Date(`${date.slice(0, 10)}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

/** Whether a tenancy can be reviewed on `today` (`YYYY-MM-DD`), as `ReviewWindow.isOpen` decides. */
export function reviewWindow(
  tenancy: { status: TenancyStatus; startDate: string; endDate?: string | null },
  today: string,
): ReviewWindow {
  if (tenancy.status !== "ACTIVE" && tenancy.status !== "ENDED") return { state: "none" }
  const end = tenancy.status === "ENDED" && tenancy.endDate ? tenancy.endDate.slice(0, 10) : null
  const afterSixty = plusDays(tenancy.startDate, OPENS_AFTER_DAYS)
  const opens = end && end < afterSixty ? end : afterSixty
  const closes = end ? plusDays(end, CLOSES_AFTER_DAYS) : null
  if (today < opens) return { state: "not_yet", opens }
  if (closes && today > closes) return { state: "closed", closes }
  return { state: "open", closes }
}
