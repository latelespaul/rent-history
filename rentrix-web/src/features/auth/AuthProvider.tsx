import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { fetchMe, logout } from "@/api/auth"
import { api, setSessionExpiredHandler } from "@/api/client"
import type { AuthSession, MeUpdate } from "@/types/api"
import { AuthContext, type AuthContextValue, type AuthState } from "./authContext"

const ANONYMOUS: AuthState = { status: "anonymous", user: null }

/**
 * Holds who is signed in. On load it trades the refresh cookie for an access token
 * (`api.restoreSession()`, D-63) and reads `GET /me`; until then the state is `loading`, so
 * guarded routes wait instead of bouncing a signed-in user to /login. The access token lives only
 * in the API client's memory (D-62).
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<AuthState>({ status: "loading", user: null })

  useEffect(() => {
    let cancelled = false
    setSessionExpiredHandler(() => {
      if (!cancelled) setState(ANONYMOUS)
    })
    void (async () => {
      try {
        const user = (await api.restoreSession()) ? await fetchMe() : null
        if (!cancelled) setState(user ? { status: "signedIn", user } : ANONYMOUS)
      } catch {
        if (!cancelled) setState(ANONYMOUS)
      }
    })()
    return () => {
      cancelled = true
      setSessionExpiredHandler(null)
    }
  }, [])

  const acceptSession = useCallback(async (session: AuthSession) => {
    api.setAccessToken(session.accessToken)
    const user = session.user ?? (await fetchMe())
    setState({ status: "signedIn", user })
    return user
  }, [])

  const acceptUpdate = useCallback((update: MeUpdate) => {
    api.setAccessToken(update.accessToken)
    setState({ status: "signedIn", user: update.user })
    return update.user
  }, [])

  const signOut = useCallback(async () => {
    try {
      await logout()
    } catch {
      // The cookie expires on its own; the local session ends either way.
    }
    api.clearSession()
    queryClient.removeQueries({ queryKey: ["me"] })
    setState(ANONYMOUS)
  }, [queryClient])

  const value = useMemo<AuthContextValue>(
    () => ({ state, acceptSession, acceptUpdate, signOut }),
    [state, acceptSession, acceptUpdate, signOut],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
