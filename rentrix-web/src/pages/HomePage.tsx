import { useEffect } from "react"
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { TenancyIcon } from "@/components/ui/icons.gen"
import { useAuth } from "@/features/auth/authContext"
import { AuthPending } from "@/features/auth/RequireAuth"
import { BRAND } from "@/lib/brand"
import type { CurrentUser } from "@/types/api"

/** Where a signed-in visitor of `/` goes: the app (admins to their console, everyone else to Search). */
function appHome(user: CurrentUser): string {
  return user.roles.includes("ADMIN") ? "/admin" : "/search"
}

/** Google's "G", so the one button says which account it uses. */
function GoogleMark() {
  return (
    <svg viewBox="0 0 48 48" aria-hidden="true" className="rounded-full bg-white p-0.5">
      <path
        fill="#EA4335"
        d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.6 5.4 2.6 13.3l7.9 6.1C12.4 13.7 17.7 9.5 24 9.5z"
      />
      <path
        fill="#4285F4"
        d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.7c-.6 2.9-2.2 5.4-4.7 7.1l7.6 5.9c4.4-4.1 6.9-10.1 6.9-17.5z"
      />
      <path
        fill="#FBBC05"
        d="M10.5 28.6c-.5-1.4-.8-3-.8-4.6s.3-3.2.8-4.6l-7.9-6.1C.9 16.6 0 20.2 0 24s.9 7.4 2.6 10.7l7.9-6.1z"
      />
      <path
        fill="#34A853"
        d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.6-5.9c-2.1 1.4-4.9 2.3-8.3 2.3-6.3 0-11.6-4.2-13.5-10l-7.9 6.1C6.6 42.6 14.6 48 24 48z"
      />
    </svg>
  )
}

/**
 * `/`: a minimal landing for anonymous visitors (brand, one line, one sign-in button, Terms and
 * Privacy). Search and the rest of the app open after sign-in, so a signed-in visitor goes there.
 */
export default function HomePage() {
  const { state } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const signingOut = (location.state as { signedOut?: boolean } | null)?.signedOut === true
  useEffect(() => {
    if (signingOut && state.status === "anonymous") navigate("/", { replace: true })
  }, [signingOut, state.status, navigate])
  if (state.status === "loading") return <AuthPending />
  if (state.status === "signedIn" && !signingOut) {
    return <Navigate to={appHome(state.user)} replace />
  }

  return (
    <div className="flex min-h-screen flex-col bg-background">
      <main className="flex flex-1 items-center">
        <Container className="py-16">
          <div className="mx-auto flex max-w-xl flex-col items-center gap-6 text-center">
            <span className="grid size-16 place-items-center rounded-[18px] bg-teal-600 text-white">
              <TenancyIcon size={36} />
            </span>
            <h1 className="font-display text-4xl font-semibold text-ink-900 sm:text-5xl">
              {BRAND.name}
            </h1>
            <p className="text-lg text-ink-700">{BRAND.tagline}</p>
            <Button asChild size="lg">
              <Link to="/login">
                <GoogleMark />
                Sign in / Sign up
              </Link>
            </Button>
          </div>
        </Container>
      </main>
      <footer className="py-6 text-sm text-ink-500">
        <Container className="flex justify-center gap-6">
          <Link to="/terms">Terms</Link>
          <Link to="/privacy">Privacy</Link>
        </Container>
      </footer>
    </div>
  )
}
