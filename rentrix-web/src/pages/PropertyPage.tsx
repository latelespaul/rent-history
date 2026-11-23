import { Link, useParams } from "react-router-dom"
import { isApiError } from "@/api/errors"
import { Badge, DemoBadge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  BathIcon,
  BuildingIcon,
  ChevronRightIcon,
  LockIcon,
  MapPinIcon,
  SofaIcon,
} from "@/components/ui/icons.gen"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { initials } from "@/components/layout/nav"
import {
  locationPending,
  placeLine,
  propertyTitle,
  reviewCount,
  unitLine,
} from "@/features/property/labels"
import { LocationPendingBadge } from "@/features/property/LocationPendingBadge"
import { useMyTenancies } from "@/features/tenancy/useTenancies"
import { PersonTick } from "@/features/property/PersonTick"
import { NextSteps } from "@/features/home/NextSteps"
import { stepsLeft, stepsLeftLabel } from "@/features/home/steps"
import { PropertyFacts } from "@/features/property/PropertyFacts"
import { PropertyReviews } from "@/features/property/PropertyReviews"
import { RatingLine } from "@/features/property/Rating"
import { RentHistory } from "@/features/property/RentHistory"
import { useProperty, useSignedIn } from "@/features/property/useProperties"
import { useAuth } from "@/features/auth/authContext"
import { PencilIcon, TenancyIcon } from "@/components/ui/icons.gen"
import type { PropertyDetail } from "@/types/api"
import { BRAND } from "@/lib/brand"

function Breadcrumb({ property }: { property: PropertyDetail }) {
  const city = encodeURIComponent(property.city.code)
  const areaPending = property.location?.area.status === "PENDING"
  return (
    <nav aria-label="Breadcrumb" className="text-sm text-ink-700">
      <ol className="flex flex-wrap items-center gap-1.5">
        <li>
          <Link to={`/search?city=${city}`} className="text-ink-700">
            {property.city.name}
          </Link>
        </li>
        <ChevronRightIcon size={14} className="text-ink-300" />
        <li>
          <Link
            to={`/search?city=${city}&locality=${encodeURIComponent(property.locality)}`}
            className="text-ink-700"
          >
            {property.locality}
          </Link>
          {areaPending && <span className="ml-1 text-xs text-warn-600">(pending review)</span>}
        </li>
        <ChevronRightIcon size={14} className="text-ink-300" />
        <li aria-current="page" className="text-ink-500">
          {propertyTitle(property)}
        </li>
      </ol>
    </nav>
  )
}

function Gallery() {
  return (
    <div className="grid h-56 gap-2 overflow-hidden rounded-xl sm:h-72 sm:grid-cols-[2fr_1fr] sm:grid-rows-2">
      <div className="rx-placeholder grid place-items-center sm:row-span-2">
        <BuildingIcon size={40} className="text-ink-300" />
      </div>
      <div className="rx-placeholder hidden place-items-center sm:grid">
        <SofaIcon size={32} className="text-ink-300" />
      </div>
      <div className="rx-placeholder hidden place-items-center sm:grid">
        <BathIcon size={32} className="text-ink-300" />
      </div>
    </div>
  )
}

