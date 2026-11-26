import { Link } from "react-router-dom"
import { ArrowRightIcon } from "@/components/ui/icons.gen"
import { cn } from "@/lib/utils"
import type { City } from "@/types/api"

/** The 4 px top strip of each tile, in turn: teal, marigold (brief §3.7, a skyline colour). */
const STRIPS = ["border-t-teal-600", "border-t-marigold-500"]

/**
 * The live cities as one grid of tiles: one per active city from `GET /cities` (D-85), each a
 * link into `/search?city=`. Two columns on a phone, four from 1024 px; no locality is named here
 * (the hero search box covers locality, society and PIN). No city is written here.
 */
export function LandingCoverage({ cities: list }: { cities: readonly City[] }) {
  return (
    <ul className="mx-auto grid max-w-4xl grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
      {list.map((city, i) => (
        <li key={city.code}>
          <Link
            to={`/search?city=${encodeURIComponent(city.code)}`}
            className={cn(
              "group flex min-h-16 items-center justify-between gap-2 rounded-2xl border border-t-4 border-line bg-surface px-4 py-3 text-ink-900 no-underline transition-colors duration-200 hover:border-x-teal-600 hover:border-b-teal-600 hover:no-underline",
              STRIPS[i % STRIPS.length],
            )}
          >
            <span className="flex min-w-0 flex-col gap-0.5">
              <span className="truncate font-display text-lg font-semibold">{city.name}</span>
              <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-success-600">
                <span aria-hidden className="size-1.5 rounded-full bg-success-600" />
                Live
              </span>
            </span>
            <ArrowRightIcon size={18} className="shrink-0 text-teal-600" />
          </Link>
        </li>
      ))}
    </ul>
  )
}
