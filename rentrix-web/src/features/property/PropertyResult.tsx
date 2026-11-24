import { Link } from "react-router-dom"
import { Badge, DemoBadge } from "@/components/ui/badge"
import { BuildingIcon, MapPinIcon } from "@/components/ui/icons.gen"
import type { PropertyCard } from "@/types/api"
import { homeTitle, placeLine } from "./labels"
import { LocationPendingBadge } from "./LocationPendingBadge"
import { PropertyFacts } from "./PropertyFacts"
import { CardReviews } from "./Rating"

/**
 * One search result (mockup `search.html`): image placeholder, the title line "Society · Tower ·
 * Flat" (D-165), place, facts, the home's reviews and verified average, and the landlord's short
 * name. Public fields only (D-56); the whole card links to `/p/:id`.
 */
export function PropertyResult({ property }: { property: PropertyCard }) {
  const title = homeTitle(property)
  return (
    <article className="relative flex flex-col overflow-hidden rounded-xl border border-line bg-surface shadow-sm transition-colors focus-within:border-teal-600 hover:border-teal-600 sm:flex-row">
      <div className="rx-placeholder relative grid h-36 shrink-0 place-items-center sm:h-auto sm:w-56">
        <BuildingIcon size={36} className="text-ink-300" />
        {property.demo && <DemoBadge className="absolute top-3 left-3" />}
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-2.5 p-4 sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1">
          <h3 className="text-lg font-semibold" data-testid="card-title">
            <Link
              to={`/p/${encodeURIComponent(property.id)}`}
              className="text-ink-900 no-underline after:absolute after:inset-0 hover:no-underline"
            >
              {title}
            </Link>
          </h3>
          <CardReviews rating={property.rating} listed={property.listedReviews} />
        </div>
        <p className="inline-flex flex-wrap items-center gap-1.5 text-sm text-ink-700">
          <MapPinIcon size={16} className="text-ink-500" />
          {placeLine(property)}
          {/* Under the card's link, so a tap on the badge opens the home too (captain 2026-11-24 09:44). */}
          {property.locationPending && <LocationPendingBadge />}
          {property.verification !== undefined && property.verification !== "VERIFIED" && (
            <Badge data-testid="card-not-verified">Not verified yet</Badge>
          )}
        </p>
        <PropertyFacts property={property} />
        <p className="mt-auto border-t border-line pt-2.5 text-sm text-ink-700">
          <span className="font-semibold text-ink-900">{property.landlordName}</span> · landlord
        </p>
      </div>
    </article>
  )
}
