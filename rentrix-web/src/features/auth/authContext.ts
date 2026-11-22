import { createContext, useContext } from "react"
import type { AuthSession, CurrentUser, MeUpdate } from "@/types/api"

export type AuthState =
  | { status: "loading"; user: null }
  | { status: "anonymous"; user: null }
  | { status: "signedIn"; user: CurrentUser }

export interface AuthContextValue {
  state: AuthState
  /** Stores the token and user of a sign-in (`/auth/google`, `/auth/dev-login`) response. */
  acceptSession: (session: AuthSession) => Promise<CurrentUser>
  /** Stores the fresh token and user of an onboarding or `PATCH /me` response (D-73). */
  acceptUpdate: (update: MeUpdate) => CurrentUser
  /** `POST /auth/logout`, then forgets the session even when the call fails. */
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error("useAuth needs <AuthProvider>")
  return value
}
