import type { CurrentUser } from "@/types/api"

/**
 * A same-app path from `?next=`, or the fallback. Only `/…` paths are kept (never `//host` or
 * `https://…`), so the login page cannot be used as an open redirect.
 */
export function safeNext(next: string | null | undefined, fallback = "/"): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) {
    return fallback
  }
  return next
}

/** `/login?next=…` for the page the visitor tried to open. */
export function loginPath(next: string): string {
  return next === "/" ? "/login" : `/login?next=${encodeURIComponent(next)}`
}

/** Where a fresh sign-in goes: the role pick first when it is not done (D-73). */
export function afterSignInPath(user: CurrentUser, next: string): string {
  if (!user.needsOnboarding) return next
  return next === "/" ? "/welcome" : `/welcome?next=${encodeURIComponent(next)}`
}

/** Public short name, "Asha Rao" → "Asha R." (D-28, D-55). */
export function shortName(displayName: string): string {
  const words = displayName.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return "?"
  if (words.length === 1) return words[0]
  return `${words[0]} ${words[1].charAt(0).toUpperCase()}.`
}
