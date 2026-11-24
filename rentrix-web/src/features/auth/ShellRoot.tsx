import { useEffect } from "react"
import { useLocation, useNavigate } from "react-router-dom"
import { isOwnerPath, modeHome, type Mode } from "@/features/mode/mode"
import { useMode } from "@/features/mode/useMode"
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
  const [mode, setMode] = useMode()
  const { pathname } = useLocation()
  useEffect(() => {
    if (isOwnerPath(pathname)) setMode("OWNER")
  }, [pathname, setMode])
  const viewer: ShellViewer | null = user
    ? { shortName: user.shortName, roles: user.roles, isDemo: user.demo, progress, mode }
    : null
  // A new mode opens its home page, so the switch always shows a page of that side.
  const onModeChange = (next: Mode) => {
    setMode(next)
    navigate(modeHome(next))
  }
  // Leave the page first: on a guarded page the guard would otherwise send the user to /login.
  // `signedOut` keeps the landing from sending the still-signed-in user back into the app.
  const onSignOut = () => {
    navigate("/", { replace: true, state: { signedOut: true } })
    void signOut()
  }
  return (
    <AppLayout
      viewer={viewer}
      onSignOut={user ? onSignOut : undefined}
      onModeChange={user && !user.needsOnboarding ? onModeChange : undefined}
    />
  )
}
