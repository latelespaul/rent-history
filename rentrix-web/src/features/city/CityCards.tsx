import { useMemo, useState } from "react"
import { Link } from "react-router-dom"
import { Building2 } from "lucide-react"
import { Button } from "@/components/ui/button"
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

  if (cities.isPending) return <p className="text-sm text-muted-foreground">Loading cities…</p>
  if (cities.isError) {
    return <p className="text-sm text-destructive">Could not load cities from the API.</p>
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
                className="flex h-full flex-col gap-3 rounded-xl border bg-card p-5 transition-colors hover:border-ring focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
              >
                <span className="flex size-10 items-center justify-center rounded-lg bg-muted">
                  <Building2 className="size-5 text-muted-foreground" aria-hidden />
                </span>
                <span>
                  <span className="text-lg font-semibold">{city.name}</span>{" "}
                  <span className="text-sm text-muted-foreground">{detail}</span>
                </span>
                {counts && (
                  <span className="text-sm text-muted-foreground">
                    {c && c.publishedProperties > 0
                      ? `${plural(c.publishedProperties, "property", "properties")} · ${plural(
                          c.confirmedTenancies,
                          "confirmed tenancy",
                          "confirmed tenancies",
                        )}`
                      : "No properties yet"}
                  </span>
                )}
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
