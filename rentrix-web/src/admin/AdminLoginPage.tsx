import { useMutation, useQuery } from "@tanstack/react-query"
import { Navigate, useSearchParams } from "react-router-dom"
import { isApiError } from "@/api/errors"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { LoginIcon, ShieldCheckIcon } from "@/components/ui/icons.gen"
import { Notice } from "@/components/ui/notice"
import { ErrorState, errorMessage } from "@/components/ui/states"
import { GoogleButton } from "@/features/auth/GoogleButton"
import { safeNext } from "@/features/auth/paths"
import { BRAND } from "@/lib/brand"
import {
  adminDevLogin,
  adminKeys,
  adminSignInWithGoogle,
  fetchAdminAuthConfig,
  fetchAdminDevUsers,
} from "./api"
import { useAdminAuth } from "./authContext"

/** Plain words for a refused sign-in; `not_an_admin` is the expected one on this page. */
function signInError(error: unknown): string {
  if (isApiError(error) && error.code === "not_an_admin") {
    return "This Google account is not on the admin list. Sign in with an admin account."
  }
  return errorMessage(error)
}

/**
 * `/login` on the admin host (SYSTEM_DESIGN §13.3): "Sign in with Google" for the emails in
 * `ADMIN_CONSOLE_EMAILS`, and in the dev profile the seed admins (dev login). A session here is an
 * admin session only; the main site's sign-in is not offered.
 */
export function AdminLoginPage() {
  const { state, acceptSession } = useAdminAuth()
  const [params] = useSearchParams()
  const config = useQuery({
    queryKey: adminKeys.config,
    queryFn: ({ signal }) => fetchAdminAuthConfig(signal),
  })
  const showDev = config.data?.devLogin === true
  const devUsers = useQuery({
    queryKey: adminKeys.devUsers,
    queryFn: ({ signal }) => fetchAdminDevUsers(signal),
    enabled: showDev,
  })
  const google = useMutation({
    mutationFn: async (idToken: string) => acceptSession(await adminSignInWithGoogle(idToken)),
  })
  const dev = useMutation({
    mutationFn: async (userId: string) => acceptSession(await adminDevLogin(userId)),
  })

  if (state.status === "signedIn") {
    return <Navigate to={safeNext(params.get("next"), "/")} replace />
  }
  const failure = google.error ?? dev.error

  return (
    <main className="min-h-dvh bg-paper px-4 py-8 sm:py-14">
      <div className="mx-auto grid max-w-md gap-6">
        <section className="rounded-xl border border-line bg-surface p-6 shadow-sm sm:p-8">
          <span className="grid size-11 place-items-center rounded-[12px] bg-ink-900 text-paper">
            <ShieldCheckIcon size={24} />
          </span>
          <h1 className="mt-4 font-display text-2xl font-semibold text-ink-900 sm:text-3xl">
            {BRAND.name} admin
          </h1>
          <p className="mt-1.5 text-ink-500">
            For the {BRAND.name} team. Sign in with an approved Google account.
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
              {showDev ? " Use dev login for now." : ""}
            </Notice>
          )}
          {(google.isPending || dev.isPending) && (
            <p role="status" className="mt-3 text-sm text-ink-500">
              Signing you in…
            </p>
          )}
          {failure && (
            <Notice tone="danger" className="mt-3">
              {signInError(failure)}
            </Notice>
          )}
        </section>

        {showDev && (
          <section
            aria-labelledby="admin-dev-login-title"
            className="rounded-xl border border-dashed border-dev-600 bg-dev-100 p-6 text-[#4d2390]"
          >
            <Badge variant="dev">DEV ONLY</Badge>
            <h2 id="admin-dev-login-title" className="mt-3 text-lg font-semibold">
              Dev login · seed admins
            </h2>
            <p className="mt-1 text-sm">
              Only when the API runs the <code>dev</code> profile with dev login on.
            </p>
            {devUsers.isPending && <p className="mt-4 text-sm">Loading seed admins…</p>}
            {devUsers.isError && (
              <p role="alert" className="mt-4 text-sm">
                Could not load seed admins: {errorMessage(devUsers.error)}
              </p>
            )}
            {devUsers.isSuccess && devUsers.data.length === 0 && (
              <p className="mt-4 text-sm">No seed admin in this database.</p>
            )}
            <div className="mt-3 space-y-2">
              {devUsers.data?.map((user) => (
                <Button
                  key={user.id}
                  type="button"
                  variant="dev"
                  size="block"
                  className="h-12"
                  disabled={dev.isPending}
                  onClick={() => dev.mutate(user.id)}
                >
                  <LoginIcon />
                  Sign in as {user.displayName}
                </Button>
              ))}
            </div>
          </section>
        )}
      </div>
    </main>
  )
}
