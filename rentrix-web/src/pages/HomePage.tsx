import { Link, useNavigate, useSearchParams } from "react-router-dom"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Icon, type IconName } from "@/components/ui/icon"
import { CityCards } from "@/features/city/CityCards"
import { LaunchCopy } from "@/features/city/LaunchCopy"
import { SearchBar } from "@/features/property/SearchBar"
import { withFilters } from "@/features/property/searchParams"

const TRUST_STEPS: ReadonlyArray<{ icon: IconName; title: string; text: string }> = [
  {
    icon: "tenancy",
    title: "1. Both sides confirm the tenancy",
    text: "Landlord or tenant records the tenancy: home, dates, rent. The other side confirms. Strangers cannot rate anyone.",
  },
  {
    icon: "eye-off",
    title: "2. Reviews unlock together",
    text: "Landlord and tenant reviews stay hidden until both are in, or 14 days pass. No revenge reviews.",
  },
  {
    icon: "trend-up",
    title: "3. Real rent history",
    text: "Rent that both sides confirmed builds the home's history. No tenant names on it, ever.",
  },
]

/**
 * Home (DESIGN.md screen 1): search entry, the launch line and one card per live city. Every
 * city and every number comes from `GET /cities` and `GET /stats` (D-03, D-85); none is written here.
 */
export default function HomePage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const city = params.get("city")

  return (
    <main>
      <section className="mx-auto max-w-page space-y-6 px-4 pt-14 pb-12 nav:px-6">
        <div className="max-w-3xl space-y-3">
          <p className="text-xs font-semibold tracking-[0.08em] text-teal-700 uppercase">
            Renting, with receipts
          </p>
          <h1 className="font-display text-3xl font-semibold sm:text-[2.75rem] sm:leading-tight">
            Check the landlord, the tenant and the flat before you sign.
          </h1>
          <p className="text-lg text-ink-700">
            Every rating on Rentrix comes from a tenancy that both sides confirmed. Tenants rate
            landlords and homes. Landlords rate tenants. Rent history is real rent that was paid.
          </p>
        </div>

        <SearchBar
          city={city}
          locality=""
          className="max-w-4xl"
          onCityChange={(code) =>
            setParams(
              (prev) => {
                const next = new URLSearchParams(prev)
                next.set("city", code)
                return next
              },
              { replace: true },
            )
          }
          onSubmit={(value) =>
            navigate({
              pathname: "/search",
              search: withFilters(new URLSearchParams(), {
                city: value.city ?? undefined,
                locality: value.locality,
              }).toString(),
            })
          }
        />
        <LaunchCopy className="block text-sm text-ink-500" />
      </section>

      <section
        aria-labelledby="cities-heading"
        className="mx-auto max-w-page space-y-4 px-4 pb-14 nav:px-6"
      >
        <h2 id="cities-heading" className="text-2xl font-semibold">
          Pick a city
        </h2>
        <CityCards />
      </section>

      <section aria-labelledby="trust-heading" className="border-y border-line bg-surface">
        <div className="mx-auto max-w-page space-y-6 px-4 py-12 nav:px-6">
          <h2 id="trust-heading" className="text-2xl font-semibold">
            How trust works
          </h2>
          <ol className="grid gap-6 md:grid-cols-3">
            {TRUST_STEPS.map((step) => (
              <li key={step.title} className="flex gap-4">
                <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-teal-50 text-teal-700">
                  <Icon name={step.icon} size={22} />
                </span>
                <span className="space-y-1">
                  <span className="block text-lg font-semibold text-ink-900">{step.title}</span>
                  <span className="block text-sm text-ink-700">{step.text}</span>
                </span>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="mx-auto grid max-w-page gap-4 px-4 py-12 md:grid-cols-2 nav:px-6">
        <div className="space-y-3 rounded-xl border border-line bg-surface p-6 shadow-sm">
          <Badge variant="tenant">For tenants</Badge>
          <h2 className="text-xl font-semibold">Know who you rent from</h2>
          <p className="text-ink-700">
            See a landlord&apos;s rating from past tenants, the home&apos;s rating and what rent it
            actually went for.
          </p>
          <Button asChild variant="outline">
            <Link to="/login">Sign in as tenant</Link>
          </Button>
        </div>
        <div className="space-y-3 rounded-xl border border-line bg-surface p-6 shadow-sm">
          <Badge variant="landlord">For landlords</Badge>
          <h2 className="text-xl font-semibold">Know who you rent to</h2>
          <p className="text-ink-700">
            When a tenant enquires, see their rating from previous landlords. Build your own
            reputation as a fair landlord.
          </p>
          <Button asChild variant="outline">
            <Link to="/login">Sign in as landlord</Link>
          </Button>
        </div>
      </section>
    </main>
  )
}
