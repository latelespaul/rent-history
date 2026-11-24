import { useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { deleteProperty, submitProperty } from "@/api/properties"
import { Container } from "@/components/layout/Container"
import { Badge, DemoBadge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { PencilIcon, PlusIcon, TenancyIcon } from "@/components/ui/icons.gen"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { useAuth } from "@/features/auth/authContext"
import { addHomePath } from "@/features/mode/mode"
import { MyReviews } from "@/features/review/MyReviews"
import { BRAND } from "@/lib/brand"
import { propertyTitle, sizeLabel, typeLabel } from "@/features/property/labels"
import { RatingLine } from "@/features/property/Rating"
import { ActionError } from "@/features/tenancy/ApprovalPrompt"
import { PROPERTY_STATUS, rentLine } from "@/features/tenancy/labels"
import { TenancyCard } from "@/features/tenancy/TenancyCard"
import {
  useMyProperties,
  useMyTenancies,
  useRefreshTenancyData,
} from "@/features/tenancy/useTenancies"
import type { PropertyDetail, Tenancy } from "@/types/api"

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="rounded-xl border border-line bg-surface p-4 shadow-sm">
      <p className="text-xs font-semibold tracking-wide text-ink-500 uppercase">{label}</p>
      <p className="num mt-1 text-2xl font-semibold text-ink-900">{value}</p>
    </div>
  )
}

function PropertyRow({ property, tenancy }: { property: PropertyDetail; tenancy?: Tenancy }) {
  const refresh = useRefreshTenancyData()
  const [confirmRemove, setConfirmRemove] = useState(false)
  const status = PROPERTY_STATUS[property.status]
  const submit = useMutation({
    mutationFn: () => submitProperty(property.id),
    onSuccess: () => void refresh(),
  })
  const remove = useMutation({
    mutationFn: () => deleteProperty(property.id),
    onSuccess: () => void refresh(),
  })
  const canSubmit = property.status === "DRAFT" || property.status === "REJECTED"
  const canInvite = property.status === "PUBLISHED" || property.status === "ARCHIVED"
  const published = property.status === "PUBLISHED"

  return (
    <li className="space-y-2 border-b border-line px-5 py-4 last:border-0">
      <div className="flex flex-wrap items-start gap-x-4 gap-y-2">
        <div className="min-w-0 flex-1 space-y-1">
          <p className="flex flex-wrap items-center gap-2">
            <Link to={`/p/${property.id}`} className="font-semibold text-ink-900">
              {propertyTitle(property)}
            </Link>
            {property.unitLabel && (
              <span className="text-sm text-ink-500">· {property.unitLabel}</span>
            )}
            <Badge variant={status.variant}>{status.label}</Badge>
            {property.demo && <DemoBadge />}
          </p>
          <p className="text-sm text-ink-500">
            {[
              `${property.locality}, ${property.city.name}`,
              typeLabel(property.propertyType),
              sizeLabel(property),
            ]
              .filter(Boolean)
              .join(" · ")}
          </p>
          <p className="text-sm text-ink-700">
            {tenancy ? (
              <>
                <Badge variant="tenant">Occupied</Badge>{" "}
                <span className="num">{rentLine(tenancy)}</span>
              </>
            ) : (
              <Badge>No active tenancy</Badge>
            )}
          </p>
        </div>
        <RatingLine rating={property.rating} className="text-sm" />
      </div>
      <div className="flex flex-wrap gap-2">
        <Button asChild size="sm" variant="outline">
          <Link to={`/me/properties/${property.id}/edit`}>
            <PencilIcon />
            Edit
          </Link>
        </Button>
        {canSubmit && (
          <Button size="sm" disabled={submit.isPending} onClick={() => submit.mutate()}>
            Submit for review
          </Button>
        )}
        {canInvite && (
          <Button asChild size="sm" variant="outline">
            <Link to={`/tenancies/new?property=${property.id}`}>
              <TenancyIcon />
              Invite a tenant
            </Link>
          </Button>
        )}
        {property.status !== "ARCHIVED" &&
          (confirmRemove ? (
            <>
              <Button
                size="sm"
                variant="destructive"
                disabled={remove.isPending}
                onClick={() => remove.mutate()}
              >
                {published ? "Yes, archive it" : "Yes, delete the draft"}
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setConfirmRemove(false)}>
                Keep it
              </Button>
            </>
          ) : (
            <Button size="sm" variant="ghost" onClick={() => setConfirmRemove(true)}>
              {published ? "Archive" : "Delete"}
            </Button>
          ))}
      </div>
      {property.status === "PENDING_REVIEW" && (
        <p className="text-xs text-ink-500">An admin checks the listing before it goes public.</p>
      )}
      {submit.isError && <ActionError error={submit.error} />}
      {remove.isError && <ActionError error={remove.error} />}
    </li>
  )
}

/**
 * Landlord dashboard and "My properties" (`/me/dashboard`, `/me/properties`; DESIGN.md screen 8):
 * real counts, tenancy requests waiting for the landlord, and every listing in every state with
 * its next step (edit, submit for review, invite a tenant, archive).
 */
export default function LandlordDashboardPage() {
  const { state } = useAuth()
  const isLandlord = state.user?.roles.includes("LANDLORD") ?? false
  if (!isLandlord) return <NoOwnedHomes />
  return <OwnerDashboard />
}

