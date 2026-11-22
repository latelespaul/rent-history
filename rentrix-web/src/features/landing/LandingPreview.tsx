import { useRef, type ReactNode } from "react"
import { Link } from "react-router-dom"
import { Badge, DemoBadge } from "@/components/ui/badge"
import { ArrowRightIcon, CheckIcon, ChevronRightIcon, MapPinIcon } from "@/components/ui/icons.gen"
import { useCities } from "@/features/city/useCities"
import { placeLine, propertyTitle } from "@/features/property/labels"
import { PropertyFacts } from "@/features/property/PropertyFacts"
import { RatingLine, Stars } from "@/features/property/Rating"
import { usePropertySearch } from "@/features/property/useProperties"
import { BRAND } from "@/lib/brand"
import { cn } from "@/lib/utils"
import type { PropertyCard } from "@/types/api"
import { Buildings } from "./AppMock"

/** How many homes the preview reads from `GET /properties` (public, D-56). */
export const PREVIEW_SIZE = 6

/** The green tick (D-134): the badge an admin review gives a user, home, tenancy or review. */
export function GreenTick() {
  return (
    <Badge variant="success">
      <CheckIcon />
      Verified by {BRAND.name}
    </Badge>
  )
}

/** Picture tints for the cards, in turn: teal, marigold, tenant blue. */
const TINTS = [
  { bg: "from-[#d6ece8] to-[#9ccbc2]", tone: "#9ccbc2" },
  { bg: "from-[#fbecd0] to-[#f1cf94]", tone: "#f1cf94" },
  { bg: "from-[#e2e9fa] to-[#b9c8ef]", tone: "#b9c8ef" },
]

const CARD =
  "flex snap-start flex-col overflow-hidden rounded-2xl border border-line bg-surface shadow-sm transition-[border-color,transform] duration-200 ease-rx motion-safe:hover:-translate-y-0.5"

function Picture({ index, children }: { index: number; children?: ReactNode }) {
  const tint = TINTS[index % TINTS.length]
  return (
    <div className={cn("relative h-36 overflow-hidden bg-linear-160", tint.bg)}>
      <Buildings tone={tint.tone} />
      {children}
    </div>
  )
}

/**
 * One published home, as anonymous visitors may see it: public fields only (D-56). No rent: a
 * card never carries one, and rent shows only where both parties chose to share it.
 */
function HomeCard({ property, index }: { property: PropertyCard; index: number }) {
  return (
    <article className={cn(CARD, "relative focus-within:border-teal-600 hover:border-teal-600")}>
      <Picture index={index}>
        {property.demo && <DemoBadge className="absolute top-3 left-3" />}
      </Picture>
      <div className="flex flex-1 flex-col gap-2 p-4">
        <h3 className="text-[17px] font-semibold">
          <Link
            to={`/p/${encodeURIComponent(property.id)}`}
            className="text-ink-900 no-underline after:absolute after:inset-0 hover:no-underline"
          >
            {propertyTitle(property)}
          </Link>
        </h3>
        <RatingLine rating={property.rating} />
        <p className="inline-flex items-center gap-1.5 text-sm text-ink-700">
          <MapPinIcon size={16} className="text-ink-500" />
          {placeLine(property)}
        </p>
        <PropertyFacts property={property} />
      </div>
      <p className="flex items-center justify-between border-t border-line px-4 py-3 text-sm text-ink-700">
        <span>
          <span className="font-semibold text-ink-900">{property.landlordName}</span> · landlord
        </span>
        <ChevronRightIcon size={18} className="text-ink-500" />
      </p>
    </article>
  )
}

/**
 * Stand-in when the API has no published home to show (or cannot be reached). It says
 * "Sample" and names no real place or person; a city appears only when `GET /cities` lists it.
 */
function SampleHomeCard({ city, index }: { city: string | undefined; index: number }) {
  return (
    <article className={CARD}>
      <Picture index={index}>
        <Badge variant="warn" className="absolute top-3 left-3">
          Sample
        </Badge>
      </Picture>
      <div className="flex flex-1 flex-col gap-2 p-4">
        <h3 className="text-[17px] font-semibold text-ink-900">Sample apartment</h3>
        <span className="inline-flex items-center gap-1.5 text-sm">
          <Stars value={4} />
          <span className="text-ink-700">How a rated home looks</span>
        </span>
        {city && (
          <p className="inline-flex items-center gap-1.5 text-sm text-ink-700">
            <MapPinIcon size={16} className="text-ink-500" />
            {city}
          </p>
        )}
      </div>
      <p className="border-t border-line px-4 py-3 text-sm text-ink-700">
        Landlord name shown here
      </p>
    </article>
  )
}

/**
 * Review text reaches only signed-in readers (D-56), so the preview shows example reviews in
 * the words of the demo seed, each marked "Demo data": who rated whom, stars, a line and its
 * real state. The seed gives no review the green tick (D-134), so each one is "Not verified yet".
 */
