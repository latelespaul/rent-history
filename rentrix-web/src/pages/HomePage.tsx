import { useEffect, type ReactNode } from "react"
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom"
import { Brand } from "@/components/layout/Brand"
import { Container } from "@/components/layout/Container"
import { Badge, VerifiedBadge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  ArrowRightIcon,
  CheckIcon,
  ChevronDownIcon,
  DocumentIcon,
  InfoIcon,
  KeyIcon,
  LandlordIcon,
  LinkIcon,
  LoginIcon,
  MessageIcon,
  ShieldCheckIcon,
  StarIcon,
  TenancyIcon,
  TenantIcon,
} from "@/components/ui/icons.gen"
import { useAuth } from "@/features/auth/authContext"
import { AuthPending } from "@/features/auth/RequireAuth"
import { launchCopy } from "@/features/city/cityText"
import { useCities } from "@/features/city/useCities"
import { AppMock } from "@/features/landing/AppMock"
import { GoogleCta } from "@/features/landing/GoogleCta"
import { LandingCoverage } from "@/features/landing/LandingCoverage"
import { GreenTick, LandingPreview } from "@/features/landing/LandingPreview"
import { BRAND } from "@/lib/brand"
import { cn } from "@/lib/utils"
import type { CurrentUser } from "@/types/api"

/** Where a signed-in visitor of `/` goes: the app (admins to their console, everyone else to Search). */
function appHome(user: CurrentUser): string {
  return user.roles.includes("ADMIN") ? "/admin" : "/search"
}

/** Section heading type: one scale for the whole page (28 px on a phone, 40 px from 1024 px). */
const H2 =
  "font-display text-[1.75rem] leading-[1.15] font-semibold tracking-tight lg:text-[2.5rem]"
const EYEBROW = "text-xs font-semibold tracking-[0.1em] text-teal-700 uppercase"
const LEAD = "max-w-2xl text-lg text-ink-700 lg:text-xl"

function Section({
  id,
  eyebrow,
  title,
  intro,
  children,
  center = false,
  className,
}: {
  id: string
  eyebrow: string
  title: string
  intro?: ReactNode
  children: ReactNode
  center?: boolean
  className?: string
}) {
  return (
    <section
      id={id}
      aria-labelledby={`${id}-title`}
      className={cn("scroll-mt-16 py-16 lg:py-26", className)}
    >
      <Container className="lg:px-8">
        <div
          className={cn(
            "mb-8 flex flex-col gap-3 lg:mb-12",
            center && "lg:items-center lg:text-center",
          )}
        >
          <span className={EYEBROW}>{eyebrow}</span>
          <h2 id={`${id}-title`} className={cn(H2, "text-ink-900")}>
            {title}
          </h2>
          {intro && <p className={LEAD}>{intro}</p>}
        </div>
        {children}
      </Container>
    </section>
  )
}

/** The launch line from `GET /cities` (D-85) in a pill with a live dot; nothing until it loads. */
function LaunchPill() {
  const cities = useCities()
  if (!cities.isSuccess) return null
  return (
    <span className="inline-flex items-center gap-2 self-start rounded-full border border-line bg-surface py-1.5 pr-3 pl-2 text-[13px] font-semibold text-ink-700 shadow-sm">
      <span
        aria-hidden
        className="size-2 rounded-full bg-success-600 shadow-[0_0_0_4px_var(--rx-success-100)]"
      />
      {launchCopy(cities.data)}
    </span>
  )
}

/** "Where we are live": the whole section waits for a non-empty `GET /cities` (D-85). */
function Coverage() {
  const cities = useCities()
  if (!cities.isSuccess || cities.data.length === 0) return null
  return (
    <Section
      id="coverage"
      eyebrow="Coverage"
      title="Where we are live."
      intro="Search by locality, gated society or PIN code."
      center
      className="border-y border-line bg-sunk"
    >
      <LandingCoverage cities={cities.data} />
      <p className="mt-5 text-center text-sm text-ink-500">More cities are on the way.</p>
    </Section>
  )
}

const NAV = [
  ["#how", "How it works"],
  ["#for-tenants", "For tenants"],
  ["#for-landlords", "For landlords"],
  ["#trust", "Trust"],
  ["#faq", "FAQ"],
] as const