/**
 * Owner mode for an account without the landlord role (PLAN R10): nothing is owned yet, so the
 * page says how to start. Adding a home with "I own it" gives the role (D-139).
 */
function NoOwnedHomes() {
  return (
    <main>
      <Container className="space-y-6 py-8">
        <div>
          <h1 className="font-display text-3xl font-semibold text-ink-900">Homes you own</h1>
          <p className="mt-1 text-ink-500">Your homes, their tenants and the reviews about you.</p>
        </div>
        <EmptyState
          icon="home"
          title="No homes you own yet"
          description={`Add a home you own and you become its landlord on ${BRAND.name}. Its tenancies and reviews then show here.`}
          action={
            <Button asChild>
              <Link to={addHomePath("OWNER")} data-testid="owner-add-home">
                <PlusIcon />
                Add a home you own
              </Link>
            </Button>
          }
        />
      </Container>
    </main>
  )
}

function OwnerDashboard() {
  const { state } = useAuth()
  const properties = useMyProperties()
  const tenancies = useMyTenancies("LANDLORD")
  const items = properties.data?.items ?? []
  const totalProperties = properties.data?.page.totalItems ?? items.length
  const lets = tenancies.data?.items ?? []
  const active = lets.filter((t) => t.status === "ACTIVE")
  const waiting = lets.filter((t) => t.status === "PENDING_CONFIRMATION")
  const needsAction = waiting.filter((t) => t.actions.confirm)
  const activeByProperty = new Map(active.map((t) => [t.property.id, t]))

  return (
    <main>
      <Container className="space-y-6 py-8">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="font-display text-3xl font-semibold text-ink-900">
              Hello, {state.user?.shortName}
            </h1>
            <p className="mt-1 text-ink-500">Your properties, tenants and tenancy requests.</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline">
              <Link to="/tenancies/new">
                <TenancyIcon />
                Invite a tenant
              </Link>
            </Button>
            <Button asChild>
              <Link to="/me/properties/new">
                <PlusIcon />
                Add property
              </Link>
            </Button>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <Stat label="Properties" value={properties.isSuccess ? totalProperties : "–"} />
          <Stat
            label="Published"
            value={
              properties.isSuccess ? items.filter((p) => p.status === "PUBLISHED").length : "–"
            }
          />
          <Stat label="Active tenancies" value={tenancies.isSuccess ? active.length : "–"} />
          <Stat label="Pending requests" value={tenancies.isSuccess ? waiting.length : "–"} />
        </div>

        {needsAction.length > 0 && (
          <section aria-labelledby="needs-heading" className="space-y-3">
            <h2 id="needs-heading" className="text-xl font-semibold">
              Needs your action ({needsAction.length})
            </h2>
            {needsAction.map((t) => (
              <TenancyCard key={t.id} tenancy={t} />
            ))}
          </section>
        )}
        {tenancies.isError && (
          <ErrorState
            title="Tenancy requests could not be loaded"
            error={tenancies.error}
            onRetry={() => void tenancies.refetch()}
          />
        )}

        <section
          aria-labelledby="mine-heading"
          className="rounded-xl border border-line bg-surface shadow-sm"
        >
          <div className="flex flex-wrap items-center justify-between gap-2 px-5 pt-4 pb-2">
            <h2 id="mine-heading" className="text-xl font-semibold">
              My properties
            </h2>
            <span className="text-sm text-ink-500">
              {totalProperties > items.length
                ? `Showing the first ${items.length} of ${totalProperties}`
                : "All statuses"}
            </span>
          </div>
          {properties.isPending ? (
            <p className="px-5 pb-5 text-sm text-ink-500">Loading your properties…</p>
          ) : properties.isError ? (
            <div className="p-5">
              <ErrorState
                title="Your properties could not be loaded"
                error={properties.error}
                onRetry={() => void properties.refetch()}
              />
            </div>
          ) : items.length === 0 ? (
            <div className="p-5">
              <EmptyState
                icon="home"
                title="No properties yet"
                description="Add your first home. It stays a private draft until you submit it for review."
                action={
                  <Button asChild>
                    <Link to="/me/properties/new">Add property</Link>
                  </Button>
                }
              />
            </div>
          ) : (
            <ul aria-label="My properties">
              {items.map((p) => (
                <PropertyRow key={p.id} property={p} tenancy={activeByProperty.get(p.id)} />
              ))}
            </ul>
          )}
        </section>

        {tenancies.isSuccess && (
          <section
            aria-labelledby="lets-heading"
            className="space-y-3"
            data-testid="owner-tenancies"
          >
            <h2 id="lets-heading" className="text-xl font-semibold">
              Tenancies on your homes ({lets.length})
            </h2>
            {lets.length === 0 ? (
              <EmptyState
                icon="tenancy"
                title="No tenancies yet"
                description="Invite a tenant from one of your published properties."
              />
            ) : (
              lets.map((t) => <TenancyCard key={t.id} tenancy={t} />)
            )}
          </section>
        )}

        <MyReviews
          kind="received"
          side="LANDLORD"
          title="Reviews about you"
          empty="Reviews your tenants write about you as a landlord show here."
          testId="owner-reviews-received"
        />
        <MyReviews
          kind="written"
          side="LANDLORD"
          title="Reviews you wrote of tenants"
          empty="Review a tenant from their tenancy once it has run for a while."
          testId="owner-reviews-written"
        />
      </Container>
    </main>
  )
}
