import { useQueries } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { Badge } from "@/components/ui/badge"
import { ArrowRightIcon, CheckIcon } from "@/components/ui/icons.gen"
import { searchAreas } from "@/api/locations"
import { cn } from "@/lib/utils"
import type { City } from "@/types/api"

/** How many localities each city card names (the first of `GET /areas`, public). */
export const COVERAGE_AREAS = 6

/** A skyline per card, in the card's tint; drawn here, no image to load. */
function Skyline({ tone, sun }: { tone: string; sun: string }) {
  return (
    <svg
      viewBox="0 0 560 120"
      preserveAspectRatio="xMidYMax slice"
      aria-hidden="true"
      className="absolute inset-0 size-full"
    >
      <g fill={tone}>
        <rect x="30" y="60" width="40" height="60" />
        <rect x="80" y="30" width="34" height="90" />
        <rect x="124" y="70" width="50" height="50" />
        <rect x="186" y="44" width="30" height="76" />
        <path d="M240 120V70l30-24 30 24v50z" />
        <rect x="316" y="20" width="36" height="100" />
        <rect x="362" y="56" width="48" height="64" />
        <rect x="420" y="38" width="30" height="82" />
        <rect x="460" y="66" width="70" height="54" />
      </g>
      <circle cx="500" cy="34" r="16" fill={sun} opacity=".25" />
    </svg>
  )
}

const TINTS = [
  { bg: "from-teal-50 to-teal-100", tone: "#9ccbc2", sun: "var(--rx-teal-600)" },
  { bg: "from-[#fdf4e1] to-marigold-100", tone: "#f1cf94", sun: "var(--rx-marigold-600)" },
]

/**
 * "Where we are live": one card per active city from `GET /cities` (D-85), each naming its
 * first localities from `GET /areas` as links into search. No city or locality is written here.
 */
export function LandingCoverage({ cities: list }: { cities: readonly City[] }) {
  const areas = useQueries({
    queries: list.map((city) => ({
      queryKey: ["landing", "areas", city.code],
      queryFn: ({ signal }: { signal: AbortSignal }) => searchAreas(city.code, "", signal),
      staleTime: 1000 * 60 * 10,
    })),
  })

  return (
    <div className="grid gap-4 md:grid-cols-2 md:gap-6">
      {list.map((city, i) => {
        const tint = TINTS[i % TINTS.length]
        const names = (areas[i]?.data ?? []).slice(0, COVERAGE_AREAS).map((a) => a.name)
        return (
          <article
            key={city.code}
            className="overflow-hidden rounded-[22px] border border-line bg-surface"
          >
            <div className={cn("relative h-28 bg-linear-180", tint.bg)}>
              <Skyline tone={tint.tone} sun={tint.sun} />
            </div>
            <div className="grid gap-3 px-5 pt-5 pb-6">
              <div className="flex items-baseline justify-between gap-2">
                <h3 className="font-display text-2xl font-semibold text-ink-900">{city.name}</h3>
                <Badge variant="success">
                  <CheckIcon />
                  Live
                </Badge>
              </div>
              {names.length > 0 && (
                <ul className="flex flex-wrap gap-2" aria-label={`Localities in ${city.name}`}>
                  {names.map((name) => (
                    <li key={name}>
                      <Link
                        to={`/search?city=${encodeURIComponent(city.code)}&locality=${encodeURIComponent(name)}`}
                        className="inline-flex min-h-9 items-center rounded-full border border-line bg-paper px-3 text-[13px] text-ink-700 no-underline transition-colors duration-200 hover:border-teal-600 hover:text-teal-700 hover:no-underline"
                      >
                        {name}
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
              <Link
                to={`/search?city=${encodeURIComponent(city.code)}`}
                className="inline-flex min-h-11 items-center gap-1.5 self-start text-[15px] font-semibold"
              >
                Browse homes in {city.name}
                <ArrowRightIcon size={18} />
              </Link>
            </div>
          </article>
        )
      })}
    </div>
  )
}
