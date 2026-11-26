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

/** A name word: letters, with apostrophes, hyphens or dots inside; any digit makes it a code. */
const NAME_WORD = /^\p{L}[\p{L}\p{M}'’.-]*$/u

/**
 * Short name, "Asha Rao" → "Asha R.", "Demo Tenant BLR-1" → "Demo T.": shown only to the user
 * and admins, never to other people (D-168). The API owns this rule (`PublicNames.shortName`) and
 * sends it as `shortName`; this mirror only builds test users.
 */
export function shortName(displayName: string): string {
  const words = displayName.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return "Member"
  const last = words
    .slice(1)
    .reverse()
    .find((w) => NAME_WORD.test(w))
  if (!last) return words[0]
  return `${words[0]} ${String.fromCodePoint(last.codePointAt(0)!).toUpperCase()}.`
}

/**
 * The hint under every name field (D-168): other people never see a name, only an anonymous
 * label such as "Verified tenant" or "Owner", never a year (D-169).
 */
export const NAME_NOT_SHOWN =
  "Never shown to other people. Your reviews show “Verified tenant” or “Owner”, never your name or the dates of your stay."
