import { useMutation, useQuery } from "@tanstack/react-query"
import { Navigate, useSearchParams } from "react-router-dom"
import { toast } from "sonner"
import {
  authKeys,
  devLogin,
  devLoginAsNewUser,
  fetchAuthConfig,
  signInWithGoogle,
} from "@/api/auth"
import { Container } from "@/components/layout/Container"
import { Icon } from "@/components/ui/icon"
import { Notice } from "@/components/ui/notice"
import { ErrorState, errorMessage } from "@/components/ui/states"
import { TenancyIcon } from "@/components/ui/icons.gen"
import { useAuth } from "@/features/auth/authContext"
import { DevLoginPanel } from "@/features/auth/DevLoginPanel"
import { GoogleButton } from "@/features/auth/GoogleButton"
import { afterSignInPath, safeNext } from "@/features/auth/paths"
import { BRAND } from "@/lib/brand"
import { roleHome } from "@/features/auth/roles"
import type { CurrentUser } from "@/types/api"

const PROMISES = [
  { icon: "lock", text: `No ${BRAND.name} password. Google confirms your email.` },
  { icon: "eye-off", text: "Your email and phone are never shown publicly." },
  {
    icon: "users",
    text: `One Google account = one ${BRAND.name} account. Be a tenant, a landlord, or both.`,
  },
] as const

/**
 * The returning sign-in toast (D-119): 4 s, in sonner's polite live region. Only after a sign-in
 * on this page, so a session restored from the refresh cookie on load shows none.
 */
function greet(user: CurrentUser) {
  if (!user.needsOnboarding) toast.success(`Signed in as ${user.displayName}`, { duration: 4000 })
}

/**
 * `/login` (DESIGN.md screen 9): the GIS button when the API has a Google client id (D-76),
 * and the dev seed-user picker when the API runs dev login (D-77). New Google users go to the
 * role pick next (D-73); everyone else sees "Signed in as …" and returns to `?next=`, or to their
 * role home without one (D-119).
 */
export default function LoginPage() {
  const { state, acceptSession } = useAuth()
  const [params] = useSearchParams()
  const nextParam = params.get("next")
  const config = useQuery({
    queryKey: authKeys.config,
    queryFn: ({ signal }) => fetchAuthConfig(signal),
  })

  const google = useMutation({
    mutationFn: async (idToken: string) => acceptSession(await signInWithGoogle(idToken)),
    onSuccess: greet,
  })
  const dev = useMutation({
    mutationFn: async (userId: string | null) =>
      acceptSession(await (userId ? devLogin(userId) : devLoginAsNewUser())),
    onSuccess: greet,
  })

  if (state.status === "signedIn") {
    const { user } = state
    // The role pick keeps a plain `next`; a returning user without one goes to their role home.
    const next = user.needsOnboarding ? safeNext(nextParam) : safeNext(nextParam, roleHome(user))
    return <Navigate to={afterSignInPath(user, next)} replace />
  }

  const failure = google.error ?? dev.error
  const showDev = config.data?.devLogin === true

  return (
    <main>
      <Container className="py-10 nav:py-14">
        <div
          className={
            showDev
              ? "mx-auto grid max-w-5xl items-start gap-8 nav:grid-cols-2"
              : "mx-auto grid max-w-md items-start gap-8"
          }
        >
          <section className="rounded-xl border border-line bg-surface p-8 shadow-sm">
            <span className="grid size-11 place-items-center rounded-[12px] bg-teal-600 text-white">
              <TenancyIcon size={26} />
            </span>
            <h1 className="mt-4 font-display text-3xl font-semibold text-ink-900">
              Sign in to {BRAND.name}
            </h1>
            <p className="mt-1.5 text-ink-500">
              Tenants and landlords use the same button. New here? It creates your account.
            </p>

            {config.isPending && (
              <div aria-hidden className="mt-6 h-11 w-full animate-pulse rounded-md bg-sunk" />
            )}
            {config.isError && (
              <ErrorState
                className="mt-6"
                error={config.error}
                onRetry={() => void config.refetch()}
              />
            )}
            {config.data?.googleClientId && (
              <GoogleButton
                clientId={config.data.googleClientId}
                onCredential={(t) => google.mutate(t)}
              />
            )}
            {config.isSuccess && !config.data.googleClientId && (
              <Notice tone="info" className="mt-6">
                Google sign-in is not set up on this server yet.
                {showDev ? " Use dev login for now." : " Please try again later."}
              </Notice>
            )}
            {(google.isPending || dev.isPending) && (
              <p role="status" className="mt-3 text-sm text-ink-500">
                Signing you in…
              </p>
            )}
            {failure && (
              <Notice tone="danger" className="mt-3">
                {errorMessage(failure)}
              </Notice>
            )}

            <ul className="mt-6 space-y-2.5 text-sm text-ink-500">
              {PROMISES.map((p) => (
                <li key={p.icon} className="flex items-start gap-2">
                  <Icon name={p.icon} size={18} className="mt-px shrink-0" />
                  {p.text}
                </li>
              ))}
            </ul>
            <p className="mt-6 text-xs text-ink-500">
              By continuing you accept the Terms and Privacy notice.
            </p>
          </section>

          {showDev && (
            <DevLoginPanel
              onSignIn={(id) => dev.mutate(id)}
              onSignInAsNew={() => dev.mutate(null)}
              pending={dev.isPending}
            />
          )}
        </div>
      </Container>
    </main>
  )
}
