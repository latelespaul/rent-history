import { useState, type FormEvent, type ReactNode } from "react"
import { useMutation } from "@tanstack/react-query"
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom"
import { createProperty, submitProperty, updateProperty } from "@/api/properties"
import { isApiError } from "@/api/errors"
import { Container } from "@/components/layout/Container"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Notice } from "@/components/ui/notice"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { Textarea } from "@/components/ui/textarea"
import { useCities } from "@/features/city/useCities"
import { LocationPickers } from "@/features/landlord/LocationPickers"
import { EMPTY_DRAFT } from "@/features/landlord/locationDraft"
import {
  checkForm,
  EMPTY_FORM,
  formFromProperty,
  locationEditable,
  toCreateBody,
  toPatchBody,
  type PropertyFormValues,
} from "@/features/landlord/propertyForm"
import { typeLabel } from "@/features/property/labels"
import { useProperty } from "@/features/property/useProperties"
import { ActionError } from "@/features/tenancy/ApprovalPrompt"
import { PROPERTY_STATUS } from "@/features/tenancy/labels"
import { useRefreshTenancyData } from "@/features/tenancy/useTenancies"
import type { Furnishing, PropertyDetail, PropertyType } from "@/types/api"

const selectClass =
  "h-10 w-full rounded-md border border-line-strong bg-surface px-3 text-sm disabled:opacity-50"
const TYPES: PropertyType[] = ["APARTMENT", "INDEPENDENT_HOUSE", "VILLA", "STUDIO", "ROOM", "PG"]
const FURNISHING: Array<{ value: Furnishing; label: string }> = [
  { value: "UNFURNISHED", label: "Unfurnished" },
  { value: "SEMI", label: "Semi-furnished" },
  { value: "FULL", label: "Fully furnished" },
]

function Field({
  id,
  label,
  hint,
  error,
  children,
}: {
  id: string
  label: ReactNode
  hint?: string
  error?: string
  children: ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? (
        <p className="text-xs text-danger-600">{error}</p>
      ) : (
        hint && <p className="text-xs text-ink-500">{hint}</p>
      )}
    </div>
  )
}

const optional = <span className="font-normal text-ink-500">(optional)</span>

