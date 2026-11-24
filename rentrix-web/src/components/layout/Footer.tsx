import { Link } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { SearchIcon } from "@/components/ui/icons.gen"
import { launchCopy } from "@/features/city/cityText"
import { useCities } from "@/features/city/useCities"
import { BRAND } from "@/lib/brand"
import { Container } from "./Container"

/**
 * Site footer. The launch line comes from the city list, never from a typed city name (D-85).
 * "Search" is a teal pill with a 44 px tap target, so it is easy to see and tap at 390 px.
 */
export function Footer() {
  const cities = useCities()
  return (
    <footer className="border-t border-line py-6 text-sm text-ink-500">
      <Container className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2">
        <p>
          {BRAND.name}
          {cities.isSuccess && ` · ${launchCopy(cities.data)}`}
        </p>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
          <p>{BRAND.tagline}</p>
          <Button
            asChild
            variant="outline"
            data-testid="footer-search"
            className="h-11 rounded-full border-teal-600 px-5 text-teal-700 hover:bg-teal-50"
          >
            <Link to="/search">
              <SearchIcon />
              Search
            </Link>
          </Button>
        </div>
      </Container>
    </footer>
  )
}
