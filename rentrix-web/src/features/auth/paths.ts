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
 * Public short name, "Asha Rao" → "Asha R.", "Demo Tenant BLR-1" → "Demo T." (D-28, D-55). The
 * API owns this rule (`PublicNames.shortName`) and sends it as `shortName`; the web shows that
 * as-is. This mirror exists only to preview a name the user is still typing.
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

/** The short name to show for `user`: the API's, or a preview while `typed` differs from it. */
export function publicName(user: CurrentUser, typed = user.displayName): string {
  const name = typed.trim()
  return !name || name === user.displayName ? user.shortName : shortName(name)
}
