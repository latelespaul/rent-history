import { useState, type FormEvent } from "react"
import { Link } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { BuildingIcon, MapPinIcon, SearchIcon } from "@/components/ui/icons.gen"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { CityPicker } from "@/features/city/CityPicker"
import { homeTitle, placeLine } from "@/features/property/labels"
import { usePropertySearch } from "@/features/property/useProperties"
import { useSessionDraft } from "@/lib/draft"

/** How many homes one pick search lists; the words narrow it further. */
const PICK_SIZE = 10

/**
 * "Which home did you rent?", the first step of recording a tenancy when no home is chosen yet
 * (`/tenancies/new` without `?property=`). A city and an area, society or building name search
 * `GET /properties` like the search page (`q`, D-162); picking a result opens the tenancy form
 * for it (`to(id)`). A home that is not listed goes to "Add your home", which records the tenancy
 * with it. The city and words stay in the tab, so Back from the form shows the same results.
 */
export function PickHome({ to }: { to: (propertyId: string) => string }) {
  const [draft, setDraft] = useSessionDraft<{ city: string | null; q: string; asked: boolean }>(
    "tenancy:pick",
    { city: null, q: "", asked: false },
  )
  const [text, setText] = useState(draft.q)
  const results = usePropertySearch(
    { city: draft.city ?? undefined, q: draft.q || undefined, size: PICK_SIZE },
    draft.asked,
  )

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setDraft((current) => ({ ...current, q: text.trim(), asked: true }))
  }

  const homes = results.data?.items ?? []

  return (
    <section aria-labelledby="pick-home-heading" className="space-y-5" data-testid="pick-home">
      <div>
        <p className="text-xs font-semibold tracking-wide text-teal-700 uppercase">
          Record your tenancy
        </p>
        <h1
          id="pick-home-heading"
          className="mt-1 font-display text-3xl font-semibold text-ink-900"
        >
          Which home did you rent?
        </h1>
        <p className="mt-1 text-ink-500">
          Find it by its area, society or building, then pick it. The landlord confirms the tenancy
          next.
        </p>
      </div>

      <form
        role="search"
        aria-label="Find your home"
        onSubmit={submit}
        className="space-y-3 rounded-xl border border-line bg-surface p-4 shadow-sm sm:p-5"
      >
        <CityPicker
          label="City"
          placeholder="Any city"
          value={draft.city}
          onChange={(city) => setDraft((current) => ({ ...current, city, asked: true }))}
          className="rounded-lg border border-line-strong"
        />
        <div className="space-y-1.5">
          <Label htmlFor="pick-q">Area, society or building</Label>
          <Input
            id="pick-q"
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder="e.g. HSR Layout, Sobha, 560102"
            autoComplete="off"
            className="h-10"
          />
        </div>
        <Button type="submit" className="w-full sm:w-auto">
          <SearchIcon />
          Search
        </Button>
      </form>

      {draft.asked && (
        <div aria-live="polite" aria-busy={results.isFetching} className="space-y-3">
          {results.isError && !results.data ? (
            <ErrorState
              title="Search failed"
              error={results.error}
              onRetry={() => void results.refetch()}
            />
          ) : results.isPending ? (
            <p className="text-sm text-ink-500">Looking for homes…</p>
          ) : homes.length === 0 ? (
            <EmptyState
              icon="home"
              title="No home matches"
              description="Try another area or society name, or add the home: your tenancy is recorded with it."
            />
          ) : (
            <ul className="space-y-2" aria-label="Homes found">
              {homes.map((home) => (
                <li
                  key={home.id}
                  data-testid="pick-home-result"
                  className="flex flex-col gap-3 rounded-xl border border-line bg-surface p-4 shadow-sm sm:flex-row sm:items-center"
                >
                  <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-sunk text-ink-500 max-sm:hidden">
                    <BuildingIcon size={20} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-ink-900">{homeTitle(home)}</p>
                    <p className="inline-flex items-center gap-1 text-sm text-ink-700">
                      <MapPinIcon size={14} className="text-ink-500" />
                      {placeLine(home)}
                    </p>
                  </div>
                  <Button asChild variant="outline" size="sm" className="shrink-0">
                    <Link to={to(home.id)} aria-label={`Pick ${homeTitle(home)}`}>
                      This is my home
                    </Link>
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <p className="rounded-xl border border-line bg-sunk p-4 text-sm text-ink-700">
        Can&rsquo;t find it?{" "}
        <Link to="/homes/new" data-testid="pick-add-home" className="font-semibold underline">
          Add your home
        </Link>{" "}
        &mdash; your tenancy is recorded with it.
      </p>
    </section>
  )
}
