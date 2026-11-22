import { useSearchParams } from "react-router-dom"
import { CityPicker } from "@/features/city/CityPicker"

/**
 * Search until W4 adds filters and results: the city picker, bound to `?city=` (a city code,
 * D-82). Free text never reaches the property search; only the chosen code does.
 */
export default function SearchPage() {
  const [params, setParams] = useSearchParams()

  return (
    <main className="mx-auto max-w-page space-y-6 px-4 py-12 nav:px-6">
      <h1 className="font-display text-3xl font-semibold">Search</h1>
      <CityPicker
        label="City"
        value={params.get("city")}
        onChange={(code) =>
          setParams((prev) => {
            const next = new URLSearchParams(prev)
            next.set("city", code)
            return next
          })
        }
        className="max-w-sm"
      />
      <p className="text-sm text-ink-500">Search results and filters are being built.</p>
    </main>
  )
}
