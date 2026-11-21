/**
 * Where the API lives (D-04). One static build can point at any API:
 *
 * 1. `window.__RENTRIX_CONFIG__.apiBaseUrl`, set by `public/config.js`. The host replaces that
 *    one file at deploy time (no rebuild), so the same `dist/` works for every environment.
 * 2. `VITE_API_BASE_URL`, baked in at build time.
 * 3. `/api/v1`: same origin; in dev the Vite proxy forwards it to the API.
 */
export interface RuntimeConfig {
  apiBaseUrl?: string
}

declare global {
  interface Window {
    __RENTRIX_CONFIG__?: RuntimeConfig
  }
}

export const DEFAULT_API_BASE_URL = "/api/v1"

/** Picks the first non-blank value and drops trailing slashes. */
export function resolveApiBaseUrl(runtime?: string, buildTime?: string): string {
  const value = [runtime, buildTime].map((v) => v?.trim()).find((v) => v) ?? DEFAULT_API_BASE_URL
  return value.replace(/\/+$/, "")
}

export function apiBaseUrl(): string {
  const runtime = typeof window === "undefined" ? undefined : window.__RENTRIX_CONFIG__?.apiBaseUrl
  return resolveApiBaseUrl(runtime, import.meta.env.VITE_API_BASE_URL)
}
