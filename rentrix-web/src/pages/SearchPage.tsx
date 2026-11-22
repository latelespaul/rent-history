import { useMemo } from "react"
import { Link, useSearchParams } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { Notice } from "@/components/ui/notice"
import { useCities } from "@/features/city/useCities"
import { Pagination } from "@/features/property/Pagination"
import { PropertyResult } from "@/features/property/PropertyResult"
import { SearchBar } from "@/features/property/SearchBar"
import { SearchFilters } from "@/features/property/SearchFilters"
import {
  DEFAULT_SORT,
  SORTS,
  activeFilterCount,
  searchFromParams,
  withFilters,
  type SearchFilters as Changes,
} from "@/features/property/searchParams"
import { usePropertySearch } from "@/features/property/useProperties"

/**
 * Search results (DESIGN.md screen 2): every filter lives in the URL, so `GET /properties` is
 * called with exactly what the address bar shows. Cities come only from `GET /cities`.
 */
export default function SearchPage() {
  const [params, setParams] = useSearchParams()
  const search = useMemo(() => searchFromParams(params), [params])
  const results = usePropertySearch(search)
  const cities = useCities()
  const cityName = cities.data?.find((c) => c.code === search.city)?.name

  const change = (changes: Changes) => setParams((prev) => withFilters(prev, changes))
  const page = (search.page ?? 0) + 1
  const data = results.data
  const total = data?.page.totalItems ?? 0
  const filtered = activeFilterCount(search) > 0

  return (
    <main className="mx-auto max-w-page space-y-8 px-4 py-8 nav:px-6">
      <h1 className="sr-only">Search homes</h1>
      <SearchBar
        city={search.city ?? null}
        locality={search.locality ?? ""}
        onCityChange={(code) => change({ city: code })}
        onSubmit={({ city, locality }) => change({ city: city ?? undefined, locality })}
      />

      <div className="grid gap-6 lg:grid-cols-[17rem_1fr]">
        <SearchFilters search={search} onChange={change} className="self-start" />

        <section
          aria-labelledby="results-heading"
          aria-busy={results.isFetching}
          className="space-y-4"
        >
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 id="results-heading" className="text-2xl font-semibold">
                {data
                  ? `${total} ${total === 1 ? "property" : "properties"}${cityName ? ` in ${cityName}` : ""}`
                  : "Searching…"}
              </h2>
              <p className="text-sm text-ink-500">
                {cityName ? "Published homes only." : "Published homes in every live city."}
                {search.locality && ` Locality starts with “${search.locality}”.`}
              </p>
            </div>
            <label className="flex items-center gap-2 text-sm text-ink-700">
              Sort
              <select
                value={search.sort ?? DEFAULT_SORT}
                onChange={(event) => change({ sort: event.target.value })}
                className="h-10 rounded-md border border-line-strong bg-surface px-3 text-sm"
              >
                {SORTS.map((s) => (
                  <option key={s.value} value={s.value}>
                    {s.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {results.isError && !data ? (
            <ErrorState
              title="Search failed"
              error={results.error}
              onRetry={() => void results.refetch()}
            />
          ) : results.isPending ? (
            <p className="text-sm text-ink-500">Loading homes…</p>
          ) : data && data.items.length === 0 ? (
            <EmptyState
              title="No homes match"
              description={
                filtered ? "Try fewer filters or another locality." : "No published homes here yet."
              }
              action={
                <Button asChild variant="outline">
                  <Link to="/homes/new" data-testid="empty-add-home">
                    Can&rsquo;t find your home? Add it
                  </Link>
                </Button>
              }
            />
          ) : (
            <ul className="space-y-4">
              {data?.items.map((property) => (
                <li key={property.id}>
                  <PropertyResult property={property} />
                </li>
              ))}
            </ul>
          )}

          {data && data.items.length > 0 && (
            <p className="text-sm text-ink-700">
              Can&rsquo;t find your home?{" "}
              <Link to="/homes/new" data-testid="search-add-home">
                Add it
              </Link>
            </p>
          )}

          {data && (
            <Pagination
              page={page}
              totalPages={data.page.totalPages}
              onPage={(next) => change({ page: next })}
            />
          )}

          <Notice tone="teal">
            Ratings show an average only after 3 reviews, so one review cannot define a home or a
            person. Anyone can see ratings and facts; review text and rent history need sign-in.
          </Notice>
        </section>
      </div>
    </main>
  )
}
