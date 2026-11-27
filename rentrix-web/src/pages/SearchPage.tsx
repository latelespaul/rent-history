import { useMemo } from "react"
import { useQuery } from "@tanstack/react-query"
import { Link, useSearchParams } from "react-router-dom"
import { fetchSociety, locationKeys } from "@/api/locations"
import { Button } from "@/components/ui/button"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { Notice } from "@/components/ui/notice"
import { useCities } from "@/features/city/useCities"
import { Pagination } from "@/features/property/Pagination"
import { PropertyResult } from "@/features/property/PropertyResult"
import { SocietyResult } from "@/features/property/SocietyResult"
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
import { useGroupedSearch, useSignedIn } from "@/features/property/useProperties"

/**
 * Search results (DESIGN.md screen 2): every filter lives in the URL, so `GET /properties/grouped`
 * is called with exactly what the address bar shows. A society with 2+ matching homes is one card
 * that opens its page (D-166); every other home is its own card. Cities come only from `GET /cities`.
 */
export default function SearchPage() {
  const [params, setParams] = useSearchParams()
  const search = useMemo(() => searchFromParams(params), [params])
  const results = useGroupedSearch(search)
  const cities = useCities()
  const cityName = cities.data?.find((c) => c.code === search.city)?.name
  const { ready, viewer } = useSignedIn()
  // the society a two-step pick narrowed the search to (D-167): its name for the chip
  const society = useQuery({
    queryKey: locationKeys.society(search.society ?? "", viewer),
    queryFn: ({ signal }) => fetchSociety(search.society!, signal),
    enabled: ready && Boolean(search.society),
    retry: false,
  })
  const pickedSociety =
    search.society && society.data ? { id: search.society, name: society.data.name } : null

  // the lists are re-written from what the page understood, so an old `type=STUDIO` link becomes the Studio
  // size and a chip switched off stays off
  const change = (changes: Changes) =>
    setParams((prev) => {
      const { type, homeSize } = searchFromParams(prev)
      return withFilters(prev, { type, homeSize, ...changes })
    })
  const page = (search.page ?? 0) + 1
  const data = results.data
  const total = data?.totalHomes ?? 0
  const filtered = activeFilterCount(search) > 0

  return (
    <main className="mx-auto max-w-page space-y-8 px-4 py-8 nav:px-6">
      <h1 className="sr-only">Search homes</h1>
      <SearchBar
        city={search.city ?? null}
        query={search.q ?? search.locality ?? ""}
        society={pickedSociety}
        onCityChange={(code) => change({ city: code, society: undefined })}
        onSubmit={({ city, query, society }) =>
          change({ city: city ?? undefined, q: query, society, locality: undefined })
        }
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
                {cityName ? "Every home added, verified or not." : "Homes in every live city."}
                {search.locality && ` Locality starts with “${search.locality}”.`}
                {pickedSociety && ` In ${pickedSociety.name}.`}
                {search.q && ` Matching “${search.q}”.`}
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
              {data?.items.map((item) =>
                item.kind === "SOCIETY" ? (
                  <li key={`s-${item.society.id}`}>
                    <SocietyResult
                      society={item.society}
                      homeSize={search.homeSize?.length === 1 ? search.homeSize[0] : undefined}
                    />
                  </li>
                ) : (
                  <li key={item.home.id}>
                    <PropertyResult property={item.home} />
                  </li>
                ),
              )}
            </ul>
          )}

          {data && data.items.length > 0 && (
            <p className="text-sm text-ink-700">
              Can&rsquo;t find your home?{" "}
              <Link
                to="/homes/new"
                data-testid="search-add-home"
                className="font-semibold underline"
              >
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
            person. Anyone can read a home&rsquo;s reviews; rent history needs sign-in. A home
            without the green tick shows &ldquo;Not verified yet&rdquo;.
          </Notice>
        </section>
      </div>
    </main>
  )
}
