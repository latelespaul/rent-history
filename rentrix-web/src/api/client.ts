import { apiBaseUrl } from "@/lib/config"
import type { AuthSession } from "@/types/api"
import { ApiError, CLIENT_ERROR_CODES, networkError, toApiError } from "./errors"

/**
 * The only way the web talks to the API (D-04).
 *
 * - The access token lives in this closure only, never in `localStorage` (D-62).
 * - The refresh token is an `HttpOnly` cookie on `/api/v1/auth` (D-63), so every call sends
 *   `credentials: "include"` and the client never sees it.
 * - On a `401` the client calls `POST /auth/refresh` once (concurrent 401s share that one call),
 *   then retries the original request once. If the refresh is refused (`401`/`403`), the token is
 *   cleared and, when the user was signed in, `onSessionExpired` runs. A refresh that failed for a
 *   passing reason (`409` from a concurrent write, `5xx`, offline) is tried once more first; any
 *   failure after that, or any other status (`429`, `400`, ...), keeps the token, and the caller
 *   sees the original error.
 * - Every failure rejects with an `ApiError` mapped from Problem Details (D-60).
 */

export type QueryValue = string | number | boolean | null | undefined
export type Query = Record<string, QueryValue | QueryValue[]>

export interface RequestOptions {
  query?: Query
  /** A plain object is sent as JSON; `FormData` is sent as multipart. */
  body?: unknown
  headers?: Record<string, string>
  signal?: AbortSignal
  /** Optional `Idempotency-Key` for retry-safe writes (SYSTEM_DESIGN.md §12.1). */
  idempotencyKey?: string
}

export interface ApiClientOptions {
  /** Base URL such as `/api/v1`, or a function read on every request. */
  baseUrl: string | (() => string)
  fetch?: typeof fetch
  /** Runs when a refresh is refused (401/403), i.e. the user must sign in again. */
  onSessionExpired?: () => void
}

const REFRESH_PATH = "/auth/refresh"
/** Pause before the one retry of a refresh that failed for a passing reason. */
const REFRESH_RETRY_DELAY_MS = 300
/** Auth endpoints never trigger a refresh: their 401 is the answer, not an expired token. */
const NO_REFRESH_PREFIX = "/auth/"

function buildUrl(base: string, path: string, query?: Query): string {
  const url = `${base}${path.startsWith("/") ? path : `/${path}`}`
  if (!query) return url
  const params = new URLSearchParams()
  for (const [key, raw] of Object.entries(query)) {
    for (const value of Array.isArray(raw) ? raw : [raw]) {
      if (value !== undefined && value !== null && value !== "") params.append(key, String(value))
    }
  }
  const qs = params.toString()
  return qs ? `${url}${url.includes("?") ? "&" : "?"}${qs}` : url
}

async function readBody<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T
  const text = await response.text()
  if (!text) return undefined as T
  try {
    return JSON.parse(text) as T
  } catch (cause) {
    throw new ApiError(
      {
        status: response.status,
        code: CLIENT_ERROR_CODES.invalidResponse,
        title: "Invalid response",
        detail: "The API sent a response that is not JSON.",
      },
      { cause },
    )
  }
}

export function createApiClient(options: ApiClientOptions) {
  const doFetch = options.fetch ?? ((...args: Parameters<typeof fetch>) => fetch(...args))
  const base = () => (typeof options.baseUrl === "function" ? options.baseUrl() : options.baseUrl)
  let accessToken: string | null = null
  let refreshInFlight: Promise<boolean> | null = null

  async function send(method: string, path: string, opts: RequestOptions): Promise<Response> {
    const headers: Record<string, string> = { Accept: "application/json", ...opts.headers }
    let body: BodyInit | undefined
    if (opts.body instanceof FormData) {
      body = opts.body
    } else if (opts.body !== undefined) {
      headers["Content-Type"] = "application/json"
      body = JSON.stringify(opts.body)
    }
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`
    if (opts.idempotencyKey) headers["Idempotency-Key"] = opts.idempotencyKey
    try {
      return await doFetch(buildUrl(base(), path, opts.query), {
        method,
        headers,
        body,
        credentials: "include",
        signal: opts.signal,
      })
    } catch (cause) {
      throw networkError(cause)
    }
  }

  /**
   * Calls `POST /auth/refresh` (cookie-based). Resolves true when a new token was stored. A
   * passing failure (a write that raced it, a server or network hiccup) gets one more try, so a
   * page load during another request does not come up signed out. The API's reuse grace window
   * (D-63) answers that retry even when the first try rotated the cookie but its reply was lost.
   */
  async function doRefresh(retry = true): Promise<boolean> {
    let response: Response
    try {
      response = await send("POST", REFRESH_PATH, {})
    } catch {
      // Offline during refresh: keep the old token, the caller sees the original 401.
      return retry ? retryRefresh() : false
    }
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        const wasSignedIn = accessToken !== null
        accessToken = null
        if (wasSignedIn) options.onSessionExpired?.()
        return false
      }
      const passing = response.status === 409 || response.status >= 500
      return retry && passing ? retryRefresh() : false
    }
    try {
      const session = await readBody<AuthSession | undefined>(response)
      accessToken = session?.accessToken ?? null
      return accessToken !== null
    } catch {
      return false
    }
  }

  async function retryRefresh(): Promise<boolean> {
    await new Promise((resolve) => setTimeout(resolve, REFRESH_RETRY_DELAY_MS))
    return doRefresh(false)
  }

  function refreshOnce(): Promise<boolean> {
    refreshInFlight ??= doRefresh().finally(() => {
      refreshInFlight = null
    })
    return refreshInFlight
  }

  async function request<T>(method: string, path: string, opts: RequestOptions = {}): Promise<T> {
    const sentWith = accessToken
    let response = await send(method, path, opts)
    if (response.status === 401 && !path.startsWith(NO_REFRESH_PREFIX)) {
      // Another call may already have refreshed while this one was in flight: just retry.
      const fresh = accessToken !== null && accessToken !== sentWith
      if (fresh || (await refreshOnce())) response = await send(method, path, opts)
    }
    if (!response.ok) throw await toApiError(response)
    return readBody<T>(response)
  }

  return {
    request,
    get: <T>(path: string, opts?: Omit<RequestOptions, "body">) => request<T>("GET", path, opts),
    post: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
      request<T>("POST", path, { ...opts, body }),
    put: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
      request<T>("PUT", path, { ...opts, body }),
    patch: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
      request<T>("PATCH", path, { ...opts, body }),
    delete: <T = void>(path: string, opts?: RequestOptions) => request<T>("DELETE", path, opts),

    /** Store the access token from a sign-in, onboarding or role change response (D-73). */
    setAccessToken(token: string | null) {
      accessToken = token
    },
    getAccessToken: () => accessToken,
    /** Forget the in-memory token, e.g. after `POST /auth/logout`. */
    clearSession() {
      accessToken = null
    },
    /** On page load: trade the refresh cookie for an access token. Resolves true if signed in. */
    restoreSession: () => refreshOnce(),
  }
}

export type ApiClient = ReturnType<typeof createApiClient>

let sessionExpiredHandler: (() => void) | null = null

/** Lets the auth layer (W3) react when the app-wide client's refresh is refused. */
export function setSessionExpiredHandler(handler: (() => void) | null) {
  sessionExpiredHandler = handler
}

/** The app-wide client. The base URL is read per request, so `public/config.js` always wins. */
export const api = createApiClient({
  baseUrl: apiBaseUrl,
  onSessionExpired: () => sessionExpiredHandler?.(),
})
