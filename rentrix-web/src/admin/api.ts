import { createApiClient } from "@/api/client"
import { adminApiBaseUrl } from "@/lib/config"
import type { AuthConfig, AuthSession, CurrentUser } from "@/types/api"

/**
 * The admin console's own API client (SYSTEM_DESIGN §13.3). It talks to the same API as the main
 * site, but only from the admin host, with the admin session: its refresh is
 * `POST /auth/admin/refresh` (the `rentrix_admin_refresh` cookie), so it never touches the main
 * site's session.
 */

let sessionExpiredHandler: (() => void) | null = null

export function setAdminSessionExpiredHandler(handler: (() => void) | null) {
  sessionExpiredHandler = handler
}

export const adminApi = createApiClient({
  baseUrl: adminApiBaseUrl,
  refreshPath: "/auth/admin/refresh",
  onSessionExpired: () => sessionExpiredHandler?.(),
})

export const adminKeys = {
  config: ["admin", "auth-config"] as const,
  devUsers: ["admin", "dev-users"] as const,
  smsRelay: ["admin", "sms-relay"] as const,
  queue: ["admin", "queue"] as const,
  editHistory: (type: string, id: string) => ["admin", "edit-history", type, id] as const,
}

/** `GET /auth/config`: the Google client id and whether dev login exists. */
export function fetchAdminAuthConfig(signal?: AbortSignal): Promise<AuthConfig> {
  return adminApi.get<AuthConfig>("/auth/config", { signal })
}

/** `POST /auth/admin/google`: only emails in `ADMIN_CONSOLE_EMAILS` get a session. */
export function adminSignInWithGoogle(idToken: string): Promise<AuthSession> {
  return adminApi.post<AuthSession>("/auth/admin/google", { idToken })
}

/** `GET /auth/admin/dev-users`: the seed admins (dev profile only). */
export function fetchAdminDevUsers(signal?: AbortSignal): Promise<CurrentUser[]> {
  return adminApi.get<CurrentUser[]>("/auth/admin/dev-users", { signal })
}

/** `POST /auth/admin/dev-login`: sign in to the console as a seed admin (dev profile only). */
export function adminDevLogin(userId: string): Promise<AuthSession> {
  return adminApi.post<AuthSession>("/auth/admin/dev-login", { userId })
}

/** `POST /auth/admin/logout`: ends the admin session and clears its cookie. */
export function adminLogout(): Promise<void> {
  return adminApi.post<void>("/auth/admin/logout")
}

/** `GET /me`: who the admin session belongs to. */
export function fetchAdminMe(signal?: AbortSignal): Promise<CurrentUser> {
  return adminApi.get<CurrentUser>("/me", { signal })
}

/** One phone code held by the `admin-relay` SMS sender (D-137); the phone is masked. */
export interface RelayedCode {
  userId: string | null
  displayName: string | null
  email: string | null
  phone: string
  code: string
  sentAt: string
  expiresAt: string
}

/** `GET /admin/sms-relay`: the codes waiting to be passed on, newest first (`404` when off). */
export function fetchSmsRelay(signal?: AbortSignal): Promise<RelayedCode[]> {
  return adminApi.get<RelayedCode[]>("/admin/sms-relay", { signal })
}

export type QueueType = "USER" | "IDENTITY" | "PROPERTY" | "TENANCY" | "REVIEW" | "CITY" | "REPORT"
export type QueueAction = "APPROVE" | "REJECT" | "HIDE"

/** One approvals-queue card (D-136). */
export interface QueueItem {
  type: QueueType
  id: string
  title: string
  subtitle: string
  details: string[]
  flags: string[]
  city: string | null
  createdAt: string | null
  verificationStatus: "UNVERIFIED" | "VERIFIED" | "REJECTED"
  link: string | null
  actions: QueueAction[]
  rejectCodes: string[]
}

export interface QueueView {
  items: QueueItem[]
  counts: Partial<Record<QueueType, number>>
}

export interface QueueDecided {
  type: QueueType
  id: string
  outcome: string
}

/** `GET /admin/queue`: everything waiting for the green tick, newest first. */
export function fetchQueue(
  filter: { type?: QueueType | null; city?: string | null },
  signal?: AbortSignal,
): Promise<QueueView> {
  return adminApi.get<QueueView>("/admin/queue", {
    query: { type: filter.type ?? undefined, city: filter.city ?? undefined },
    signal,
  })
}

const ITEM_PATH: Partial<Record<QueueType, string>> = {
  USER: "applications",
  IDENTITY: "identity",
  PROPERTY: "properties",
  TENANCY: "tenancies",
  REVIEW: "reviews",
}

/**
 * One answer to a queue card, on the §12.2 route of its type: `/admin/{applications|identity|
 * properties|tenancies|reviews}/{id}/{approve|verify|reject|hide}`, `PATCH /admin/cities/{code}`
 * and `/admin/reports/{id}/resolve`. A reject or hide sends the reason code; the note is optional,
 * except that a user's rejection always carries one (B11), so the reason's words go when it is empty.
 */
export function decideQueueItem(
  item: Pick<QueueItem, "type" | "id">,
  action: QueueAction,
  body: { code?: string; note?: string; noteFallback?: string } = {},
): Promise<QueueDecided> {
  const id = encodeURIComponent(item.id)
  const { code, note, noteFallback } = body
  if (item.type === "CITY") {
    return adminApi.patch<QueueDecided>(`/admin/cities/${id}`, { live: true })
  }
  if (item.type === "REPORT") {
    return adminApi.post<QueueDecided>(`/admin/reports/${id}/resolve`, {
      outcome: action === "HIDE" ? "HIDE" : "DISMISS",
      code,
      note,
    })
  }
  const verb =
    action === "APPROVE" ? (item.type === "IDENTITY" ? "verify" : "approve") : action.toLowerCase()
  const path = `/admin/${ITEM_PATH[item.type]}/${id}/${verb}`
  if (action === "APPROVE") return adminApi.post<QueueDecided>(path, { note })
  if (item.type === "USER") {
    return adminApi.post<QueueDecided>(path, { code, note: note ?? noteFallback ?? code })
  }
  return adminApi.post<QueueDecided>(path, { code, note })
}

/** One field an owner changed on their own entry (D-156); `editRef` groups the rows of one save. */
export interface EditHistoryRow {
  editRef: string
  at: string
  editorId: string
  editorName: string
  change: "EDITED" | "DELETED"
  field: string
  before: string | null
  after: string | null
  /** The item had the green tick then; the change took it away. */
  wasVerified: boolean
}

/** `GET /admin/edit-history?type=&id=`: what the owner changed on one item, newest first. */
export function fetchEditHistory(
  type: "TENANCY" | "PROPERTY" | "REVIEW",
  id: string,
  signal?: AbortSignal,
): Promise<EditHistoryRow[]> {
  return adminApi.get<EditHistoryRow[]>("/admin/edit-history", { query: { type, id }, signal })
}
