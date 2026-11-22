import { useNavigate } from "react-router-dom"
import { useVerificationProgress } from "@/features/verification/useVerification"
import { AppLayout } from "@/components/layout/AppLayout"
import type { ShellViewer } from "@/components/layout/nav"
import { useAuth } from "./authContext"

/** The app shell drawn for whoever is signed in (W3 fills the viewer W2 left null). */
export function ShellRoot() {
  const { state, signOut } = useAuth()
  const navigate = useNavigate()
  const user = state.user
  const progress = useVerificationProgress()
  const viewer: ShellViewer | null = user
    ? { shortName: user.shortName, roles: user.roles, isDemo: user.demo, progress }
    : null
  // Leave the page first: on a guarded page the guard would otherwise send the user to /login.
  // `signedOut` keeps the landing from sending the still-signed-in user back into the app.
  const onSignOut = () => {
    navigate("/", { replace: true, state: { signedOut: true } })
    void signOut()
  }
  return <AppLayout viewer={viewer} onSignOut={user ? onSignOut : undefined} />
}
