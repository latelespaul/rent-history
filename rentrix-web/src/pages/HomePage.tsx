import { useState, type FormEvent } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import { MapPin, Search } from "lucide-react"
import { Button } from "@/components/ui/button"
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
    <main className="mx-auto max-w-6xl space-y-14 px-4 py-16">
      <section className="space-y-6">
        <div className="max-w-3xl space-y-3">
          <p className="text-xs font-semibold tracking-widest text-muted-foreground uppercase">
            Renting, with receipts
          </p>
          <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">
            Check the landlord, the tenant and the flat before you sign.
          </h1>
          <p className="text-lg text-muted-foreground">
            Every rating on Rentrix comes from a tenancy that both sides confirmed.
          </p>
        </div>

        <form
          role="search"
          onSubmit={onSearch}
          className="flex max-w-3xl flex-col gap-2 rounded-xl border bg-card p-2 shadow-sm sm:flex-row sm:items-center"
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
            <MapPin className="size-4 shrink-0 text-muted-foreground" aria-hidden />
            <span className="min-w-0 flex-1">
              <span className="block text-xs text-muted-foreground">Locality or building</span>
              <input
                value={locality}
                onChange={(event) => setLocality(event.target.value)}
                placeholder="Type a locality"
                className="w-full bg-transparent outline-none placeholder:text-muted-foreground"
              />
            </span>
          </label>
          <Button type="submit" size="lg" className="h-12 px-5">
            <Search aria-hidden />
            Search
          </Button>
        </form>
        <LaunchCopy className="block text-sm text-muted-foreground" />
      </section>

      <section aria-labelledby="cities-heading" className="space-y-4">
        <h2 id="cities-heading" className="text-2xl font-semibold tracking-tight">
          Pick a city
        </h2>
        <CityCards />
      </section>
    </main>
  )
}
