import { Navigate, Outlet, useLocation } from "react-router-dom"
import { Container } from "@/components/layout/Container"
import { EmptyState } from "@/components/ui/states"
import type { Role } from "@/types/api"
import { useAuth } from "./authContext"
import { loginPath } from "./paths"

interface Props {
  /** Only users holding this role may open the routes (e.g. `LANDLORD`). */
  role?: Role
  /** The role-pick page itself: open to users who have not picked a role yet. */
  allowOnboarding?: boolean
}

export function AuthPending() {
  return (
    <main aria-busy="true">
      <Container className="py-16">
        <p role="status" className="text-center text-sm text-ink-500">
          Checking your sign-in…
        </p>
      </Container>
    </main>
  )
}

/**
 * Guards a group of routes. Anonymous visitors go to `/login?next=…`; users who still have to
 * pick a role go to `/welcome` (D-73);
 * a missing `role` renders "No access" instead of the page. The API enforces all of this too;
 * the guard only keeps the UI honest.
 */
export function RequireAuth({ role, allowOnboarding = false }: Props) {
  const { state } = useAuth()
  const location = useLocation()
  const here = `${location.pathname}${location.search}`

  if (state.status === "loading") return <AuthPending />
  if (state.status === "anonymous") return <Navigate to={loginPath(here)} replace />

  const { user } = state
  if (user.needsOnboarding && !allowOnboarding) {
    return <Navigate to={`/welcome?next=${encodeURIComponent(here)}`} replace />
  }
  if (role && !user.roles.includes(role)) {
    return (
      <main>
        <Container className="py-16">
          <EmptyState
            icon="lock"
            title="No access"
            description="Your account cannot open this page."
          />
        </Container>
      </main>
    )
  }
  return <Outlet />
}
