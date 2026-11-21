import { env } from "@/lib/env"

/**
 * Single source of truth for where the backend API lives.
 *
 * Resolution order for the API origin:
 *   1. Runtime override: `window.__RENTRIX_CONFIG__.apiBaseUrl` (set by
 *      `public/config.js`, editable after build so one bundle can target
 *      different APIs).
 *   2. Build time: `VITE_API_BASE_URL`.
 *   3. Default: `http://localhost:8080` (local Spring Boot server).
 *
 * The value is the API origin (e.g. `https://api.example.com`). The server's
 * context path `/api/v1` is appended here. A value that already ends with
 * `/api/v1` (older configs) is accepted and not doubled.
 */

declare global {
  interface Window {
    __RENTRIX_CONFIG__?: { apiBaseUrl?: string }
  }
}

export const DEFAULT_API_BASE_URL = "http://localhost:8080"
export const API_PREFIX = "/api/v1"

function readRuntimeBaseUrl(): string | undefined {
  if (typeof window === "undefined") return undefined
  const value = window.__RENTRIX_CONFIG__?.apiBaseUrl
  return typeof value === "string" && value.trim() ? value.trim() : undefined
}

function normalizeOrigin(raw: string): string {
  let value = raw.trim().replace(/\/+$/, "")
  if (value.endsWith(API_PREFIX)) value = value.slice(0, -API_PREFIX.length)
  return value.replace(/\/+$/, "")
}

/** API origin without trailing slash; empty string means same origin as the page. */
export const apiOrigin = normalizeOrigin(
  readRuntimeBaseUrl() ?? env.VITE_API_BASE_URL ?? DEFAULT_API_BASE_URL,
)

/** Base URL for all REST calls, e.g. `http://localhost:8080/api/v1`. */
export const apiBaseUrl = `${apiOrigin}${API_PREFIX}`

/**
 * Resolve a URL returned by (or built against) the API. Absolute URLs
 * (http, https, protocol-relative, data:, blob:) pass through unchanged;
 * root-relative paths are resolved against the API origin.
 */
export function resolveApiUrl(url: string): string
export function resolveApiUrl(url: string | null | undefined): string | undefined
export function resolveApiUrl(url: string | null | undefined): string | undefined {
  if (!url) return undefined
  if (/^([a-z][a-z\d+\-.]*:|\/\/)/i.test(url)) return url
  return `${apiOrigin}${url.startsWith("/") ? "" : "/"}${url}`
}
