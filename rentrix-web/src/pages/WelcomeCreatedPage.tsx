import { Link, Navigate, useLocation } from "react-router-dom"
import { Container } from "@/components/layout/Container"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { CheckIcon, LockIcon, MessageIcon, PlusIcon, UserIcon } from "@/components/ui/icons.gen"
import { Notice } from "@/components/ui/notice"
import { useAuth } from "@/features/auth/authContext"
import { safeNext } from "@/features/auth/paths"
import { ROLE_COPY, roleHome, type PickableRole } from "@/features/auth/roles"
import { progressOf } from "@/features/verification/progress"
import { useVerification } from "@/features/verification/useVerification"
import { BRAND } from "@/lib/brand"

/** What the role pick hands over when it opens this page (D-118). */
export interface CreatedState {
  created: true
  next?: string | null
}

function isCreatedState(value: unknown): value is CreatedState {
  return typeof value === "object" && value !== null && (value as CreatedState).created === true
}

/**
 * `/welcome/created` (D-118, mockup `welcome-created.html`): shown once, right after the first
 * sign-in's role pick. It says the account exists, shows what the user gave (name, email, roles)
 * and offers the next step, adding their home, which needs no verification (D-163); verifying for
 * the green tick is an optional second button. Opened any other way it forwards to `/verify` while
 * verification is open, else to the role home.
 */
export default function WelcomeCreatedPage() {
  const { state } = useAuth()
  const location = useLocation()
  const verification = useVerification()
  const user = state.user
  if (!user) return null
  if (!isCreatedState(location.state)) {
    if (verification.isPending && verification.fetchStatus !== "idle") return null
    const open = progressOf(verification.data) !== null
    return <Navigate to={open ? "/verify" : roleHome(user)} replace />
  }

  const later = safeNext(location.state.next, roleHome(user))
  const roles = user.roles.filter((r): r is PickableRole => r === "TENANT" || r === "LANDLORD")
  // The new user's first job (captain 21:38): add their home, as its tenant or, for a landlord, its
  // owner ("I own it" picked), which publishes it at once (open by default, D-132).
  const firstJob =
    roles.includes("TENANT") || roles.length === 0 ? "/homes/new" : "/homes/new?own=1"

  return (
    <main>
      <Container className="max-w-[640px] py-10 nav:py-14">
        <section className="rounded-xl border border-line bg-surface p-6 shadow-sm nav:p-8">
          <div className="text-center">
            <span className="mx-auto grid size-16 place-items-center rounded-full bg-success-100 text-success-600">
              <CheckIcon size={34} />
            </span>
            <p className="mt-3 text-xs font-semibold tracking-wide text-teal-700 uppercase">
              {user.demo ? "Signed in with dev login" : "Signed in with Google"}
            </p>
            <h1 className="mt-1 font-display text-3xl font-semibold text-ink-900">
              Welcome to {BRAND.name}
            </h1>
            <p className="mt-1.5 text-ink-500">Your account is created.</p>
          </div>

          <hr className="my-6 border-line" />

          <div className="flex items-center gap-4">
            <span className="grid size-16 shrink-0 place-items-center overflow-hidden rounded-full bg-sunk text-ink-700">
              {user.avatarUrl ? (
                <img
                  src={user.avatarUrl}
                  alt=""
                  referrerPolicy="no-referrer"
                  className="size-full object-cover"
                />
              ) : (
                <UserIcon size={32} />
              )}
            </span>
            <dl className="grid min-w-0 flex-1 grid-cols-[80px_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm">
              <dt className="text-ink-500">Name</dt>
              <dd className="min-w-0 text-ink-900">
                <b>{user.displayName}</b>{" "}
                <span className="text-xs text-ink-500">
                  · shown publicly as &ldquo;{user.shortName}&rdquo;
                </span>
              </dd>
              <dt className="text-ink-500">Email</dt>
              <dd className="min-w-0 truncate text-ink-900">
                {user.email}{" "}
                <span className="inline-flex items-center gap-1 text-xs text-ink-500">
                  <LockIcon size={12} />
                  private
                </span>
              </dd>
              <dt className="text-ink-500">{roles.length > 1 ? "Roles" : "Role"}</dt>
              <dd className="flex flex-wrap gap-1.5">
                {roles.map((role) => (
                  <Badge key={role} variant={role === "TENANT" ? "tenant" : "landlord"}>
                    {ROLE_COPY[role].badge}
                  </Badge>
                ))}
              </dd>
            </dl>
          </div>

          <Notice tone="teal" className="mt-6">
            <b>You can post right away.</b> What you post shows as &ldquo;Not verified yet&rdquo;.
            Optional: verify with any one of phone, Aadhaar or LinkedIn to get a green tick.
          </Notice>
          <Button asChild size="lg" className="mt-5 w-full">
            <Link to={firstJob} data-testid="welcome-add-home">
              <PlusIcon />
              {firstJob === "/homes/new" ? "Add your home" : "Add your property"}
            </Link>
          </Button>
          <Button asChild variant="outline" className="mt-2 w-full">
            <Link
              to={`/verify/phone?next=${encodeURIComponent(firstJob)}`}
              data-testid="welcome-verify"
            >
              <MessageIcon />
              Verify to get a green tick
            </Link>
          </Button>
          <Button asChild variant="ghost" className="mt-2 w-full">
            <Link to={later}>Not now, take me back</Link>
          </Button>
          <p className="mt-3 text-center text-xs text-ink-500">
            You can read every review and the rent history, and post reviews, homes and rent, right
            away.
          </p>
        </section>
      </Container>
    </main>
  )
}
