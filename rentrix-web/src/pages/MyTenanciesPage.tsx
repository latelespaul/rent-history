import { useState } from "react"
import { Link } from "react-router-dom"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { PlusIcon } from "@/components/ui/icons.gen"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { useAuth } from "@/features/auth/authContext"
import { NextSteps } from "@/features/home/NextSteps"
import { stepsLeft, stepsLeftLabel } from "@/features/home/steps"
import { TenancyCard } from "@/features/tenancy/TenancyCard"
import {
  groupOf,
  TENANCY_GROUPS,
  tenancyHome,
  type TenancyGroupKey,
} from "@/features/tenancy/labels"
import { useMyTenancies } from "@/features/tenancy/useTenancies"
import { cn } from "@/lib/utils"
import type { TenancyParty } from "@/types/api"

const SIDES: Array<{ value: TenancyParty | null; label: string }> = [
  { value: null, label: "Both sides" },
  { value: "TENANT", label: "As tenant" },
  { value: "LANDLORD", label: "As landlord" },
]

/**
 * `/me/tenancies` (DESIGN.md screen 7) for tenants and landlords: what needs the caller's action
 * first, then every tenancy by state. Dual-role users can narrow to one side.
 */
export default function MyTenanciesPage() {
  const { state } = useAuth()
  const roles = state.user?.roles ?? []
  const isLandlord = roles.includes("LANDLORD")
  const both = isLandlord && roles.includes("TENANT")
  const [side, setSide] = useState<TenancyParty | null>(null)
  const [tab, setTab] = useState<TenancyGroupKey>("current")
  const query = useMyTenancies(side)

  const items = query.data?.items ?? []
  const needsAction = items.filter((t) => t.actions.confirm)
  // Optional steps left on the tenant's own records stay visible until done (captain 2026-11-23 14:53).
  const withSteps = items.filter((t) => stepsLeft(t) > 0)
  const shown = items.filter((t) => groupOf(t.status) === tab)

  return (
    <main>
      <Container className="space-y-6 py-8">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="font-display text-3xl font-semibold text-ink-900">My tenancies</h1>
            <p className="mt-1 text-ink-500">Homes you rent or let through confirmed tenancies.</p>
          </div>
          <Button asChild>
            {isLandlord ? (
              <Link to="/tenancies/new">
                <PlusIcon />
                Invite a tenant
              </Link>
            ) : (
              <Link to="/search">
                <PlusIcon />
                Record a tenancy
              </Link>
            )}
          </Button>
        </div>

        {both && (
          <div role="group" aria-label="Side" className="flex flex-wrap gap-2">
            {SIDES.map((s) => (
              <Button
                key={s.label}
                size="sm"
                variant={side === s.value ? "default" : "outline"}
                aria-pressed={side === s.value}
                onClick={() => setSide(s.value)}
              >
                {s.label}
              </Button>
            ))}
          </div>
        )}

        {query.isPending ? (
          <p className="text-sm text-ink-500">Loading your tenancies…</p>
        ) : query.isError ? (
          <ErrorState
            title="Your tenancies could not be loaded"
            error={query.error}
            onRetry={() => void query.refetch()}
          />
        ) : (
          <>
            {withSteps.length > 0 && (
              <section
                aria-labelledby="next-steps-heading"
                className="space-y-3"
                data-testid="my-next-steps"
              >
                <h2 id="next-steps-heading" className="text-xl font-semibold">
                  Next steps for your homes
                </h2>
                {withSteps.map((t) => (
                  <div key={t.id} className="space-y-2 rounded-xl border border-line bg-sunk p-4">
                    <p className="font-semibold text-ink-900">
                      <Link to={`/p/${t.property.id}`}>{tenancyHome(t)}</Link>
                      <span className="ml-2 text-sm font-normal text-ink-500">
                        {stepsLeftLabel(stepsLeft(t))}
                      </span>
                    </p>
                    <NextSteps tenancy={t} />
                  </div>
                ))}
              </section>
            )}
            {needsAction.length > 0 && (
              <section aria-labelledby="action-heading" className="space-y-3">
                <h2 id="action-heading" className="text-xl font-semibold">
                  Needs your action ({needsAction.length})
                </h2>
                {needsAction.map((t) => (
                  <TenancyCard key={t.id} tenancy={t} />
                ))}
              </section>
            )}

            <div
              role="tablist"
              aria-label="Tenancy state"
              className="flex flex-wrap gap-1 border-b border-line"
            >
              {TENANCY_GROUPS.map((g) => {
                const count = items.filter((t) => groupOf(t.status) === g.key).length
                return (
                  <button
                    key={g.key}
                    role="tab"
                    type="button"
                    aria-selected={tab === g.key}
                    onClick={() => setTab(g.key)}
                    className={cn(
                      "-mb-px border-b-2 px-3 py-2 text-sm font-semibold",
                      tab === g.key
                        ? "border-teal-700 text-teal-700"
                        : "border-transparent text-ink-500 hover:text-ink-900",
                    )}
                  >
                    {g.label} ({count})
                  </button>
                )
              })}
            </div>

            <div role="tabpanel" className="space-y-3">
              {shown.length === 0 ? (
                <EmptyState
                  icon="tenancy"
                  title="Nothing here"
                  description={
                    isLandlord
                      ? "Invite a tenant from one of your published properties."
                      : "Add the home you rent or rented: your tenancy shows at once."
                  }
                  action={
                    !isLandlord && (
                      <Button asChild>
                        <Link to="/homes/new" data-testid="empty-add-home">
                          Add your home
                        </Link>
                      </Button>
                    )
                  }
                />
              ) : (
                shown.map((t) => <TenancyCard key={t.id} tenancy={t} />)
              )}
            </div>
          </>
        )}
      </Container>
    </main>
  )
}
