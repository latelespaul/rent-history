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
