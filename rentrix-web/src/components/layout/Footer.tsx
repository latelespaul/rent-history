import { launchCopy } from "@/features/city/cityText"
import { useCities } from "@/features/city/useCities"

/** Site footer. The launch line comes from the city list, never from a typed city name (D-85). */
export function Footer() {
  const cities = useCities()
  return (
    <footer className="border-t">
      <p className="mx-auto max-w-6xl px-4 py-6 text-sm text-muted-foreground">
        Rentrix{cities.isSuccess && ` · ${launchCopy(cities.data)}`}
      </p>
    </footer>
  )
}