function PropertyForm({ property }: { property?: PropertyDetail }) {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const cities = useCities()
  const refresh = useRefreshTenancyData()
  const initial = property ? formFromProperty(property) : EMPTY_FORM
  const [values, setValues] = useState<PropertyFormValues>(initial)
  const [checked, setChecked] = useState<Record<string, string>>({})
  const status = property?.status
  const editable = locationEditable(status)
  const set = <K extends keyof PropertyFormValues>(key: K, value: PropertyFormValues[K]) =>
    setValues((v) => ({ ...v, [key]: value }))

  const save = useMutation({
    mutationFn: () =>
      property
        ? updateProperty(property.id, toPatchBody(initial, values, property.status))
        : createProperty(toCreateBody(values)),
    onSuccess: async (saved) => {
      await refresh()
      navigate(`/me/properties/${saved.id}/edit?saved=1`, { replace: true })
    },
  })
  const submit = useMutation({
    mutationFn: () => submitProperty(property!.id),
    onSuccess: async (submitted) => {
      await refresh()
      navigate(`/me/properties/${submitted.id}/edit`, { replace: true })
    },
  })

  const apiError = (field: string) =>
    isApiError(save.error) ? save.error.fieldError(field) : undefined
  const error = (field: string) => checked[field] ?? apiError(field)
  const locationError =
    checked.location ??
    (isApiError(save.error)
      ? save.error.fieldErrors.find((e) => e.field.startsWith("location"))?.message
      : undefined)

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const errors = editable ? checkForm(values) : {}
    setChecked(errors)
    if (Object.keys(errors).length === 0) save.mutate()
  }

  return (
    <form noValidate onSubmit={onSubmit} aria-label="Property" className="space-y-6">
      {params.get("saved") && save.isIdle && (
        <Notice tone="teal">
          Saved.{" "}
          {editable
            ? "Submit it for review when it is ready; an admin checks it before it goes public."
            : ""}
        </Notice>
      )}
      {!editable && (
        <Notice tone="info" icon="lock">
          The location of a {PROPERTY_STATUS[status!].label.toLowerCase()} listing is locked. Ask an
          admin to move it. Other details can still change.
        </Notice>
      )}

      <section className="space-y-4 rounded-xl border border-line bg-surface p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Where is it?</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="pf-city" label="City" error={error("city")}>
            <select
              id="pf-city"
              className={selectClass}
              value={values.city}
              disabled={!editable}
              onChange={(e) =>
                setValues((v) => ({ ...v, city: e.target.value, location: EMPTY_DRAFT }))
              }
            >
              <option value="">Pick a city</option>
              {cities.data?.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
          <Field id="pf-pincode" label="PIN code" error={error("pincode")}>
            <Input
              id="pf-pincode"
              inputMode="numeric"
              maxLength={6}
              value={values.pincode}
              disabled={!editable}
              onChange={(e) => set("pincode", e.target.value)}
              className="h-10"
            />
          </Field>
        </div>
        <LocationPickers
          city={values.city}
          value={values.location}
          onChange={(location) => set("location", location)}
          disabled={!editable}
          error={locationError}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            id="pf-building"
            label={<>Building name {optional}</>}
            error={error("buildingName")}
          >
            <Input
              id="pf-building"
              value={values.buildingName}
              maxLength={160}
              disabled={!editable}
              onChange={(e) => set("buildingName", e.target.value)}
              className="h-10"
            />
          </Field>
          <Field id="pf-street" label={<>Street {optional}</>} error={error("street")}>
            <Input
              id="pf-street"
              value={values.street}
              maxLength={200}
              disabled={!editable}
              onChange={(e) => set("street", e.target.value)}
              className="h-10"
            />
          </Field>
          <Field
            id="pf-unit"
            label={<>Flat or house number {optional}</>}
            hint="Private: only you, your tenants and admins see it."
            error={error("unitLabel")}
          >
            <Input
              id="pf-unit"
              value={values.unitLabel}
              maxLength={40}
              onChange={(e) => set("unitLabel", e.target.value)}
              className="h-10"
            />
          </Field>
        </div>
      </section>

      <section className="space-y-4 rounded-xl border border-line bg-surface p-6 shadow-sm">
        <h2 className="text-xl font-semibold">About the home</h2>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field id="pf-type" label="Type" error={error("propertyType")}>
            <select
              id="pf-type"
              className={selectClass}
              value={values.propertyType}
              onChange={(e) => set("propertyType", e.target.value as PropertyType)}
            >
              {TYPES.map((t) => (
                <option key={t} value={t}>
                  {typeLabel(t)}
                </option>
              ))}
            </select>
          </Field>
          <Field id="pf-bedrooms" label={<>Bedrooms {optional}</>} error={error("bedrooms")}>
            <Input
              id="pf-bedrooms"
              inputMode="numeric"
              value={values.bedrooms}
              onChange={(e) => set("bedrooms", e.target.value)}
              className="h-10"
            />
          </Field>
          <Field id="pf-bathrooms" label={<>Bathrooms {optional}</>} error={error("bathrooms")}>
            <Input
              id="pf-bathrooms"
              inputMode="numeric"
              value={values.bathrooms}
              onChange={(e) => set("bathrooms", e.target.value)}
              className="h-10"
            />
          </Field>
          <Field id="pf-area-sqft" label={<>Area, sq ft {optional}</>} error={error("areaSqft")}>
            <Input
              id="pf-area-sqft"
              inputMode="numeric"
              value={values.areaSqft}
              onChange={(e) => set("areaSqft", e.target.value)}
              className="h-10"
            />
          </Field>
          <Field id="pf-furnishing" label={<>Furnishing {optional}</>}>
            <select
              id="pf-furnishing"
              className={selectClass}
              value={values.furnishing}
              onChange={(e) => set("furnishing", e.target.value as Furnishing | "")}
            >
              <option value="">Not stated</option>
              {FURNISHING.map((f) => (
                <option key={f.value} value={f.value}>
                  {f.label}
                </option>
              ))}
            </select>
          </Field>
          <Field id="pf-parking" label={<>Parking {optional}</>}>
            <select
              id="pf-parking"
              className={selectClass}
              value={values.parking}
              onChange={(e) => set("parking", e.target.value as PropertyFormValues["parking"])}
            >
              <option value="">Not stated</option>
              <option value="yes">Yes</option>
              <option value="no">No</option>
            </select>
          </Field>
        </div>
        <Field id="pf-description" label={<>Description {optional}</>} error={error("description")}>
          <Textarea
            id="pf-description"
            value={values.description}
            maxLength={2000}
            onChange={(e) => set("description", e.target.value)}
          />
        </Field>
      </section>

      <section className="space-y-2 rounded-xl border border-dashed border-line-strong bg-surface p-6">
        <h2 className="text-xl font-semibold">Photos and ownership proof</h2>
        <p className="text-sm text-ink-500">
          Photo and proof uploads are coming soon. Your listing can be saved and submitted without
          them for now.
        </p>
      </section>

      {save.isError && <ActionError error={save.error} />}
      {submit.isError && <ActionError error={submit.error} />}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? "Saving…" : property ? "Save changes" : "Save as draft"}
        </Button>
        {property && editable && (
          <Button
            type="button"
            variant="outline"
            disabled={submit.isPending || save.isPending}
            onClick={() => submit.mutate()}
          >
            {submit.isPending ? "Submitting…" : "Submit for review"}
          </Button>
        )}
        <Button type="button" variant="ghost" asChild>
          <Link to="/me/dashboard">Back to my properties</Link>
        </Button>
      </div>
    </form>
  )
}

/**
 * Property create / edit (`/me/properties/new`, `/me/properties/:id/edit`; DESIGN.md "property
 * create/edit form"): place with the location pickers, facts, then "Submit for review" (D-92).
 * Photo and proof uploads (B9) are a placeholder.
 */
export default function PropertyFormPage() {
  const { id = "" } = useParams()
  const query = useProperty(id)
  const editing = Boolean(id)

  let body
  if (!editing) body = <PropertyForm />
  else if (query.isPending) body = <p className="text-sm text-ink-500">Loading the listing…</p>
  else if (query.isError)
    body =
      isApiError(query.error) && query.error.status === 404 ? (
        <EmptyState icon="home" title="This listing is not yours or does not exist" />
      ) : (
        <ErrorState
          title="The listing could not be loaded"
          error={query.error}
          onRetry={() => void query.refetch()}
        />
      )
  else
    body = <PropertyForm key={`${query.data.id}-${query.data.updatedAt}`} property={query.data} />

  const status = query.data ? PROPERTY_STATUS[query.data.status] : null
  return (
    <main>
      <Container className="max-w-[860px] space-y-6 py-8">
        <div>
          <h1 className="flex flex-wrap items-center gap-3 font-display text-3xl font-semibold text-ink-900">
            {editing ? "Edit property" : "Add a property"}
            {editing && status && <Badge variant={status.variant}>{status.label}</Badge>}
          </h1>
          <p className="mt-1 text-ink-500">
            {editing
              ? "Changes to a published listing show right away; the location stays locked."
              : "Start with where it is. It stays a private draft until you submit it for review."}
          </p>
        </div>
        {body}
      </Container>
    </main>
  )
}