/** What goes wrong today, per side, and for everyone. */
const PROBLEMS = [
  {
    icon: <TenantIcon size={24} />,
    tone: "bg-tenant-100 text-tenant-600",
    title: "Tenants get burned",
    text: "Deposits kept without a reason, repairs ignored for weeks, surprise rent rises at renewal.",
  },
  {
    icon: <LandlordIcon size={24} />,
    tone: "bg-landlord-100 text-landlord-600",
    title: "Landlords take a gamble",
    text: "Rent paid late, homes handed back damaged, and no way to check a tenant before move-in.",
  },
  {
    icon: <DocumentIcon size={24} />,
    tone: "bg-sunk text-ink-700",
    title: "No record anyone trusts",
    text: "Group-chat hearsay and anonymous stars vanish. Nothing follows a landlord or a tenant to the next rental.",
  },
]

const STEPS = [
  {
    icon: <LoginIcon size={26} />,
    title: "Sign in with Google",
    text: "Pick your side: tenant, landlord or both. That is all it takes to search homes and read reviews.",
  },
  {
    icon: <KeyIcon size={26} />,
    title: "Verify once, add your rental",
    text: "Verify with any one: phone, Aadhaar or LinkedIn. Then add the home you rent or rent out.",
  },
  {
    icon: <StarIcon size={26} />,
    title: "Rate each other",
    text: "Tenants rate the home and the landlord; landlords rate the tenant. The next person reads it before they sign.",
  },
]

const AUDIENCES = [
  {
    id: "for-tenants",
    side: "tenant",
    icon: <TenantIcon size={26} />,
    title: "For tenants",
    sub: "Rent with your eyes open.",
    benefits: [
      ["Check the landlord first", "How they handled the deposit, repairs and rent rises."],
      [
        "Know the home",
        "Upkeep, water, power, noise and the society, from people who lived there.",
      ],
      ["See real rents", "What past tenants paid, where both sides chose to share it."],
      ["Build your renter record", "Share good ratings from past landlords with the next one."],
    ],
    link: { to: "/search", label: "Search homes" },
  },
  {
    id: "for-landlords",
    side: "landlord",
    icon: <LandlordIcon size={26} />,
    title: "For landlords",
    sub: "Hand over the keys with confidence.",
    benefits: [
      [
        "Check a tenant before move-in",
        "See how past landlords rated them when they share their record or enquire.",
      ],
      ["List your homes", "Add each home you rent out, with its details, in a few minutes."],
      ["Answer reviews in public", "Reply once, under any review of you or your home."],
      ["Earn the green tick", "Our team checks your homes and tenancies; checked ones carry it."],
    ],
    link: { to: "/login", label: "List your home" },
  },
] as const

const TRUST = [
  {
    tag: "Any one",
    icon: <MessageIcon size={22} />,
    title: "Phone OTP",
    text: "Confirm your mobile number with a one-time code. Others never see the number.",
    badge: null,
  },
  {
    tag: "Any one",
    icon: <ShieldCheckIcon size={22} />,
    title: "Aadhaar",
    text: 'Upload a masked Aadhaar for an "ID verified" badge. Others see only the badge, never the number.',
    badge: <VerifiedBadge />,
  },
  {
    tag: "Any one",
    icon: <LinkIcon size={22} />,
    title: "LinkedIn",
    text: "Link your LinkedIn profile. Any one of these three lets you post; the others add extra badges.",
    badge: (
      <Badge variant="info">
        <LinkIcon />
        LinkedIn verified
      </Badge>
    ),
  },
  {
    tag: "By our team",
    icon: <CheckIcon size={22} />,
    title: "Green tick",
    text: `Green tick = approved by the ${BRAND.name} team after an ID check. We also check homes, tenancies and reviews.`,
    badge: <GreenTick />,
  },
]

const FAQ = [
  [
    "Do I need an account?",
    "To search homes and read full reviews, sign in with Google; it is one step. A home's page and its ratings open for anyone with the link. To post, verify with any one: phone, Aadhaar or LinkedIn.",
  ],
  [
    "Who can post a review?",
    "A tenant or landlord verified with any one of phone, Aadhaar or LinkedIn, about a tenancy they add. Tenants rate the home and the landlord; landlords rate the tenant.",
  ],
  [
    "What does the green tick mean?",
    `Our team has checked that user, home, tenancy or review: "Verified by ${BRAND.name}". Until then a post shows as "Not verified yet".`,
  ],
  [
    "Is the rent I paid shown to everyone?",
    'Only when both you and the other party choose to share it. Otherwise the home says "Rent history: kept private by the parties".',
  ],
  [
    "Will others see my Aadhaar, PAN or phone number?",
    'No. Others see only a badge such as "ID verified". You need any one of phone, Aadhaar or LinkedIn; the rest are optional.',
  ],
] as const

