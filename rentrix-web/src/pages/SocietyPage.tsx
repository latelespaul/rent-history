import { useQuery } from "@tanstack/react-query"
import { Link, useParams, useSearchParams } from "react-router-dom"
import { isApiError } from "@/api/errors"
import { fetchSociety, fetchSocietySummary, locationKeys } from "@/api/locations"
import { Button } from "@/components/ui/button"
import { MapPinIcon } from "@/components/ui/icons.gen"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { HOME_SIZES } from "@/features/property/labels"
import { LocationPendingBadge } from "@/features/property/LocationPendingBadge"
import { PropertyResult } from "@/features/property/PropertyResult"
import { CardReviews } from "@/features/property/Rating"
import { Chip } from "@/features/property/SearchFilters"
import { flatCount, flatsByTower, rentRangeLabel } from "@/features/property/society"
import { usePropertySearch, useSignedIn } from "@/features/property/useProperties"
import { BRAND } from "@/lib/brand"

/** How many of the society's flats the page lists (the API's largest page, D-59). */
const FLATS_PAGE = 50

/** A map search for the society: there are no stored coordinates, so the pin is a search. */
function mapLink(name: string, area: string, city: string): string {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${name}, ${area}, ${city}`)}`
}

/**
 * The society page `/s/:id` (captain 2026-11-25, D-166): header (name, area and city, map pin),
 * the society's flats, reviews and verified average (and, signed in, the rent range), tower and
 * BHK filters in the URL, and the flats grouped by tower, each opening its home page.
 */
