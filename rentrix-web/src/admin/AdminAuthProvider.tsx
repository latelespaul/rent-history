import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react"
import { useQueryClient } from "@tanstack/react-query"
import type { AuthSession } from "@/types/api"
import { adminApi, adminLogout, fetchAdminMe, setAdminSessionExpiredHandler } from "./api"
import { AdminAuthContext, type AdminAuthState } from "./authContext"

const ANONYMOUS: AdminAuthState = { status: "anonymous", user: null }

/**
 * The admin console's session (SYSTEM_DESIGN §13.3), separate from the main site's: on load it
 * trades the admin refresh cookie for an admin access token, kept in memory only (D-62).
 */
export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<AdminAuthState>({ status: "loading", user: null })

  useEffect(() => {
    let cancelled = false
    setAdminSessionExpiredHandler(() => {
      if (!cancelled) setState(ANONYMOUS)
    })
    void (async () => {
      try {
        const user = (await adminApi.restoreSession()) ? await fetchAdminMe() : null
        if (!cancelled) setState(user ? { status: "signedIn", user } : ANONYMOUS)
      } catch {
        if (!cancelled) setState(ANONYMOUS)
      }
    })()
    return () => {
      cancelled = true
      setAdminSessionExpiredHandler(null)
    }
  }, [])

  const acceptSession = useCallback(async (session: AuthSession) => {
    adminApi.setAccessToken(session.accessToken)
    const user = session.user ?? (await fetchAdminMe())
    setState({ status: "signedIn", user })
    return user
  }, [])

  const signOut = useCallback(async () => {
    try {
      await adminLogout()
    } catch {
      // The cookie expires on its own; the local session ends either way.
    }
    adminApi.clearSession()
    queryClient.clear()
    setState(ANONYMOUS)
  }, [queryClient])

  const value = useMemo(() => ({ state, acceptSession, signOut }), [state, acceptSession, signOut])
  return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>
}