/**
 * `/`: the landing for anonymous visitors, a mobile-first scrolling page. Hero with the call to
 * action and a sample app screen; the problem; how it works; one card per side; trust and
 * verification; a row of home and review cards; the live cities; FAQ; the call to action again.
 * A signed-in visitor goes into the app.
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
      <header className="sticky top-0 z-20 border-b border-line bg-paper/90 backdrop-blur-md">
        <Container className="flex h-16 items-center gap-6 lg:px-8">
          <Brand />
          <nav
            aria-label="Sections"
            className="ml-auto hidden gap-7 text-[15px] font-medium lg:flex"
          >
            {NAV.map(([href, label]) => (
              <a key={href} href={href} className="text-ink-700 no-underline hover:text-teal-700">
                {label}
              </a>
            ))}
          </nav>
          <Button asChild variant="outline" className="ml-auto lg:ml-0">
            <Link to="/login">Sign in</Link>
          </Button>
        </Container>
      </header>

      <main className="flex-1">
        <section
          aria-labelledby="hero-title"
          className="relative overflow-hidden bg-[radial-gradient(900px_500px_at_85%_10%,var(--rx-teal-100),transparent_60%),radial-gradient(600px_400px_at_0%_100%,var(--rx-marigold-100),transparent_60%)] pt-10 pb-14 lg:pt-22 lg:pb-26"
        >
          <Container className="grid items-center gap-10 lg:grid-cols-[1.08fr_0.92fr] lg:gap-14 lg:px-8">
            <div className="flex flex-col gap-5 motion-safe:animate-in motion-safe:duration-200 motion-safe:fade-in motion-safe:slide-in-from-bottom-2">
              <LaunchPill />
              <h1
                id="hero-title"
                className="font-display text-4xl leading-[1.08] font-semibold tracking-tight text-ink-900 lg:text-[3.5rem]"
              >
                Know the home, the landlord <span className="text-teal-600">and</span> the tenant
                before you sign.
              </h1>
              <p className={LEAD}>
                Real rents and two-sided reviews from people who actually lived there. Tenants rate
                homes and landlords; landlords rate tenants. A green tick shows what our team has
                verified.
              </p>
              <div className="mt-1 flex flex-wrap items-center gap-3">
                <GoogleCta className="h-[52px] rounded-xl shadow-[0_6px_18px_rgb(15_118_110/0.25)]" />
                <Button
                  asChild
                  variant="ghost"
                  size="lg"
                  className="h-[52px] w-full rounded-xl text-teal-700 hover:bg-teal-50 hover:text-teal-700 sm:w-auto"
                >
                  <a href="#how">
                    See how it works
                    <ChevronDownIcon />
                  </a>
                </Button>
              </div>
              <ul className="flex flex-wrap gap-x-5 gap-y-2 text-sm text-ink-500">
                {["One Google account for both sides", "Phone, Aadhaar and PAN stay private"].map(
                  (line) => (
                    <li key={line} className="inline-flex items-center gap-1.5">
                      <CheckIcon size={16} className="text-success-600" />
                      {line}
                    </li>
                  ),
                )}
              </ul>
            </div>
            <AppMock />
          </Container>
        </section>

        <Section
          id="problem"
          eyebrow="The problem"
          title="Renting runs on trust nobody can check."
          intro="You hand over a deposit or a set of keys to a stranger, on a broker's word and a 15-minute visit."
        >
          <ul className="grid gap-4 md:grid-cols-3 md:gap-6">
            {PROBLEMS.map((p) => (
              <li
                key={p.title}
                className="flex flex-col gap-3 rounded-[20px] border border-line bg-surface p-6"
              >
                <span className={cn("grid size-12 place-items-center rounded-[14px]", p.tone)}>
                  {p.icon}
                </span>
                <h3 className="text-[19px] font-semibold text-ink-900">{p.title}</h3>
                <p className="text-[15px] text-ink-700">{p.text}</p>
              </li>
            ))}
          </ul>
          <p className="mt-6 flex items-start gap-3 rounded-2xl bg-teal-50 px-5 py-4 text-teal-800">
            <TenancyIcon size={22} className="mt-0.5 text-teal-600" />
            <span>
              <strong>{BRAND.name} keeps that record.</strong> Each side rates the other after a
              real tenancy, and the record stays with the home and the people.
            </span>
          </p>
        </Section>

        <Section
          id="how"
          eyebrow="How it works"
          title="Three steps, a few minutes."
          center
          className="border-y border-line bg-sunk"
        >
          <ol className="grid gap-7 lg:grid-cols-3 lg:gap-10">
            {STEPS.map((step, i) => (
              <li
                key={step.title}
                className={cn(
                  "relative grid grid-cols-[56px_1fr] gap-4 lg:grid-cols-1",
                  i < STEPS.length - 1 &&
                    "before:absolute before:top-[60px] before:-bottom-7 before:left-[27px] before:border-l-2 before:border-dashed before:border-line-strong lg:before:top-7 lg:before:-right-10 lg:before:bottom-auto lg:before:left-16 lg:before:border-t-2 lg:before:border-l-0",
                )}
              >
                <span className="relative z-[1] grid size-14 place-items-center rounded-[18px] bg-teal-600 text-white shadow-[0_8px_18px_rgb(15_118_110/0.25)]">
                  {step.icon}
                </span>
                <div>
                  <span className="text-xs font-bold tracking-[0.08em] text-teal-700 uppercase">
                    Step {i + 1}
                  </span>
                  <h3 className="mt-0.5 mb-1.5 text-[19px] font-semibold text-ink-900">
                    {step.title}
                  </h3>
                  <p className="text-[15px] text-ink-700">{step.text}</p>
                </div>
              </li>
            ))}
          </ol>
        </Section>

        <Section
          id="audience"
          eyebrow="Built for both sides"
          title="One place, two points of view."
          center
        >
          <div className="grid gap-5 lg:grid-cols-2 lg:gap-7">
            {AUDIENCES.map((a) => {
              const tenant = a.side === "tenant"
              return (
                <article
                  key={a.id}
                  id={a.id}
                  aria-labelledby={`${a.id}-title`}
                  className="flex scroll-mt-20 flex-col overflow-hidden rounded-3xl border border-line bg-surface"
                >
                  <div
                    className={cn(
                      "flex items-center gap-4 p-6",
                      tenant ? "bg-tenant-100" : "bg-landlord-100",
                    )}
                  >
                    <span
                      className={cn(
                        "grid size-13 place-items-center rounded-[14px] bg-white",
                        tenant ? "text-tenant-600" : "text-landlord-600",
                      )}
                    >
                      {a.icon}
                    </span>
                    <div>
                      <h3
                        id={`${a.id}-title`}
                        className="font-display text-2xl leading-tight font-semibold text-ink-900"
                      >
                        {a.title}
                      </h3>
                      <p className="text-sm text-ink-700">{a.sub}</p>
                    </div>
                  </div>
                  <ul className="grid gap-3.5 px-6 pt-5 pb-2">
                    {a.benefits.map(([title, text]) => (
                      <li
                        key={title}
                        className="grid grid-cols-[24px_1fr] gap-2.5 text-[15px] text-ink-700"
                      >
                        <CheckIcon
                          size={22}
                          className={cn(
                            "rounded-full p-[3px]",
                            tenant
                              ? "bg-tenant-100 text-tenant-600"
                              : "bg-landlord-100 text-landlord-600",
                          )}
                        />
                        <span>
                          <strong className="block font-semibold text-ink-900">{title}</strong>
                          {text}
                        </span>
                      </li>
                    ))}
                  </ul>
                  <div className="mt-auto px-6 pt-3 pb-6">
                    <Link
                      to={a.link.to}
                      className={cn(
                        "inline-flex min-h-11 items-center gap-1.5 text-[15px] font-semibold",
                        !tenant && "text-landlord-600",
                      )}
                    >
                      {a.link.label}
                      <ArrowRightIcon size={18} />
                    </Link>
                  </div>
                </article>
              )
            })}
          </div>
        </Section>

        <section
          id="trust"
          aria-labelledby="trust-title"
          className="relative scroll-mt-16 overflow-hidden bg-teal-800 py-16 text-[#e6f4f1] lg:py-26"
        >
          <span
            aria-hidden
            className="absolute -top-30 -right-30 size-[420px] rounded-full bg-[radial-gradient(closest-side,rgb(20_149_138/0.45),transparent)]"
          />
          <Container className="relative lg:px-8">
            <div className="mb-8 flex flex-col gap-3 lg:mb-12">
              <span className={cn(EYEBROW, "text-[#8fd8cd]")}>Trust and verification</span>
              <h2 id="trust-title" className={cn(H2, "text-white")}>
                Open to everyone. Verified where it counts.
              </h2>
              <p className={cn(LEAD, "text-[#c4e4df]")}>
                Verify with any one: phone, Aadhaar or LinkedIn. Then you can post, and posts show
                at once as "Not verified yet". Green tick = approved by the {BRAND.name} team after
                an ID check.
              </p>
            </div>
            <ul className="grid gap-3.5 sm:grid-cols-2 lg:grid-cols-4 lg:gap-4.5">
              {TRUST.map((t) => (
                <li
                  key={t.title}
                  className="grid grid-cols-[48px_1fr] items-start gap-3.5 rounded-[18px] border border-white/12 bg-white/6 p-4.5 lg:grid-cols-1 lg:p-5"
                >
                  <span className="grid size-12 place-items-center rounded-[14px] bg-white/10 text-[#8fd8cd]">
                    {t.icon}
                  </span>
                  <div className="flex h-full flex-col gap-2">
                    <span className="self-start rounded-md bg-[#8fd8cd] px-2 py-0.5 text-[11px] font-bold tracking-[0.06em] text-teal-800 uppercase">
                      {t.tag}
                    </span>
                    <h3 className="text-[17px] font-semibold text-white">{t.title}</h3>
                    <p className="text-sm text-[#c4e4df]">{t.text}</p>
                    {t.badge && <span className="mt-auto pt-1">{t.badge}</span>}
                  </div>
                </li>
              ))}
            </ul>
            <p className="mt-6 flex items-start gap-2 text-sm text-[#a9d3cc]">
              <InfoIcon size={18} className="mt-px" />
              We never show phone numbers, Aadhaar or PAN to other users.
            </p>
          </Container>
        </section>

        <Section id="preview" eyebrow="A look inside" title="Cards straight from the app.">
          <LandingPreview />
        </Section>

        <Coverage />

        <section id="faq" aria-labelledby="faq-title" className="scroll-mt-16 py-16 lg:py-26">
          <Container className="grid gap-8 lg:grid-cols-[0.8fr_1.2fr] lg:items-start lg:gap-16 lg:px-8">
            <div className="flex flex-col gap-3">
              <span className={EYEBROW}>FAQ</span>
              <h2 id="faq-title" className={cn(H2, "text-ink-900")}>
                Questions, answered.
              </h2>
            </div>
            <div className="grid gap-2.5">
              {FAQ.map(([q, a], i) => (
                <details
                  key={q}
                  open={i === 0}
                  className="group rounded-2xl border border-line bg-surface px-5"
                >
                  <summary className="flex min-h-15 cursor-pointer list-none items-center justify-between gap-3 font-semibold text-ink-900 [&::-webkit-details-marker]:hidden">
                    {q}
                    <ChevronDownIcon
                      size={20}
                      className="text-ink-500 transition-transform duration-200 ease-rx group-open:rotate-180"
                    />
                  </summary>
                  <p className="pb-4.5 text-[15px] text-ink-700">{a}</p>
                </details>
              ))}
            </div>
          </Container>
        </section>

        <section aria-labelledby="cta-title" className="pb-16 lg:pb-26">
          <Container className="lg:px-8">
            <div className="flex flex-col items-center gap-4 rounded-[28px] bg-teal-700 bg-[radial-gradient(500px_260px_at_100%_0%,rgb(20_149_138/0.55),transparent_70%)] px-6 py-10 text-center lg:px-12 lg:py-18">
              <h2 id="cta-title" className={cn(H2, "text-white")}>
                Rent with your eyes open.
              </h2>
              <p className="max-w-xl text-lg text-[#cdeae5] lg:text-xl">
                Read real rents and reviews, or add your own. One Google account works for both
                sides.
              </p>
              <GoogleCta className="h-[52px] rounded-xl bg-white text-teal-800 hover:bg-teal-50" />
            </div>
          </Container>
        </section>
      </main>

      <footer className="border-t border-line py-7 text-sm text-ink-500">
        <Container className="flex flex-wrap items-center justify-between gap-4 lg:px-8">
          <span>
            <span className="font-display font-semibold text-ink-900">{BRAND.name}</span> ·{" "}
            {BRAND.tagline}
          </span>
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