function RatingCard({ property, signedIn }: { property: PropertyDetail; signedIn: boolean }) {
  const { landlord } = property
  return (
    <section
      aria-labelledby="rating-heading"
      className="space-y-4 rounded-xl border border-line bg-surface p-5 shadow-sm"
    >
      <div className="space-y-2">
        <h2
          id="rating-heading"
          className="text-xs font-semibold tracking-[0.08em] text-teal-700 uppercase"
        >
          Property rating
        </h2>
        <RatingLine rating={property.rating} className="text-base" />
        {property.rating.average === null && property.rating.reviewCount > 0 && (
          <p className="text-sm text-ink-500">
            An average appears after 3 reviews, so one review cannot define a home.
          </p>
        )}
      </div>
      <div className="flex items-start gap-3 border-t border-line pt-4">
        <span className="grid size-11 shrink-0 place-items-center rounded-full bg-landlord-100 text-sm font-semibold text-landlord-600">
          {initials(landlord.shortName)}
        </span>
        <div className="min-w-0 space-y-1">
          <p className="flex flex-wrap items-center gap-2">
            <span className="font-semibold text-ink-900">{landlord.shortName}</span>
            <Badge variant="landlord">Landlord</Badge>
            {!property.addedByTenant && <PersonTick verified={landlord.verified} role="landlord" />}
          </p>
          {property.addedByTenant ? (
            <p className="text-xs text-ink-500">
              A tenant added this home. Its owner can claim it later.
            </p>
          ) : (
            <>
              <RatingLine rating={landlord.rating} />
              <p className="text-xs text-ink-500">
                {landlord.rating.reviewCount > 0
                  ? `${reviewCount(landlord.rating.reviewCount)} as landlord`
                  : "No landlord reviews yet"}
              </p>
            </>
          )}
        </div>
      </div>
      {!signedIn && (
        <p className="text-xs text-ink-500">
          Sign in to see the landlord&apos;s full profile and every review they received.
        </p>
      )}
    </section>
  )
}

/**
 * The next step on a home for the signed-in viewer: its owner edits it or invites a tenant; a
 * tenant who rents or rented it records their tenancy through "Add your home", at once and without
 * waiting for the owner (D-142).
 */
/** The viewer's own optional steps on this home, until done (captain 2026-11-23 14:53). */
function MyNextSteps({ propertyId }: { propertyId: string }) {
  const mine = useMyTenancies("TENANT")
  const tenancy = mine.data?.items.find((t) => t.property.id === propertyId && stepsLeft(t) > 0)
  if (!tenancy) return null
  return (
    <section
      aria-labelledby="my-steps-heading"
      data-testid="home-next-steps"
      className="space-y-2 rounded-xl border border-line bg-sunk p-4"
    >
      <h2 id="my-steps-heading" className="font-semibold text-ink-900">
        Your tenancy here · {stepsLeftLabel(stepsLeft(tenancy))}
      </h2>
      <NextSteps tenancy={tenancy} />
    </section>
  )
}

/**
 * "Edit home" for the person who added it, and "Edit my tenancy" for the tenant who added their
 * tenancy here (D-156): free until the team verifies them.
 */
function MyEdits({ property }: { property: PropertyDetail }) {
  const mine = useMyTenancies("TENANT")
  const tenancy = mine.data?.items.find((t) => t.property.id === property.id && t.actions.edit)
  if (!property.editable && !tenancy) return null
  return (
    <>
      {property.editable && (
        <Button asChild variant="outline" size="sm" data-testid="home-edit">
          <Link to={`/homes/${property.id}/edit`}>
            <PencilIcon />
            Edit home
          </Link>
        </Button>
      )}
      {tenancy && (
        <Button asChild variant="outline" size="sm" data-testid="home-edit-tenancy">
          <Link to={`/tenancies/${tenancy.id}/edit`}>
            <PencilIcon />
            Edit my tenancy
          </Link>
        </Button>
      )}
    </>
  )
}

function PropertyActions({ property }: { property: PropertyDetail }) {
  const { state } = useAuth()
  const user = state.user
  if (!user) return null
  const owner = property.landlord.id === user.id
  if (owner) {
    return (
      <div className="flex flex-wrap gap-2">
        <Button asChild variant="outline" size="sm">
          <Link to={`/me/properties/${property.id}/edit`}>
            <PencilIcon />
            Edit listing
          </Link>
        </Button>
        {(property.status === "PUBLISHED" || property.status === "ARCHIVED") && (
          <Button asChild size="sm">
            <Link to={`/tenancies/new?property=${property.id}`}>
              <TenancyIcon />
              Invite a tenant
            </Link>
          </Button>
        )}
      </div>
    )
  }
  if (property.status !== "PUBLISHED") return <MyEdits property={property} />
  return (
    <div className="flex flex-wrap gap-2">
      <MyEdits property={property} />
      <Button asChild variant="outline" size="sm" data-testid="i-rent-this-home">
        <Link to={`/homes/new?property=${property.id}`}>
          <TenancyIcon />I rent or rented this home
        </Link>
      </Button>
    </div>
  )
}

