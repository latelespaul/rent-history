import { useLocation } from "react-router-dom"
import { isApiError } from "@/api/errors"

/**
 * The API's gate code for trust writes (D-117, D-132): the caller has no verified phone. Since
 * open by default the API no longer answers `approval_required`.
 */
export const GATE_CODES = ["id_verification_required"] as const

export function isGateError(error: unknown): boolean {
  return isApiError(error) && (GATE_CODES as readonly string[]).includes(error.code)
}

/** `/verify/phone?next=<this page>`: the phone step, then back to what the user was doing (D-119). */
export function usePhoneStepHref(): string {
  const location = useLocation()
  return `/verify/phone?next=${encodeURIComponent(`${location.pathname}${location.search}`)}`
}
