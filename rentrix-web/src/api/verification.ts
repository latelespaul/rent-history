import type { IdentityView, PhoneChallenge, Role, VerificationEntry } from "@/types/api"
import { api } from "./client"

export const verificationKeys = {
  all: ["me", "verification"] as const,
  identity: ["me", "identity"] as const,
}

/** `GET /me/verification`: the stepper's state per held role (D-120). */
export function fetchVerification(signal?: AbortSignal): Promise<VerificationEntry[]> {
  return api.get<VerificationEntry[]>("/me/verification", { signal })
}

/** `POST /me/verification/{role}/submit`: "Request verification" (D-133). */
export function requestVerification(role: Exclude<Role, "ADMIN">): Promise<VerificationEntry> {
  return api.post<VerificationEntry>(`/me/verification/${role}/submit`)
}

/** `GET /me/identity`: the caller's checks, masked only (D-106). */
export function fetchIdentity(signal?: AbortSignal): Promise<IdentityView> {
  return api.get<IdentityView>("/me/identity", { signal })
}

/** `POST /me/identity/phone/start`: sends a 6-digit code by SMS (D-101). */
export function startPhoneCheck(phone: string): Promise<PhoneChallenge> {
  return api.post<PhoneChallenge>("/me/identity/phone/start", { phone })
}

/** `POST /me/identity/phone/confirm`: checks the code; the reply is the new `GET /me/identity`. */
export function confirmPhoneCheck(challengeId: string, code: string): Promise<IdentityView> {
  return api.post<IdentityView>("/me/identity/phone/confirm", { challengeId, code })
}

/** `PUT /me/identity/linkedin`: queues the profile URL for an admin (D-93). */
export function saveLinkedin(url: string): Promise<IdentityView> {
  return api.put<IdentityView>("/me/identity/linkedin", { url })
}

/** `DELETE /me/identity/linkedin`. */
export function removeLinkedin(): Promise<void> {
  return api.delete<void>("/me/identity/linkedin")
}
