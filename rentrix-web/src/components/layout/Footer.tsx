import { Link } from "react-router-dom"
import { launchCopy } from "@/features/city/cityText"
import { useCities } from "@/features/city/useCities"
import { BRAND } from "@/lib/brand"
import { Container } from "./Container"

/** Site footer. The launch line comes from the city list, never from a typed city name (D-85). */
export function Footer() {
  const cities = useCities()
  return (
    <footer className="border-t border-line py-6 text-sm text-ink-500">
      <Container className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2">
        <p>
          {BRAND.name}
          {cities.isSuccess && ` · ${launchCopy(cities.data)}`}
        </p>
        <p className="flex items-center gap-4">
          <span>{BRAND.tagline}</span>
          <Link to="/search">Search</Link>
        </p>
      </Container>
    </footer>
  )
}
