import { Link } from "react-router-dom"
import { Badge, DemoBadge } from "@/components/ui/badge"
import { BuildingIcon, CheckIcon, MapPinIcon } from "@/components/ui/icons.gen"
import { useCities } from "@/features/city/useCities"
import { placeLine, propertyTitle } from "@/features/property/labels"
import { PropertyFacts } from "@/features/property/PropertyFacts"
import { RatingLine, Stars } from "@/features/property/Rating"
import { usePropertySearch } from "@/features/property/useProperties"
import { BRAND } from "@/lib/brand"
import type { PropertyCard } from "@/types/api"

/** How many homes the preview reads from `GET /properties` (public, D-56). */
export const PREVIEW_SIZE = 3

/** The green tick (D-134): the badge an admin review gives a user, home, tenancy or review. */
export function GreenTick() {
  return (
    <Badge variant="success">
      <CheckIcon />
      Verified by {BRAND.name}
    </Badge>
  )
}

function SampleBadge({ className }: { className?: string }) {
  return (
    <Badge variant="warn" className={className}>
      Sample
    </Badge>
  )
}

/** One published home, as anonymous visitors may see it: public fields only (D-56). */
function HomeCard({ property }: { property: PropertyCard }) {
  return (
    <article className="relative flex flex-col overflow-hidden rounded-xl border border-line bg-surface shadow-sm focus-within:border-teal-600 hover:border-teal-600">
      <div className="rx-placeholder relative grid h-28 place-items-center">
        <BuildingIcon size={32} className="text-ink-300" />
        {property.demo && <DemoBadge className="absolute top-3 left-3" />}
      </div>
      <div className="flex flex-1 flex-col gap-2 p-4">
        <h3 className="text-base font-semibold">
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
        <p className="mt-auto border-t border-line pt-2 text-sm text-ink-700">
          <span className="font-semibold text-ink-900">{property.landlordName}</span> · landlord
        </p>
      </div>
    </article>
  )
}

/**
 * Stand-ins when the API has no published home to show (or cannot be reached). Each card says
 * "Sample" and names no real place or person; a city appears only when `GET /cities` lists it.
 */
function SampleHomeCard({ city }: { city: string | undefined }) {
  return (
    <article className="flex flex-col overflow-hidden rounded-xl border border-line bg-surface shadow-sm">
      <div className="rx-placeholder relative grid h-28 place-items-center">
        <BuildingIcon size={32} className="text-ink-300" />
        <SampleBadge className="absolute top-3 left-3" />
      </div>
      <div className="flex flex-1 flex-col gap-2 p-4">
        <h3 className="text-base font-semibold text-ink-900">Sample apartment</h3>
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
        <p className="mt-auto border-t border-line pt-2 text-sm text-ink-700">
          Landlord name shown here
        </p>
      </div>
    </article>
  )
}

/** A sample of the two-sided review: who rated whom, stars, a line, and the green tick. */
function SampleReview({
  from,
  about,
  stars,
  text,
}: {
  from: "Tenant" | "Landlord"
  about: string
  stars: number
  text: string
}) {
  return (
    <article className="flex flex-col gap-2.5 rounded-xl border border-line bg-surface p-4 shadow-sm">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant={from === "Tenant" ? "tenant" : "landlord"}>{from}</Badge>
        <span className="text-sm text-ink-700">rates {about}</span>
        <SampleBadge />
      </div>
      <span className="inline-flex items-center gap-1.5 text-sm">
        <Stars value={stars} />
        <span className="font-semibold text-ink-900">{stars}.0</span>
      </span>
      <p className="text-sm text-ink-700">{text}</p>
      <GreenTick />
    </article>
  )
}

/**
 * "A look inside": up to three published homes from `GET /properties` (top rated; the seed
 * marks its rows "Demo data"), else sample cards marked "Sample". Two sample reviews show the
 * two sides. No count or number here is made up (D-03).
 */
export function LandingPreview() {
  const homes = usePropertySearch({ sort: "top_rated", size: PREVIEW_SIZE })
  const cities = useCities()
  const live = homes.data?.items.slice(0, PREVIEW_SIZE) ?? []
  const cityNames = cities.data?.map((c) => c.name) ?? []

  return (
    <div className="flex flex-col gap-6">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-busy={homes.isPending}>
        {homes.isPending
          ? Array.from({ length: PREVIEW_SIZE }, (_, i) => (
              <div key={i} className="rx-placeholder h-64 rounded-xl border border-line" />
            ))
          : live.length > 0
            ? live.map((property) => <HomeCard key={property.id} property={property} />)
            : Array.from({ length: PREVIEW_SIZE }, (_, i) => (
                <SampleHomeCard
                  key={i}
                  city={cityNames.length ? cityNames[i % cityNames.length] : undefined}
                />
              ))}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <SampleReview
          from="Tenant"
          about="the landlord"
          stars={4}
          text="Returned the deposit on time and fixed the geyser within a week."
        />
        <SampleReview
          from="Landlord"
          about="the tenant"
          stars={5}
          text="Paid rent on the 1st every month and left the flat clean."
        />
      </div>
    </div>
  )
}
