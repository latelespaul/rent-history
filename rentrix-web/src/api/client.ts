import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios"
import { env } from "@/lib/env"

interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

export const api = axios.create({
  baseURL: env.VITE_API_BASE_URL,
  headers: { "Content-Type": "application/json" },
  // 90s — Render free tier can take up to ~130s to cold start; this
  // gives the request a fighting chance once the container wakes up.
  timeout: 90000,
})

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

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

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