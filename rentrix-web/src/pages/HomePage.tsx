import { useState, type FormEvent } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { MapPinIcon, SearchIcon } from "@/components/ui/icons.gen"
import { CityCards } from "@/features/city/CityCards"
import { CityPicker } from "@/features/city/CityPicker"
import { LaunchCopy } from "@/features/city/LaunchCopy"

/**
 * Home until W4: the search entry with the city picker, the launch line and one card per
 * active city. Every city and number comes from the API (D-03, D-85); none is written here.
 */
export default function HomePage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const [locality, setLocality] = useState("")
  const city = params.get("city")

  function onSearch(event: FormEvent) {
    event.preventDefault()
    const next = new URLSearchParams()
    if (city) next.set("city", city)
    if (locality.trim()) next.set("locality", locality.trim())
    navigate({ pathname: "/search", search: next.toString() })
  }

  return (
    <main className="mx-auto max-w-page space-y-14 px-4 py-16 nav:px-6">
      <section className="space-y-6">
        <div className="max-w-3xl space-y-3">
          <p className="text-xs font-semibold tracking-[0.08em] text-teal-700 uppercase">
            Renting, with receipts
          </p>
          <h1 className="font-display text-3xl font-semibold sm:text-[2.5rem]">
            Check the landlord, the tenant and the flat before you sign.
          </h1>
          <p className="text-lg text-ink-700">
            Every rating on Rentrix comes from a tenancy that both sides confirmed.
          </p>
        </div>

        <form
          role="search"
          onSubmit={onSearch}
          className="flex max-w-3xl flex-col gap-2 rounded-xl border border-line-strong bg-surface p-2 shadow-md sm:flex-row sm:items-center"
        >
          <CityPicker
            label="City"
            value={city}
            onChange={(code) =>
              setParams(
                (prev) => {
                  const next = new URLSearchParams(prev)
                  next.set("city", code)
                  return next
                },
                { replace: true },
              )
            }
            className="sm:w-72"
          />
          <label className="flex min-h-14 flex-1 items-center gap-3 rounded-lg px-3">
            <MapPinIcon size={18} className="text-ink-500" />
            <span className="min-w-0 flex-1">
              <span className="block text-xs text-ink-500">Locality or building</span>
              <input
                value={locality}
                onChange={(event) => setLocality(event.target.value)}
                placeholder="Type a locality"
                className="w-full bg-transparent outline-none placeholder:text-ink-500"
              />
            </span>
          </label>
          <Button type="submit" size="lg">
            <SearchIcon />
            Search
          </Button>
        </form>
        <LaunchCopy className="block text-sm text-ink-500" />
      </section>

      <section aria-labelledby="cities-heading" className="space-y-4">
        <h2 id="cities-heading" className="text-2xl font-semibold">
          Pick a city
        </h2>
        <CityCards />
      </section>
    </main>
  )
}
