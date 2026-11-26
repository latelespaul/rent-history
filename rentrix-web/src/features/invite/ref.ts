/**
 * The invite code a visitor arrived with (design note `invite-friends.md` §3). It is kept in
 * `localStorage` for 30 days, so it survives the Google popup, a closed tab and the trip to
 * `/login`; it is sent once with the next sign-in and then dropped, whatever the API did with it.
 * If storage is blocked, the code lives in memory for this tab only.
 */
const KEY = "rx-ref"
const MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000
const CODE = /^[0-9A-HJKMNP-TV-Z]{10}$/

let memory: { code: string; at: number } | null = null

/** A well-formed code, upper-cased, or null (the API ignores anything else too). */
export function normaliseRef(value: string | null | undefined): string | null {
  const code = value?.trim().toUpperCase() ?? ""
  return CODE.test(code) ? code : null
}

/**
 * Keeps `?ref=` from the address and removes it from the address bar, so the code is not copied
 * on with the page link or shown in a screenshot. A newer code replaces an older one.
 */
export function keepRefFromUrl(win: Window = window): void {
  const url = new URL(win.location.href)
  if (!url.searchParams.has("ref")) return
  const code = normaliseRef(url.searchParams.get("ref"))
  if (code) {
    memory = { code, at: Date.now() }
    try {
      win.localStorage.setItem(KEY, JSON.stringify(memory))
    } catch {
      // Memory only, for this tab.
    }
  }
  url.searchParams.delete("ref")
  win.history.replaceState(win.history.state, "", url.pathname + url.search + url.hash)
}

/** The kept code, if it is younger than 30 days. */
export function readRef(now = Date.now()): string | null {
  let kept = memory
  try {
    const raw = window.localStorage.getItem(KEY)
    if (raw) kept = JSON.parse(raw) as { code: string; at: number }
  } catch {
    // Fall back to memory.
  }
  if (!kept || typeof kept.at !== "number" || now - kept.at > MAX_AGE_MS) return null
  return normaliseRef(kept.code)
}

/** Drops the kept code (after any successful sign-in). */
export function clearRef(): void {
  memory = null
  try {
    window.localStorage.removeItem(KEY)
  } catch {
    // Nothing kept.
  }
}