const SAMPLE_REVIEWS = [
  {
    from: "Tenant",
    about: "the landlord",
    stars: 5,
    text: "Answered every message the same day and fixed the geyser within a week. The full deposit came back on time.",
  },
  {
    from: "Landlord",
    about: "the tenant",
    stars: 5,
    text: "Paid the rent on the first of every month and kept the home clean. Handed it back in good condition.",
  },
  {
    from: "Tenant",
    about: "the home",
    stars: 4,
    text: "Well maintained building with a working lift and good security. Shops and buses are a short walk away.",
  },
] as const

function SampleReview({ review }: { review: (typeof SAMPLE_REVIEWS)[number] }) {
  return (
    <article className={cn(CARD, "gap-3 p-5")}>
      <div className="flex flex-wrap items-center gap-2 text-sm text-ink-700">
        <Badge variant={review.from === "Tenant" ? "tenant" : "landlord"}>{review.from}</Badge>
        rates {review.about}
        <DemoBadge />
      </div>
      <span
        className="inline-flex items-center gap-1.5"
        aria-label={`${review.stars} out of 5 stars`}
      >
        <Stars value={review.stars} />
      </span>
      <blockquote className="font-display text-lg leading-snug font-medium text-ink-900">
        “{review.text}”
      </blockquote>
      <div className="mt-auto">
        <Badge variant="neutral">Not verified yet</Badge>
      </div>
    </article>
  )
}

/**
 * "A look inside": a row of cards to swipe (scroll-snap; arrow buttons on wide screens). Up to
 * six published homes from `GET /properties` (top rated; the seed marks its rows "Demo data"),
 * else sample cards marked "Sample", with the example reviews in between. No count or number
 * here is made up (D-03).
 */
export function LandingPreview() {
  const track = useRef<HTMLDivElement>(null)
  const homes = usePropertySearch({ sort: "top_rated", size: PREVIEW_SIZE })
  const cities = useCities()
  const live = homes.data?.items.slice(0, PREVIEW_SIZE) ?? []
  const cityNames = cities.data?.map((c) => c.name) ?? []

  const homeCards = homes.isPending
    ? Array.from({ length: 3 }, (_, i) => (
        <div
          key={`wait-${i}`}
          className="rx-placeholder h-72 snap-start rounded-2xl border border-line"
        />
      ))
    : live.length > 0
      ? live.map((property, i) => <HomeCard key={property.id} property={property} index={i} />)
      : Array.from({ length: 3 }, (_, i) => (
          <SampleHomeCard
            key={`sample-${i}`}
            index={i}
            city={cityNames.length ? cityNames[i % cityNames.length] : undefined}
          />
        ))
  // Homes and reviews take turns: home, review, home, review, …
  const cards: ReactNode[] = []
  homeCards.forEach((home, i) => {
    cards.push(home)
    if (i < SAMPLE_REVIEWS.length)
      cards.push(<SampleReview key={`review-${i}`} review={SAMPLE_REVIEWS[i]} />)
  })

  function scroll(direction: 1 | -1) {
    const el = track.current
    if (!el || typeof el.scrollBy !== "function") return
    const reduce = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches
    el.scrollBy({ left: direction * el.clientWidth * 0.8, behavior: reduce ? "auto" : "smooth" })
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="hidden justify-end gap-2 lg:flex">
        <button
          type="button"
          onClick={() => scroll(-1)}
          aria-label="Previous cards"
          className="grid size-11 place-items-center rounded-full border border-line-strong bg-surface text-ink-900 transition-colors duration-200 hover:bg-sunk"
        >
          <ArrowRightIcon size={20} className="rotate-180" />
        </button>
        <button
          type="button"
          onClick={() => scroll(1)}
          aria-label="Next cards"
          className="grid size-11 place-items-center rounded-full border border-line-strong bg-surface text-ink-900 transition-colors duration-200 hover:bg-sunk"
        >
          <ArrowRightIcon size={20} />
        </button>
      </div>
      <div
        ref={track}
        role="region"
        aria-label="Homes and reviews"
        aria-busy={homes.isPending}
        tabIndex={0}
        className="-mx-4 grid snap-x snap-mandatory scroll-px-4 [scrollbar-width:none] auto-cols-[84%] grid-flow-col gap-3.5 overflow-x-auto px-4 pt-1 pb-4 sm:auto-cols-[46%] lg:mx-0 lg:scroll-px-0 lg:auto-cols-[calc((100%-60px)/3.4)] lg:gap-5 lg:px-0 [&::-webkit-scrollbar]:hidden"
      >
        {cards}
      </div>
      <p className="text-center text-sm text-ink-500">
        <DemoBadge className="mr-1.5 align-[1px]" />
        marks the example homes and reviews we seeded for testing. They are not real tenancies.
      </p>
    </div>
  )
}
