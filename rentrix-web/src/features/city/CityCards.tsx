import { useMemo, useState } from "react"
import { Link } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { ChevronRightIcon, CityIcon } from "@/components/ui/icons.gen"
import { ErrorState } from "@/components/ui/states"
import { cn } from "@/lib/utils"
import { CITY_CARDS_VISIBLE, plural, sortForCards, statsByCity } from "./cityText"
import { useCities, useStats } from "./useCities"

/**
 * Home's city cards: one per active city from `GET /cities`, with real counts from `GET /stats`
 * (D-03, D-85). Two wide cards for two cities, a 4-column grid beyond; the first 8 show, then
 * "Show all N cities". A city with no published property says so instead of a number.
 */
export function CityCards() {
  const cities = useCities()
  const stats = useStats()
  const [showAll, setShowAll] = useState(false)

  const counts = useMemo(
    () => (stats.isSuccess ? statsByCity(stats.data.cities) : null),
    [stats.isSuccess, stats.data],
  )
  const sorted = useMemo(
    () => sortForCards(cities.data ?? [], counts ?? new Map()),
    [cities.data, counts],
  )

  if (cities.isPending) return <p className="text-sm text-ink-500">Loading cities…</p>
  if (cities.isError) {
    return (
      <ErrorState
        title="Cities could not be loaded"
        error={cities.error}
        onRetry={() => void cities.refetch()}
      />
    )
  }
  if (sorted.length === 0) return null

  const visible = showAll ? sorted : sorted.slice(0, CITY_CARDS_VISIBLE)
  const few = sorted.length <= 2

  return (
    <div className="space-y-4">
      <ul
        className={cn(
          "grid gap-4",
          few ? "sm:grid-cols-2" : "grid-cols-1 sm:grid-cols-2 lg:grid-cols-4",
        )}
      >
        {visible.map((city) => {
          const c = counts?.get(city.code)
          const detail = [...city.aliases, city.state].filter(Boolean).join(" · ")
          return (
            <li key={city.code}>
              <Link
                to={`/search?city=${encodeURIComponent(city.code)}`}
                className={cn(
                  "flex h-full gap-3.5 rounded-xl border border-line bg-surface p-5 text-ink-900 no-underline shadow-sm transition-colors hover:border-teal-600 hover:no-underline",
                  few ? "items-center" : "flex-col items-start",
                )}
              >
                <span
                  className={cn(
                    "grid shrink-0 place-items-center",
                    few ? "size-13 rounded-[14px]" : "size-10 rounded-[10px]",
                    !counts || (c && c.publishedProperties > 0)
                      ? "bg-teal-50 text-teal-700"
                      : "bg-sunk text-ink-500",
                  )}
                >
                  <CityIcon size={few ? 24 : 20} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="text-lg font-semibold">{city.name}</span>{" "}
                  <span className="text-sm text-ink-500">{detail}</span>
                  {counts && (
                    <span className="block text-sm text-ink-700">
                      {c && c.publishedProperties > 0
                        ? `${plural(c.publishedProperties, "property", "properties")} · ${plural(
                            c.confirmedTenancies,
                            "confirmed tenancy",
                            "confirmed tenancies",
                          )}`
                        : "No properties yet"}
                    </span>
                  )}
                </span>
                {few && <ChevronRightIcon size={18} className="text-teal-700" />}
              </Link>
            </li>
          )
        })}
      </ul>
      {sorted.length > CITY_CARDS_VISIBLE && !showAll && (
        <Button variant="outline" size="lg" onClick={() => setShowAll(true)}>
          Show all {sorted.length} cities
        </Button>
      )}
    </div>
  )
}
