import { createContext, useContext } from "react"
import type { AuthSession, CurrentUser } from "@/types/api"

export type AdminAuthState =
  | { status: "loading"; user: null }
  | { status: "anonymous"; user: null }
  | { status: "signedIn"; user: CurrentUser }

export interface AdminAuthValue {
  state: AdminAuthState
  /** Stores the token and user of an admin sign-in (`/auth/admin/google`, `/auth/admin/dev-login`). */
  acceptSession: (session: AuthSession) => Promise<CurrentUser>
  /** `POST /auth/admin/logout`, then forgets the session even when the call fails. */
  signOut: () => Promise<void>
}

export const AdminAuthContext = createContext<AdminAuthValue | null>(null)

export function useAdminAuth(): AdminAuthValue {
  const value = useContext(AdminAuthContext)
  if (!value) throw new Error("useAdminAuth needs <AdminAuthProvider>")
  return value
}
