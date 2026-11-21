import { useQuery } from "@tanstack/react-query"
import { MapPin } from "lucide-react"
import { cityKeys, fetchCities } from "@/api/cities"

/**
 * Placeholder until the new design system (W2) and Home (W4) land. It shows only data from
 * the API (D-03): no stats, testimonials or city counts are written into the page.
 */
export default function HomePage() {
  const cities = useQuery({
    queryKey: cityKeys.all,
    queryFn: ({ signal }) => fetchCities(signal),
  })

  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-6 px-4 py-16">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight">Rentrix</h1>
        <p className="text-muted-foreground">
          Ratings for landlords, tenants and properties, tied to real tenancies. The new app is
          being built.
        </p>
      </div>

      <section aria-labelledby="cities-heading" className="space-y-2">
        <h2 id="cities-heading" className="text-sm font-medium">
          Live cities
        </h2>
        {cities.isPending && <p className="text-sm text-muted-foreground">Loading…</p>}
        {cities.isError && (
          <p className="text-sm text-destructive">Could not load cities from the API.</p>
        )}
        {cities.isSuccess && (
          <ul className="flex flex-wrap gap-2">
            {cities.data.map((city) => (
              <li
                key={city.code}
                className="inline-flex items-center gap-1 rounded-md border px-3 py-1 text-sm"
              >
                <MapPin className="h-4 w-4" aria-hidden />
                {city.name}
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}
