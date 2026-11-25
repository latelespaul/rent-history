import type { PropertySearch } from "@/api/properties"
import { cn } from "@/lib/utils"
import { HOME_SIZES } from "./labels"
import {
  RATING_OPTIONS,
  TYPE_OPTIONS,
  activeFilterCount,
  type SearchFilters as Changes,
} from "./searchParams"

interface Props {
  search: PropertySearch
  onChange: (changes: Changes) => void
  className?: string
}

export function Chip({
  pressed,
  onClick,
  children,
}: {
  pressed: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      onClick={onClick}
      className={cn(
        "h-9 rounded-full border px-3.5 text-sm transition-colors",
        pressed
          ? "border-teal-600 bg-teal-50 font-semibold text-teal-800"
          : "border-line-strong bg-surface text-ink-700 hover:border-teal-600",
      )}
    >
      {children}
    </button>
  )
}

/**
 * The filter panel (mockup `search.html`): type, home size and minimum rating, each written to the
 * URL by the page. Area and society filters arrive with B15/B16 (D-112).
 */
export function SearchFilters({ search, onChange, className }: Props) {
  const count = activeFilterCount(search)
  return (
    <section
      aria-labelledby="filters-heading"
      className={cn("space-y-5 rounded-xl border border-line bg-surface p-5 shadow-sm", className)}
    >
      <div className="flex items-center justify-between">
        <h2 id="filters-heading" className="text-lg font-semibold">
          Filters
        </h2>
        {count > 0 && (
          <button
            type="button"
            className="text-sm font-semibold text-teal-700 hover:underline"
            onClick={() =>
              onChange({
                locality: undefined,
                society: undefined,
                q: undefined,
                type: undefined,
                homeSize: undefined,
                bedrooms: undefined,
                minRating: undefined,
              })
            }
          >
            Clear
          </button>
        )}
      </div>

      <fieldset className="space-y-2">
        <legend className="mb-2 text-sm font-semibold text-ink-900">Type</legend>
        <div className="flex flex-wrap gap-2">
          <Chip pressed={!search.type} onClick={() => onChange({ type: undefined })}>
            Any
          </Chip>
          {TYPE_OPTIONS.map((t) => (
            <Chip
              key={t.value}
              pressed={search.type === t.value}
              onClick={() => onChange({ type: t.value })}
            >
              {t.label}
            </Chip>
          ))}
        </div>
      </fieldset>

      <fieldset className="space-y-2">
        <legend className="mb-2 text-sm font-semibold text-ink-900">Home size</legend>
        <div className="flex flex-wrap gap-2">
          <Chip
            pressed={search.homeSize === undefined && search.bedrooms === undefined}
            onClick={() => onChange({ homeSize: undefined, bedrooms: undefined })}
          >
            Any
          </Chip>
          {HOME_SIZES.filter((s) => s.value !== "OTHER").map((s) => (
            <Chip
              key={s.value}
              pressed={search.homeSize === s.value}
              onClick={() => onChange({ homeSize: s.value, bedrooms: undefined })}
            >
              {s.label}
            </Chip>
          ))}
        </div>
      </fieldset>

      <div className="space-y-1.5">
        <label htmlFor="min-rating" className="block text-sm font-semibold text-ink-900">
          Property rating
        </label>
        <select
          id="min-rating"
          value={search.minRating ?? ""}
          onChange={(event) => onChange({ minRating: event.target.value || undefined })}
          className="h-11 w-full rounded-md border border-line-strong bg-surface px-3 text-sm"
        >
          <option value="">Any (includes New)</option>
          {RATING_OPTIONS.map((r) => (
            <option key={r} value={r}>
              {r.toFixed(1)} and up
            </option>
          ))}
        </select>
        <p className="text-xs text-ink-500">
          Homes with fewer than 3 reviews have no average yet, so a minimum rating leaves them out.
        </p>
      </div>
    </section>
  )
}
