import type { TenancyStatus } from "@/types/api"

/**
 * The server's D-39 window, changed by the open-by-default model (D-132) and the open model
 * (firstmate 2026-11-23): open from the moment the tenancy is current, current tenants too, and
 * never closed, so a home left years ago can still be reviewed.
 */
export type ReviewWindow = { state: "open" } | { state: "none" }

/** Whether a tenancy can be reviewed, as `ReviewWindow.confirmed` decides. */
export function reviewWindow(tenancy: { status: TenancyStatus }): ReviewWindow {
  return tenancy.status === "ACTIVE" || tenancy.status === "ENDED"
    ? { state: "open" }
    : { state: "none" }
}
