import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios"
import { env } from "@/lib/env"

interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

export const api = axios.create({
  baseURL: env.VITE_API_BASE_URL,
  headers: { "Content-Type": "application/json" },
  timeout: 15000,
})

/**
 * Token storage — kept in module scope to avoid circular deps with Zustand.
 * Zustand store will set these on login/refresh, clear on logout.
 */
let accessToken: string | null = null
let refreshToken: string | null = null
let onUnauthorized: (() => void) | null = null

export const tokenStore = {
  set(access: string | null, refresh: string | null) {
    accessToken = access
    refreshToken = refresh
  },
  getAccess() {
    return accessToken
  },
  getRefresh() {
    return refreshToken
  },
  clear() {
    accessToken = null
    refreshToken = null
  },
  setOnUnauthorized(fn: () => void) {
    onUnauthorized = fn
  },
}

// Request interceptor — attach access token
api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

// Response interceptor — refresh on 401, retry once
let refreshPromise: Promise<string> | null = null

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as RetryConfig | undefined

    if (
      error.response?.status === 401 &&
      original &&
      !original._retry &&
      refreshToken &&
      !original.url?.includes("/auth/refresh")
    ) {
      original._retry = true

      try {
        // De-dupe concurrent refreshes
        if (!refreshPromise) {
          refreshPromise = axios
            .post<{ accessToken: string }>(`${env.VITE_API_BASE_URL}/auth/refresh`, {
              refreshToken,
            })
            .then((r) => {
              const newAccess = r.data.accessToken
              tokenStore.set(newAccess, refreshToken)
              return newAccess
            })
            .finally(() => {
              refreshPromise = null
            })
        }

        const newAccess = await refreshPromise
        original.headers.Authorization = `Bearer ${newAccess}`
        return api(original)
      } catch {
        tokenStore.clear()
        onUnauthorized?.()
        return Promise.reject(error)
      }
    }

    return Promise.reject(error)
  },
)
