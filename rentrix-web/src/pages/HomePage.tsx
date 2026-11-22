import { useEffect, type ReactNode } from "react"
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom"
import { Brand } from "@/components/layout/Brand"
import { Container } from "@/components/layout/Container"
import { Badge } from "@/components/ui/badge"
import {
  CheckIcon,
  HomeIcon,
  KeyIcon,
  LandlordIcon,
  LinkIcon,
  LoginIcon,
  MessageIcon,
  ShieldCheckIcon,
  StarIcon,
  TenantIcon,
} from "@/components/ui/icons.gen"
import { useAuth } from "@/features/auth/authContext"
import { AuthPending } from "@/features/auth/RequireAuth"
import { LaunchCopy } from "@/features/city/LaunchCopy"
import { GoogleCta } from "@/features/landing/GoogleCta"
import { GreenTick, LandingPreview } from "@/features/landing/LandingPreview"
import { BRAND } from "@/lib/brand"
import type { CurrentUser } from "@/types/api"

/** Where a signed-in visitor of `/` goes: the app (admins to their console, everyone else to Search). */
function appHome(user: CurrentUser): string {
  return user.roles.includes("ADMIN") ? "/admin" : "/search"
}

function Section({
  id,
  title,
  intro,
  children,
  tinted = false,
}: {
  id: string
  title: string
  intro?: ReactNode
  children: ReactNode
  tinted?: boolean
}) {
  return (
    <section
      aria-labelledby={`${id}-title`}
      className={tinted ? "border-y border-line bg-sunk py-12 sm:py-16" : "py-12 sm:py-16"}
    >
      <Container className="flex flex-col gap-6">
        <div className="flex max-w-2xl flex-col gap-2">
          <h2
            id={`${id}-title`}
            className="font-display text-2xl font-semibold text-ink-900 sm:text-3xl"
          >
            {title}
          </h2>
          {intro && <p className="text-base text-ink-700">{intro}</p>}
        </div>
        {children}
      </Container>
    </section>
  )
}

/** The three ratings, one per side and subject: the product's point. */
const SIDES = [
  {
    icon: <HomeIcon size={22} />,
    who: "Tenants rate homes",
    text: "Upkeep, water, power, noise and the society, from people who lived there.",
    tone: "bg-tenant-100 text-tenant-600",
  },
  {
    icon: <LandlordIcon size={22} />,
    who: "Tenants rate landlords",
    text: "Deposit returned on time, repairs done, fair rent rises, respect for privacy.",
    tone: "bg-tenant-100 text-tenant-600",
  },
  {
    icon: <TenantIcon size={22} />,
    who: "Landlords rate tenants",
    text: "Rent paid on time, the home looked after, a smooth move-out.",
    tone: "bg-landlord-100 text-landlord-600",
  },
]

const STEPS = [
  {
    icon: <LoginIcon size={20} />,
    title: "Sign in with Google",
    text: "Pick your side: tenant, landlord or both. Reading homes, rents and reviews needs nothing more.",
  },
  {
    icon: <KeyIcon size={20} />,
    title: "Verify your phone and add your rental",
    text: "A one-time code (OTP) to your phone lets you post. Add the home you rent or rent out, and the rent you paid or charged.",
  },
  {
    icon: <StarIcon size={20} />,
    title: "Rate each other",
    text: "Tenants rate the home and the landlord; landlords rate the tenant. Others read real rents and reviews before they sign.",
  },
]

const TRUST = [
  {
    icon: <MessageIcon size={20} />,
    title: "Phone OTP for everyone who posts",
    text: "Every tenant and landlord verifies a phone number before they add a home, a tenancy or a review.",
  },
  {
    icon: <CheckIcon size={20} />,
    title: `Green tick: "Verified by ${BRAND.name}"`,
    text: 'Posts show at once as "Not verified yet". After our team checks a user, home, tenancy or review, it gets the green tick. Headline ratings count only green-tick reviews.',
  },
  {
    icon: <ShieldCheckIcon size={20} />,
    title: "Optional ID badge: Aadhaar or PAN",
    text: 'Add a masked Aadhaar or a PAN for an "ID verified" badge. Others see only the badge, never the number.',
  },
  {
    icon: <LinkIcon size={20} />,
    title: "Optional LinkedIn badge",
    text: 'Link your LinkedIn profile for a bonus "LinkedIn verified" badge. It is never required.',
  },
]

