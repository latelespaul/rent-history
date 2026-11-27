import { apiBaseUrl } from "./config"

/**
 * Privacy-first visit counting (SYSTEM_DESIGN D-175). One tiny beacon per route change to
 * `POST /metrics/pageview` with the path, the referrer and a coarse source. No cookie, no
 * `localStorage` id, nothing that identifies the visitor; the API keeps daily totals only. Bots,
 * prerendering and visitors who send Do-Not-Track are not counted.
 */

const BOT = /bot|crawl|spider|slurp|headless|lighthouse|preview|facebookexternalhit|curl|wget/i

/** What the first view says about how the visitor arrived; later views are `internal`. */
let landingSource: string | null = null

/** True when this visit may be counted: not a bot, not prerendering, Do-Not-Track not set. */
export function shouldTrack(nav: Navigator = navigator, doc: Document = document): boolean {
  const dnt = nav.doNotTrack ?? (window as { doNotTrack?: string }).doNotTrack
  if (dnt === "1" || dnt === "yes") return false
  if (nav.webdriver || BOT.test(nav.userAgent)) return false
  const prerender = (doc as { prerendering?: boolean }).prerendering
  if (prerender || (doc.visibilityState as string) === "prerender") return false
  return true
}

/**
 * Reads how the visitor arrived, from the address, before `?ref=` is removed from it: a ref code
 * only says `invite` (the code itself is never sent); otherwise `utm_source`, if present.
 */
export function captureLanding(href: string = window.location.href): void {
  const params = new URL(href).searchParams
  const utm = params.get("utm_source")?.trim().toLowerCase().slice(0, 40)
  landingSource = params.has("ref") ? "invite" : utm || null
}

export interface PageView {
  path: string
  referrer: string
  source: string | null
}

/** Sends one page view with `sendBeacon`; failures are ignored, a lost view is fine. */
export function sendPageView(view: PageView, nav: Navigator = navigator): void {
  try {
    const blob = new Blob([JSON.stringify(view)], { type: "application/json" })
    nav.sendBeacon?.(`${apiBaseUrl()}/metrics/pageview`, blob)
  } catch {
    // Counting never breaks the page.
  }
}

/** The first view carries the landing source and the referrer; later ones are in-site. */
export function createPageViewTracker(send: (view: PageView) => void = sendPageView) {
  let first = true
  let last: string | null = null
  return (pathname: string) => {
    if (pathname === last || !shouldTrack()) return
    last = pathname
    const view: PageView = first
      ? { path: pathname, referrer: document.referrer, source: landingSource }
      : { path: pathname, referrer: "", source: "internal" }
    first = false
    send(view)
  }
}

/** Counts the current page and every later route change of a react-router data router. */
export function trackRouter(router: {
  state: { location: { pathname: string } }
  subscribe: (fn: (state: { location: { pathname: string } }) => void) => () => void
}): () => void {
  const track = createPageViewTracker()
  track(router.state.location.pathname)
  return router.subscribe((state) => track(state.location.pathname))
}