export default function SocietyPage() {
  const { id = "" } = useParams()
  const [params, setParams] = useSearchParams()
  const { ready, viewer } = useSignedIn()
  const tower = params.get("tower") ?? undefined
  const homeSize = params.get("homeSize") ?? undefined

  const society = useQuery({
    queryKey: locationKeys.society(id, viewer),
    queryFn: ({ signal }) => fetchSociety(id, signal),
    enabled: ready && Boolean(id),
    retry: false,
  })
  const summary = useQuery({
    queryKey: locationKeys.summary(id, viewer),
    queryFn: ({ signal }) => fetchSocietySummary(id, signal),
    enabled: ready && Boolean(id),
    retry: false,
  })
  const flats = usePropertySearch({
    society: id,
    tower,
    homeSize,
    size: FLATS_PAGE,
    sort: "newest",
  })

  function filter(name: "tower" | "homeSize", value: string | undefined) {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      if (value) next.set(name, value)
      else next.delete(name)
      return next
    })
  }

  if (society.isPending) {
    return (
      <main className="mx-auto max-w-page px-4 py-12 nav:px-6">
        <p className="text-sm text-ink-500">Loading the society…</p>
      </main>
    )
  }
  if (society.isError) {
    const missing = isApiError(society.error) && society.error.status === 404
    return (
      <main className="mx-auto max-w-page px-4 py-12 nav:px-6">
        {missing ? (
          <EmptyState
            icon="home"
            title={`This society is not on ${BRAND.name}`}
            description="The link may be wrong."
            action={
              <Button asChild variant="outline">
                <Link to="/search">Search homes</Link>
              </Button>
            }
          />
        ) : (
          <ErrorState
            title="The society could not be loaded"
            error={society.error}
            onRetry={() => void society.refetch()}
          />
        )}
      </main>
    )
  }

  const s = society.data
  const towers = s.towers.filter((t) => t.publishedHomes > 0)
  const groups = flats.data ? flatsByTower(s.towers, flats.data.items) : []
  const sizes = HOME_SIZES.filter((size) => size.value !== "OTHER")

  return (
    <main className="mx-auto max-w-page space-y-6 px-4 py-8 nav:px-6">
      <header className="space-y-2">
        <p className="text-sm text-ink-500">
          <Link to={`/search?city=${encodeURIComponent(s.city.code)}`}>{s.city.name}</Link>
          {" › "}
          {s.area.name}
        </p>
        <h1 className="flex flex-wrap items-center gap-3 font-display text-3xl font-semibold">
          {s.name}
          {s.status === "PENDING" && <LocationPendingBadge />}
        </h1>
        <p className="inline-flex flex-wrap items-center gap-1.5 text-sm text-ink-700">
          <MapPinIcon size={16} className="text-ink-500" />
          {`${s.area.name}, ${s.city.name}`}
          {" · "}
          <a
            href={mapLink(s.name, s.area.name, s.city.name)}
            target="_blank"
            rel="noreferrer"
            data-testid="society-map"
            className="inline-flex min-h-11 items-center font-semibold"
          >
            View on map
          </a>
        </p>
      </header>

      <section
        aria-label="Society reviews"
        data-testid="society-summary"
        className="space-y-1.5 rounded-xl border border-line bg-surface p-4"
      >
        <h2 className="text-lg font-semibold">Reviews of its homes</h2>
        {summary.data ? (
          <>
            <CardReviews rating={summary.data.rating} listed={summary.data.listedReviews} />
            <p className="text-sm text-ink-700">
              {flatCount(summary.data.flats)} on {BRAND.name}
              {summary.data.rentRange && ` · Rent ${rentRangeLabel(summary.data.rentRange)}`}
            </p>
            <p className="text-xs text-ink-500">
              The average counts only reviews our team verified, from 3 reviews on.
            </p>
          </>
        ) : (
          <p className="text-sm text-ink-500">Loading…</p>
        )}
      </section>

      <section aria-label="Filters" className="space-y-3">
        {towers.length > 0 && (
          <div className="space-y-2">
            <h2 className="text-sm font-medium text-ink-900">Tower</h2>
            <div className="flex flex-wrap gap-2">
              <Chip pressed={!tower} onClick={() => filter("tower", undefined)}>
                All
              </Chip>
              {towers.map((t) => (
                <Chip key={t.id} pressed={tower === t.id} onClick={() => filter("tower", t.id)}>
                  {t.name}
                </Chip>
              ))}
            </div>
          </div>
        )}
        <div className="space-y-2">
          <h2 className="text-sm font-medium text-ink-900">Home size</h2>
          <div className="flex flex-wrap gap-2">
            <Chip pressed={!homeSize} onClick={() => filter("homeSize", undefined)}>
              Any
            </Chip>
            {sizes.map((size) => (
              <Chip
                key={size.value}
                pressed={homeSize === size.value}
                onClick={() => filter("homeSize", size.value)}
              >
                {size.label}
              </Chip>
            ))}
          </div>
        </div>
      </section>

      <section aria-labelledby="flats-heading" aria-busy={flats.isFetching} className="space-y-5">
        <h2 id="flats-heading" className="text-2xl font-semibold">
          {flats.data ? flatCount(flats.data.page.totalItems) : "Flats"}
        </h2>
        {flats.isError ? (
          <ErrorState
            title="The flats could not be loaded"
            error={flats.error}
            onRetry={() => void flats.refetch()}
          />
        ) : flats.isPending ? (
          <p className="text-sm text-ink-500">Loading flats…</p>
        ) : groups.length === 0 ? (
          <EmptyState
            title="No flats match"
            description="Try another tower or home size."
            action={
              <Button asChild variant="outline">
                <Link to="/homes/new">Add your home here</Link>
              </Button>
            }
          />
        ) : (
          groups.map((group) => (
            <section
              key={group.key}
              aria-label={group.name}
              data-testid="tower-group"
              className="space-y-3"
            >
              <h3 className="text-lg font-semibold">
                {group.name}{" "}
                <span className="text-sm font-normal text-ink-500">
                  · {flatCount(group.flats.length)}
                </span>
              </h3>
              <ul className="space-y-4">
                {group.flats.map((flat) => (
                  <li key={flat.id}>
                    <PropertyResult property={flat} />
                  </li>
                ))}
              </ul>
            </section>
          ))
        )}
      </section>
    </main>
  )
}
