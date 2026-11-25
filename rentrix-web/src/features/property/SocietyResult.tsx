import { Link } from "react-router-dom"
import { BuildingIcon, ChevronRightIcon, MapPinIcon } from "@/components/ui/icons.gen"
import type { SocietyGroup } from "@/types/api"
import { flatCount, rentRangeLabel } from "./society"
import { LocationPendingBadge } from "./LocationPendingBadge"
import { CardReviews } from "./Rating"

/**
 * One society in the search results when 2+ of its homes match (D-166): name, area and city, how
 * many flats match, their reviews and verified average, and (signed in) the rent range. The whole
 * card opens the society page `/s/:id`, which lists its flats by tower.
 */
export function SocietyResult({ society }: { society: SocietyGroup }) {
  return (
    <article
      data-testid="society-result"
      className="relative flex flex-col overflow-hidden rounded-xl border border-line bg-surface shadow-sm transition-colors focus-within:border-teal-600 hover:border-teal-600 sm:flex-row"
    >
      <div className="rx-placeholder relative grid h-28 shrink-0 place-items-center sm:h-auto sm:w-56">
        <BuildingIcon size={36} className="text-ink-300" />
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-2.5 p-4 sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1">
          <h3 className="text-lg font-semibold" data-testid="society-title">
            <Link
              to={`/s/${encodeURIComponent(society.id)}`}
              className="text-ink-900 no-underline after:absolute after:inset-0 hover:no-underline"
            >
              {society.name}
            </Link>
          </h3>
          <CardReviews rating={society.rating} listed={society.listedReviews} />
        </div>
        <p className="inline-flex flex-wrap items-center gap-1.5 text-sm text-ink-700">
          <MapPinIcon size={16} className="text-ink-500" />
          {`${society.area.name}, ${society.city.name}`}
          {society.pending && <LocationPendingBadge />}
        </p>
        <p className="text-sm text-ink-700">
          <span className="font-semibold text-ink-900" data-testid="society-flats">
            {flatCount(society.flats)}
          </span>
          {society.rentRange && (
            <>
              {" · "}
              <span data-testid="society-rent">{rentRangeLabel(society.rentRange)}</span>
            </>
          )}
        </p>
        <p className="mt-auto flex items-center justify-between border-t border-line pt-2.5 text-sm font-semibold text-teal-700">
          See the flats by tower
          <ChevronRightIcon size={18} />
        </p>
      </div>
    </article>
  )
}
