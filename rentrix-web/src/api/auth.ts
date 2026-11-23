import type {
  AuthConfig,
  AuthSession,
  CurrentUser,
  MeUpdate,
  OnboardingRequest,
  UpdateMeRequest,
} from "@/types/api"
import { api } from "./client"

export const authKeys = {
  config: ["auth", "config"] as const,
  devUsers: ["auth", "dev-users"] as const,
}

/** `GET /auth/config`: the Google client id (or null) and whether dev login exists (D-76, D-77). */
export function fetchAuthConfig(signal?: AbortSignal): Promise<AuthConfig> {
  return api.get<AuthConfig>("/auth/config", { signal })
}

/** `POST /auth/google` with the GIS ID token; the API verifies it and starts a session (D-70). */
export function signInWithGoogle(idToken: string): Promise<AuthSession> {
  return api.post<AuthSession>("/auth/google", { idToken })
}

/** `GET /auth/dev-users`: the demo seed users (dev profile only, D-77). */
export function fetchDevUsers(signal?: AbortSignal): Promise<CurrentUser[]> {
  return api.get<CurrentUser[]>("/auth/dev-users", { signal })
}

/** `POST /auth/dev-login`: sign in as a demo seed user, or one `dev-login/new` made (dev profile only, D-77). */
export function devLogin(userId: string): Promise<AuthSession> {
  return api.post<AuthSession>("/auth/dev-login", { userId })
}

/** `POST /auth/dev-login/new`: sign in as a brand-new user with no role yet (dev profile only). */
export function devLoginAsNewUser(): Promise<AuthSession> {
  return api.post<AuthSession>("/auth/dev-login/new")
}

/** `POST /auth/logout`: revokes the refresh token and clears its cookie. */
export function logout(): Promise<void> {
  return api.post<void>("/auth/logout")
}

/** `GET /me`: the signed-in user's own account. */
export function fetchMe(signal?: AbortSignal): Promise<CurrentUser> {
  return api.get<CurrentUser>("/me", { signal })
}

/** `POST /me/onboarding`: first sign-in role pick (D-73). The reply carries a fresh token. */
export function completeOnboarding(body: OnboardingRequest): Promise<MeUpdate> {
  return api.post<MeUpdate>("/me/onboarding", body)
}

/**
 * `DELETE /me` (D-58, D-157): deletes the account at once. `confirm` must be `DELETE`; a sign-in older than
 * 10 minutes is `403 reauth_required`. The reply clears the refresh cookie.
 */
export function deleteAccount(confirm: string): Promise<void> {
  return api.delete<void>("/me", { body: { confirm } })
}

/** `PATCH /me`: profile edits and role adds (D-16). The reply carries a fresh token. */
export function updateMe(body: UpdateMeRequest): Promise<MeUpdate> {
  return api.patch<MeUpdate>("/me", body)
}