/**
 * `/`: the landing for anonymous visitors. It says what the product is, the problem (two-sided
 * ratings), how it works, how trust works and shows a small preview; "Sign in / Sign up with
 * Google" is the call to action, at the top and the end. A signed-in visitor goes into the app.
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
      <header className="border-b border-line bg-surface">
        <Container className="flex h-16 items-center">
          <Brand />
        </Container>
      </header>

      <main className="flex-1">
        <section aria-labelledby="hero-title" className="py-12 sm:py-20">
          <Container className="flex flex-col items-start gap-5 sm:items-center sm:text-center">
            <LaunchCopy className="rounded-full bg-teal-50 px-3 py-1 text-sm font-semibold text-teal-700" />
            <h1
              id="hero-title"
              className="font-display text-4xl font-semibold text-ink-900 sm:text-5xl"
            >
              {BRAND.name}
            </h1>
            <p className="max-w-2xl text-xl text-ink-900 sm:text-2xl">{BRAND.tagline}</p>
            <p className="max-w-2xl text-base text-ink-700">
              Before you rent, see what past tenants paid and how they rated the home and the
              landlord. Before you let, see how past landlords rated the tenant.
            </p>
            <GoogleCta className="mt-2" />
          </Container>
        </section>

        <Section
          id="problem"
          tinted
          title="Ratings from both sides of the rental"
          intro="Renting is a leap of faith for both sides. Tenants cannot check a landlord or a home before they pay the deposit, and landlords cannot check a tenant. Here each side rates the other after a real tenancy."
        >
          <ul className="grid gap-4 sm:grid-cols-3">
            {SIDES.map((side) => (
              <li
                key={side.who}
                className="flex flex-col gap-3 rounded-xl border border-line bg-surface p-5"
              >
                <span className={`grid size-11 place-items-center rounded-lg ${side.tone}`}>
                  {side.icon}
                </span>
                <h3 className="text-lg font-semibold text-ink-900">{side.who}</h3>
                <p className="text-sm text-ink-700">{side.text}</p>
              </li>
            ))}
          </ul>
        </Section>

        <Section id="how" title="How it works">
          <ol className="grid gap-4 sm:grid-cols-3">
            {STEPS.map((step, i) => (
              <li key={step.title} className="flex gap-4 sm:flex-col">
                <span className="grid size-11 shrink-0 place-items-center rounded-full bg-teal-600 font-semibold text-white">
                  {i + 1}
                </span>
                <div className="flex flex-col gap-1.5">
                  <h3 className="inline-flex items-center gap-2 text-lg font-semibold text-ink-900">
                    <span className="text-teal-600" aria-hidden>
                      {step.icon}
                    </span>
                    {step.title}
                  </h3>
                  <p className="text-sm text-ink-700">{step.text}</p>
                </div>
              </li>
            ))}
          </ol>
        </Section>

        <Section
          id="trust"
          tinted
          title="Trust and verification"
          intro={
            <>
              Anyone with a verified phone can post, and posts show at once. The green tick{" "}
              <GreenTick /> shows what our team has checked.
            </>
          }
        >
          <ul className="grid gap-4 sm:grid-cols-2">
            {TRUST.map((item) => (
              <li
                key={item.title}
                className="flex gap-4 rounded-xl border border-line bg-surface p-5"
              >
                <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-teal-50 text-teal-700">
                  {item.icon}
                </span>
                <div className="flex flex-col gap-1.5">
                  <h3 className="text-base font-semibold text-ink-900">{item.title}</h3>
                  <p className="text-sm text-ink-700">{item.text}</p>
                </div>
              </li>
            ))}
          </ul>
        </Section>

        <Section
          id="preview"
          title="A look inside"
          intro={
            <>
              Homes as they appear in search. Cards marked <Badge variant="demo">Demo data</Badge>{" "}
              or <Badge variant="warn">Sample</Badge> are examples, not real tenancies.
            </>
          }
        >
          <LandingPreview />
        </Section>

        <section
          aria-labelledby="cta-title"
          className="border-t border-line bg-teal-50 py-12 sm:py-16"
        >
          <Container className="flex flex-col items-start gap-4 sm:items-center sm:text-center">
            <h2
              id="cta-title"
              className="font-display text-2xl font-semibold text-ink-900 sm:text-3xl"
            >
              Rent with your eyes open
            </h2>
            <p className="max-w-xl text-base text-ink-700">
              Read real rents and reviews, or add your own. One Google account works for both sides.
            </p>
            <GoogleCta />
          </Container>
        </section>
      </main>

      <footer className="py-6 text-sm text-ink-500">
        <Container className="flex flex-wrap items-center justify-between gap-4">
          <span>{BRAND.name}</span>
          <span className="flex gap-2">
            <Link to="/terms" className="inline-flex min-h-11 items-center px-2">
              Terms
            </Link>
            <Link to="/privacy" className="inline-flex min-h-11 items-center px-2">
              Privacy
            </Link>
          </span>
        </Container>
      </footer>
    </div>
  )
}
