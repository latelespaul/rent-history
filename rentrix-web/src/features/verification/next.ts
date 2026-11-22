import { useSearchParams } from "react-router-dom"
import { safeNext } from "@/features/auth/paths"
import type { VerificationEntry } from "@/types/api"

const ROLE_WORD = { TENANT: "tenant", LANDLORD: "landlord" } as const

/** `?next=` of the page the user came from, kept through every step (D-119); null without one. */
export function useNext(): string | null {
  const [params] = useSearchParams()
  const next = params.get("next")
  return next ? safeNext(next, "") || null : null
}

/** `path` with the current `?next=` carried along. */
export function useWithNext(): (path: string) => string {
  const next = useNext()
  return (path) => (next ? `${path}?next=${encodeURIComponent(next)}` : path)
}

/** The role words for the heading: "tenant", "landlord" or "tenant and landlord". */
export function roleWords(entries: VerificationEntry[]): string {
  return entries.map((entry) => ROLE_WORD[entry.role]).join(" and ")
}