/**
 * Property page (DESIGN.md screen 3, `/p/:id`). Anonymous visitors see place, facts and the
 * aggregate ratings; signed-in users also get rent history and review text (D-56). The flat
 * number shows only when the API sends it, i.e. to the owner and admins (D-57).
 */
export default function PropertyPage() {
  const { id = "" } = useParams()
  const { signedIn } = useSignedIn()
  const query = useProperty(id)

  if (query.isPending) {
    return (
      <main className="mx-auto max-w-page px-4 py-12 nav:px-6">
        <p className="text-sm text-ink-500">Loading the home…</p>
      </main>
    )
  }
  if (query.isError) {
    const missing =
      isApiError(query.error) && (query.error.status === 404 || query.error.status === 400)
    return (
      <main className="mx-auto max-w-page px-4 py-12 nav:px-6">
        {missing ? (
          <EmptyState
            icon="home"
            title={`This home is not on ${BRAND.name}`}
            description="It may have been archived, or the link is wrong."
            action={
              <Button asChild variant="outline">
                <Link to="/search">Search homes</Link>
              </Button>
            }
          />
        ) : (
          <ErrorState
            title="The home could not be loaded"
            error={query.error}
            onRetry={() => void query.refetch()}
          />
        )}
      </main>
    )
  }

  const property = query.data
  return (
    <main className="mx-auto max-w-page space-y-6 px-4 py-8 nav:px-6">
      <div className="space-y-2">
        <Breadcrumb property={property} />
        <h1 className="flex flex-wrap items-center gap-3 font-display text-3xl font-semibold">
          {propertyTitle(property)}
          {property.demo && <DemoBadge />}
          {property.addedByTenant && (
            <Badge data-testid="added-by-tenant">Added by a tenant · not verified yet</Badge>
          )}
          {locationPending(property.location) && <LocationPendingBadge />}
        </h1>
        <p className="inline-flex items-center gap-1.5 text-ink-700">
          <MapPinIcon size={16} className="text-ink-500" />
          {[property.street, placeLine(property)].filter(Boolean).join(", ")}
        </p>
        {/* Inside a tower or wing; a plain flat number shows in "About this home" below. */}
        {(property.location?.tower || property.location?.wing) && (
          <p data-testid="unit-line" className="text-sm font-medium text-ink-700">
            {unitLine(property.location, property.unitLabel)}
          </p>
        )}
        <PropertyActions property={property} />
        {signedIn && <MyNextSteps propertyId={property.id} />}
      </div>

      <Gallery />

      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="min-w-0 space-y-6">
          <section
            aria-label="About this home"
            className="space-y-4 rounded-xl border border-line bg-surface p-5 shadow-sm"
          >
            <PropertyFacts property={property} detailed className="text-base" />
            {property.description && (
              <p className="border-t border-line pt-4 text-ink-700">{property.description}</p>
            )}
            <p className="inline-flex items-center gap-1.5 text-sm text-ink-500">
              <LockIcon size={14} />
              {property.unitLabel
                ? property.unitLabelPublic
                  ? `Flat ${property.unitLabel}.`
                  : `Flat ${property.unitLabel} (shown to you as owner or admin only).`
                : "Flat number visible only to the landlord and tenants of this home."}
            </p>
          </section>

          <div className="lg:hidden">
            <RatingCard property={property} signedIn={signedIn} />
          </div>

          <RentHistory propertyId={property.id} signedIn={signedIn} />
          <PropertyReviews propertyId={property.id} rating={property.rating} signedIn={signedIn} />
        </div>

        <aside className="hidden space-y-6 lg:block">
          <RatingCard property={property} signedIn={signedIn} />
        </aside>
      </div>
    </main>
  )
}
