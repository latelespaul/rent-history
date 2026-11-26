import { Fragment, useEffect, useState, type ReactNode } from "react"
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom"
import { Brand } from "@/components/layout/Brand"
import { Container } from "@/components/layout/Container"
import { FooterSearch } from "@/components/layout/Footer"
import { Badge, VerifiedBadge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  ArrowRightIcon,
  BadgeCheckIcon,
  BuildingIcon,
  ChevronDownIcon,
  DocumentXIcon,
  EyeOffIcon,
  HomeIcon,
  IdCardIcon,
  LandlordIcon,
  LinkIcon,
  LockIcon,
  LoginIcon,
  PhoneIcon,
  RupeeIcon,
  SearchIcon,
  ShieldCheckIcon,
  StarIcon,
  TenancyIcon,
  TenantIcon,
  TrendUpIcon,
} from "@/components/ui/icons.gen"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useAuth } from "@/features/auth/authContext"
import { AuthPending } from "@/features/auth/RequireAuth"
import { chipNames } from "@/features/city/cityText"
import { useCities } from "@/features/city/useCities"
import { AppMock } from "@/features/landing/AppMock"
import { GoogleCta } from "@/features/landing/GoogleCta"
import { LandingCoverage } from "@/features/landing/LandingCoverage"
import { GreenTick, LandingPreview } from "@/features/landing/LandingPreview"
import { SearchBar } from "@/features/property/SearchBar"
import { withFilters } from "@/features/property/searchParams"
import { BRAND } from "@/lib/brand"
import { cn } from "@/lib/utils"

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
      className={cn("scroll-mt-16 py-20 lg:py-32", className)}
    >
      <Container className="lg:px-8">
        <div
          className={cn(
            "mb-10 flex flex-col gap-3 lg:mb-16",
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

/** How many city chips the hero names before it shows "+N more" (captain 2026-11-26). */
const HERO_CITY_CHIPS = 2

/**
 * A short "Live in" line from `GET /cities` (D-85): the first two cities as chips, then "+N more"
 * (or "+ More soon" when there are no more); nothing until a non-empty list loads. The full list
 * is the coverage section further down, so the first screen stays open (captain 2026-11-26).
 */
function CityChips() {
  const cities = useCities()
  if (!cities.isSuccess || cities.data.length === 0) return null
  const names = chipNames(cities.data)
  const more = names.length - HERO_CITY_CHIPS
  return (
    <div className="flex items-center gap-2">
      <span className="inline-flex h-8 shrink-0 items-center gap-2 text-[13px] font-semibold text-ink-700">
        <span
          aria-hidden
          className="size-2 rounded-full bg-success-600 shadow-[0_0_0_4px_var(--rx-success-100)]"
        />
        Live in
      </span>
      <ul aria-label="Live cities" className="flex min-w-0 flex-wrap gap-1.5">
        {names.slice(0, HERO_CITY_CHIPS).map((name) => (
          <li
            key={name}
            className="inline-flex h-8 shrink-0 items-center rounded-full border border-line bg-surface px-3 text-[13px] font-semibold text-ink-900 shadow-sm"
          >
            {name}
          </li>
        ))}
        <li className="inline-flex h-8 shrink-0 items-center rounded-full border border-dashed border-line-strong px-3 text-[13px] font-semibold text-ink-700">
          {more > 0 ? `+${more} more` : "+ More soon"}
        </li>
      </ul>
    </div>
  )
}

/**
 * "Live in N cities": one tile per city, the count and names from `GET /cities` (D-85), never
 * written here. The whole section waits for a non-empty list.
 */
function Coverage() {
  const cities = useCities()
  if (!cities.isSuccess || cities.data.length === 0) return null
  return (
    <Section
      id="coverage"
      eyebrow="Coverage"
      title={cities.data.length === 1 ? "Live in 1 city." : `Live in ${cities.data.length} cities.`}
      center
      className="border-y border-line bg-sunk"
    >
      <LandingCoverage cities={cities.data} />
      <p className="mt-8 text-center text-sm text-ink-500">More cities are on the way.</p>
    </Section>
  )
}

/**
 * The hero's search box: the same `SearchBar` as `/search` (city + locality or society, then a flat),
 * so anyone can search from the first screen without signing in (captain 2026-11-25). A text search
 * or a society pick opens `/search` with those filters; a flat or tower pick opens its page.
 */
function HeroSearch() {
  const navigate = useNavigate()
  const [city, setCity] = useState<string | null>(null)
  return (
    <SearchBar
      city={city}
      query=""
      onCityChange={setCity}
      onSubmit={({ city: picked, query, society }) =>
        navigate({
          pathname: "/search",
          search: withFilters(new URLSearchParams(), {
            city: picked ?? undefined,
            q: query,
            society,
          }).toString(),
        })
      }
    />
  )
}

const NAV = [
  ["#how", "How it works"],
  ["#for-tenants", "For tenants"],
  ["#for-landlords", "For landlords"],
  ["#trust", "Trust"],
  ["#faq", "FAQ"],
] as const

/** The hero's three promises, one icon badge each, in place of a lead paragraph. */
const HERO_BADGES = [
  { icon: <StarIcon size={20} />, label: "Two-sided reviews" },
  { icon: <TrendUpIcon size={20} />, label: "Real rents" },
  { icon: <ShieldCheckIcon size={20} />, label: "Team-verified" },
]

/** What goes wrong today, per side, and for everyone: one icon tile and a few words each. */
const PROBLEMS = [
  {
    icon: <TenantIcon size={24} />,
    tone: "bg-tenant-100 text-tenant-600",
    title: "Deposits kept, repairs ignored",
  },
  {
    icon: <LandlordIcon size={24} />,
    tone: "bg-landlord-100 text-landlord-600",
    title: "Late rent, damaged homes",
  },
  {
    icon: <DocumentXIcon size={24} />,
    tone: "bg-sunk text-ink-700",
    title: "No record follows anyone",
  },
]

/** Three steps, each an icon and a label of 3-5 words, one line max (captain 2026-11-26). */
const STEPS = [
  { icon: <LoginIcon size={26} />, title: "Sign in with Google" },
  { icon: <BadgeCheckIcon size={26} />, title: "Verify once" },
  { icon: <StarIcon size={26} />, title: "Rate your rental" },
]

/** One card per side: three one-line icon rows, a few words each, and one link (captain 2026-11-26). */
const AUDIENCES = [
  {
    id: "for-tenants",
    side: "tenant",
    tab: "I rent",
    icon: TenantIcon,
    title: "For tenants",
    tiles: [
      [LandlordIcon, "Check the landlord"],
      [HomeIcon, "Know the home"],
      [RupeeIcon, "See real rents"],
    ],
    link: { to: "/search", label: "Search homes" },
  },
  {
    id: "for-landlords",
    side: "landlord",
    tab: "I own",
    icon: LandlordIcon,
    title: "For landlords",
    tiles: [
      [TenantIcon, "Check a tenant"],
      [BuildingIcon, "List your homes"],
      [ShieldCheckIcon, "Earn the green tick"],
    ],
    link: { to: "/login", label: "List your home" },
  },
] as const

/** A small chip on the dark trust band: an icon, then a word (white on white/10, contrast AA). */
function BandChip({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <li className="inline-flex h-8 items-center gap-1.5 rounded-full border border-white/20 bg-white/10 pr-3 pl-2.5 text-[13px] font-semibold text-white">
      {icon}
      {children}
    </li>
  )
}

/**
 * The trust ladder: three stages, each the real badge a post or a user carries at that stage
 * (brief §3.5) and a caption of 2-3 words. Stage 2 names the three ways to verify (any one, D-144).
 */
const TRUST_STAGES = [
  {
    caption: "Post at once",
    badge: <Badge variant="neutral">Not verified yet</Badge>,
  },
  {
    caption: "Verify once",
    badge: (
      <div className="flex flex-col items-center gap-2">
        <span className="text-[11px] font-bold tracking-[0.08em] text-[#8fd8cd] uppercase">
          Any one
        </span>
        <ul aria-label="Verify with any one" className="flex flex-wrap justify-center gap-2">
          <BandChip icon={<PhoneIcon size={16} className="text-[#8fd8cd]" />}>Phone</BandChip>
          <BandChip icon={<IdCardIcon size={16} className="text-[#8fd8cd]" />}>Aadhaar</BandChip>
          <BandChip icon={<LinkIcon size={16} className="text-[#8fd8cd]" />}>LinkedIn</BandChip>
        </ul>
        <VerifiedBadge />
      </div>
    ),
  },
  {
    caption: "Checked by our team",
    // on white: success-600 on its success-100 tint is 4.23:1, below AA (brief §4.5)
    badge: <GreenTick className="bg-white" />,
  },
]

const FAQ = [
  [
    "Do I need an account?",
    "No. Anyone can search homes and read their reviews. Sign in with Google (one step) for rent history and landlord profiles. Signed in, you can post at once; verifying with any one of phone, Aadhaar or LinkedIn gets you a green tick.",
  ],
  [
    "Who can post a review?",
    'Any signed-in tenant or landlord, about a tenancy they add. It shows as "Not verified yet" until our team verifies it, and only verified reviews count in the headline rating. Tenants rate the home and the landlord; landlords rate the tenant.',
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
 * `/`: the landing for anonymous visitors, a mobile-first scrolling page. Hero with the search box,
 * the call to action and a sample app screen; the problem; how it works; one card per side; trust and
 * verification; a row of home and review cards; the live cities; FAQ; the call to action again.
 * A signed-in visitor goes to `/search`, which opens with the same search box.
 */
export default function HomePage() {
  const { state, signOut } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const leaving = location.state as { signedOut?: boolean; accountDeleted?: boolean } | null
  const signingOut = leaving?.signedOut === true
  const accountDeleted = leaving?.accountDeleted === true
  // After a deletion (D-58) the local session ends here, once the landing is on screen: ending it on
  // the guarded delete page would let its guard send the user to /login while the route changes.
  useEffect(() => {
    if (accountDeleted && state.status === "signedIn") void signOut()
  }, [accountDeleted, state.status, signOut])
  useEffect(() => {
    if (signingOut && state.status === "anonymous") navigate("/", { replace: true })
  }, [signingOut, state.status, navigate])
  if (state.status === "loading") return <AuthPending />
  if (state.status === "signedIn" && !signingOut) {
    // A signed-in visitor goes to Search; the admin console is on its own host (SYSTEM_DESIGN §13.3).
    return <Navigate to="/search" replace />
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
          className="relative overflow-hidden bg-[radial-gradient(900px_500px_at_85%_10%,var(--rx-teal-100),transparent_60%),radial-gradient(600px_400px_at_0%_100%,var(--rx-marigold-100),transparent_60%)] pt-12 pb-16 lg:pt-26 lg:pb-32"
        >
          <Container className="grid items-center gap-14 lg:grid-cols-[1.08fr_0.92fr] lg:gap-20 lg:px-8">
            <div className="flex flex-col gap-6 motion-safe:animate-in motion-safe:duration-200 motion-safe:fade-in motion-safe:slide-in-from-bottom-2 max-lg:min-w-0 lg:gap-7">
              <CityChips />
              <h1
                id="hero-title"
                className="font-display text-[2rem] leading-[1.1] font-semibold tracking-tight text-ink-900 lg:text-[3.5rem]"
              >
                Check the home, the landlord <span className="text-teal-600">and</span> the tenant.
              </h1>
              <HeroSearch />
              <ul className="grid grid-cols-3 gap-2 sm:flex sm:flex-wrap">
                {HERO_BADGES.map((b) => (
                  <li
                    key={b.label}
                    className="flex flex-col items-center gap-1 rounded-2xl border border-line bg-surface/80 px-1.5 py-2 text-center text-xs leading-tight font-semibold text-ink-900 sm:h-9 sm:flex-row sm:rounded-full sm:py-0 sm:pr-3.5 sm:pl-2.5 sm:text-[13px]"
                  >
                    <span className="text-teal-600">{b.icon}</span>
                    {b.label}
                  </li>
                ))}
              </ul>
              <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
                <GoogleCta
                  variant="accent"
                  className="h-[52px] rounded-xl shadow-[0_6px_18px_rgb(201_138_12/0.3)]"
                />
                <Button
                  asChild
                  variant="ghost"
                  size="lg"
                  className="hidden h-[52px] rounded-xl text-teal-700 hover:bg-teal-50 hover:text-teal-700 lg:inline-flex"
                >
                  <a href="#how">
                    See how it works
                    <ChevronDownIcon />
                  </a>
                </Button>
              </div>
              <p className="inline-flex items-center gap-1.5 text-sm text-ink-700">
                <LockIcon size={16} className="text-teal-700" />
                Phone, Aadhaar, PAN stay private
              </p>
            </div>
            <AppMock />
          </Container>
        </section>

        <Section id="problem" eyebrow="The problem" title="Renting runs on trust nobody can check.">
          <ul className="grid grid-cols-3 gap-2.5 md:gap-6">
            {PROBLEMS.map((p) => (
              <li
                key={p.title}
                className="flex flex-col items-center gap-2.5 rounded-[20px] border border-line bg-surface px-2 py-4 text-center md:p-6"
              >
                <span className={cn("grid size-12 place-items-center rounded-full", p.tone)}>
                  {p.icon}
                </span>
                <h3 className="text-sm leading-snug font-semibold text-ink-900 md:text-[17px]">
                  {p.title}
                </h3>
              </li>
            ))}
          </ul>
          <p className="mt-5 inline-flex items-center gap-2.5 rounded-full bg-teal-50 py-2.5 pr-5 pl-3 font-semibold text-teal-800">
            <TenancyIcon size={22} className="text-teal-700" />
            {BRAND.name} keeps the record.
          </p>
        </Section>

        <Section
          id="how"
          eyebrow="How it works"
          title="Three steps, a few minutes."
          center
          className="border-y border-line bg-sunk"
        >
          <ol className="grid gap-8 lg:grid-cols-3 lg:gap-12">
            {STEPS.map((step, i) => (
              <li
                key={step.title}
                className={cn(
                  "relative flex items-center gap-5 lg:flex-col lg:gap-4",
                  i < STEPS.length - 1 &&
                    "before:absolute before:top-[60px] before:-bottom-8 before:left-[27px] before:border-l-2 before:border-dashed before:border-line-strong lg:before:top-7 lg:before:right-[calc(-50%-12px)] lg:before:bottom-auto lg:before:left-[calc(50%+36px)] lg:before:border-t-2 lg:before:border-l-0",
                )}
              >
                <span className="relative z-[1] grid size-14 shrink-0 place-items-center rounded-[18px] bg-teal-600 text-white shadow-[0_8px_18px_rgb(15_118_110/0.25)]">
                  {step.icon}
                  <span
                    aria-hidden
                    className="absolute -top-2 -right-2 grid size-6 place-items-center rounded-full border-2 border-sunk bg-ink-900 text-xs font-bold text-white"
                  >
                    {i + 1}
                  </span>
                </span>
                <h3 className="text-[19px] font-semibold text-ink-900">{step.title}</h3>
              </li>
            ))}
          </ol>
          <p className="mt-10 inline-flex items-center gap-2 rounded-full bg-surface py-2 pr-4 pl-3 text-sm font-semibold text-ink-900 lg:mx-auto lg:mt-14 lg:flex lg:w-fit">
            <SearchIcon size={18} className="text-teal-600" />
            Search and read reviews with no account.
          </p>
        </Section>

        <Section
          id="audience"
          eyebrow="Built for both sides"
          title="One place, two points of view."
          center
        >
          {/* On a phone a segmented "I rent / I own" toggle shows one card; from 1024 px both show. */}
          <Tabs defaultValue="tenant">
            <TabsList
              aria-label="Choose your side"
              className="mb-6 w-full max-w-sm self-center lg:hidden"
            >
              {AUDIENCES.map(({ side, tab, icon: Icon }) => (
                <TabsTrigger
                  key={side}
                  value={side}
                  className={
                    side === "tenant"
                      ? "data-[state=active]:text-tenant-600"
                      : "data-[state=active]:text-landlord-600"
                  }
                >
                  <Icon size={20} />
                  {tab}
                </TabsTrigger>
              ))}
            </TabsList>
            <div className="mx-auto grid w-full max-w-4xl gap-5 lg:grid-cols-2 lg:gap-8">
              {AUDIENCES.map((a) => {
                const tenant = a.side === "tenant"
                const tint = tenant
                  ? "bg-tenant-100 text-tenant-600"
                  : "bg-landlord-100 text-landlord-600"
                return (
                  <TabsContent
                    key={a.id}
                    value={a.side}
                    forceMount
                    className="data-[state=inactive]:max-lg:hidden"
                  >
                    <article
                      id={a.id}
                      aria-labelledby={`${a.id}-title`}
                      className="flex h-full scroll-mt-20 flex-col overflow-hidden rounded-3xl border border-line bg-surface"
                    >
                      <div
                        className={cn(
                          "flex items-center gap-4 p-5 lg:p-6",
                          tenant ? "bg-tenant-100" : "bg-landlord-100",
                        )}
                      >
                        <span
                          className={cn(
                            "grid size-13 place-items-center rounded-[14px] bg-white",
                            tenant ? "text-tenant-600" : "text-landlord-600",
                          )}
                        >
                          <a.icon size={26} />
                        </span>
                        <h3
                          id={`${a.id}-title`}
                          className="font-display text-2xl leading-tight font-semibold text-ink-900"
                        >
                          {a.title}
                        </h3>
                      </div>
                      <ul className="grid gap-3 p-5 lg:p-7">
                        {a.tiles.map(([Icon, label]) => (
                          <li
                            key={label}
                            className="flex items-center gap-3.5 rounded-2xl border border-line bg-paper px-4 py-3"
                          >
                            <span
                              className={cn("grid size-10 place-items-center rounded-full", tint)}
                            >
                              <Icon size={22} />
                            </span>
                            <span className="text-[15px] leading-snug font-semibold text-ink-900">
                              {label}
                            </span>
                          </li>
                        ))}
                      </ul>
                      <div className="mt-auto px-5 pb-5 lg:px-6 lg:pb-6">
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
                  </TabsContent>
                )
              })}
            </div>
          </Tabs>
        </Section>

        <section
          id="trust"
          aria-labelledby="trust-title"
          className="relative scroll-mt-16 overflow-hidden bg-teal-800 py-20 text-[#e6f4f1] lg:py-32"
        >
          <span
            aria-hidden
            className="absolute -top-30 -right-30 size-[420px] rounded-full bg-[radial-gradient(closest-side,rgb(20_149_138/0.45),transparent)]"
          />
          <Container className="relative lg:px-8">
            <div className="mb-10 flex flex-col gap-3 lg:mb-16 lg:items-center lg:text-center">
              <span className={cn(EYEBROW, "text-[#8fd8cd]")}>Trust and verification</span>
              <h2 id="trust-title" className={cn(H2, "text-white")}>
                Verified where it counts.
              </h2>
            </div>
            <ol className="mx-auto grid max-w-6xl gap-3 lg:grid-cols-[1fr_auto_1fr_auto_1fr] lg:items-stretch lg:gap-5">
              {TRUST_STAGES.map((stage, i) => (
                <Fragment key={stage.caption}>
                  {i > 0 && (
                    <li aria-hidden className="grid place-items-center text-[#8fd8cd]">
                      <ArrowRightIcon size={24} className="rotate-90 lg:rotate-0" />
                    </li>
                  )}
                  <li className="flex flex-col items-center justify-center gap-2.5 rounded-[20px] border border-white/12 bg-white/6 px-5 py-5 text-center lg:py-7">
                    <span className="grid size-8 place-items-center rounded-full bg-white/10 text-sm font-bold text-white">
                      {i + 1}
                    </span>
                    {stage.badge}
                    <h3 className="text-[17px] font-semibold text-white">{stage.caption}</h3>
                  </li>
                </Fragment>
              ))}
            </ol>
            <p className="mt-10 flex items-center justify-center gap-2 text-sm font-semibold text-[#c4e4df]">
              <EyeOffIcon size={18} />
              Others never see your numbers.
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
          <span className="flex flex-wrap items-center gap-2">
            <FooterSearch />
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
