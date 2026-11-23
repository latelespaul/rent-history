import { useRef, useState, type FormEvent, type ReactNode } from "react"
import { useMutation } from "@tanstack/react-query"
import { Link, useNavigate, useParams } from "react-router-dom"
import { checkAddress, deleteHome, updateHome } from "@/api/homes"
import { isApiError } from "@/api/errors"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { TrashIcon } from "@/components/ui/icons.gen"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Notice } from "@/components/ui/notice"
import { EmptyState, ErrorState } from "@/components/ui/states"
import {
  checkHomeEdit,
  homeEditDraft,
  homeEditErrors,
  standaloneEdit,
  toHomeUpdate,
  type HomeEditDraft,
} from "@/features/edit/homeEdit"
import {
  needsWarning,
  VERIFIED_DELETE_WARNING,
  VERIFIED_EDIT_WARNING,
} from "@/features/edit/verified"
import type { HomeErrors } from "@/features/home/addHome"
import { HomeSizeChips } from "@/features/home/HomeSizeChips"
import { LocationPickers } from "@/features/landlord/LocationPickers"
import type { LocationDraft } from "@/features/landlord/locationDraft"
import { propertyTitle } from "@/features/property/labels"
import { useProperty } from "@/features/property/useProperties"
import { ActionError } from "@/features/tenancy/ApprovalPrompt"
import { useRefreshTenancyData } from "@/features/tenancy/useTenancies"
import type { PropertyDetail } from "@/types/api"

const optional = <span className="font-normal text-ink-500">(optional)</span>

function Field({
  id,
  label,
  error,
  children,
}: {
  id: string
  label: ReactNode
  error?: string
  children: ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error && (
        <p className="text-xs text-danger-600" data-testid={`${id}-error`}>
          {error}
        </p>
      )}
    </div>
  )
}

function EditForm({ property }: { property: PropertyDetail }) {
  const navigate = useNavigate()
  const refresh = useRefreshTenancyData()
  const form = useRef<HTMLFormElement>(null)
  const keepPin = useRef<string | null>(null)
  const [draft, setDraft] = useState<HomeEditDraft>(() => homeEditDraft(property))
  const [errors, setErrors] = useState<HomeErrors>({})
  const [pinWarning, setPinWarning] = useState<string | null>(null)
  const [checking, setChecking] = useState(false)
  const [confirm, setConfirm] = useState<null | "save" | "delete">(null)
  const verified = needsWarning(property.verification)
  const placed = Boolean(property.location)

  const set = <K extends keyof HomeEditDraft>(key: K, value: HomeEditDraft[K]) => {
    setDraft((d) => ({ ...d, [key]: value }))
    setErrors((e) => ({ ...e, [key]: undefined, form: undefined }))
    if (key === "pincode") setPinWarning(null)
  }
  const setLocation = (location: LocationDraft) => {
    setDraft((d) => ({ ...d, location }))
    setErrors((e) => ({
      ...e,
      area: undefined,
      society: undefined,
      tower: undefined,
      wing: undefined,
    }))
  }

  const save = useMutation({
    mutationFn: () => updateHome(property.id, toHomeUpdate(draft, property)),
    onSuccess: async (saved) => {
      await refresh()
      navigate(`/p/${saved.id}`)
    },
    onError: (error) => {
      setConfirm(null)
      if (isApiError(error) && error.fieldErrors.length > 0)
        setErrors(homeEditErrors(error.fieldErrors))
    },
  })
  const remove = useMutation({
    mutationFn: () => deleteHome(property.id),
    onSuccess: async () => {
      await refresh()
      navigate("/me/tenancies")
    },
    onError: () => setConfirm(null),
  })

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const found = checkHomeEdit(draft, property)
    if (Object.values(found).some(Boolean)) {
      setErrors(found)
      return
    }
    // The add-home PIN check: outside the city blocks, outside the area only warns.
    const pincode = draft.pincode.trim()
    const area = draft.location.area.kind === "pick" ? draft.location.area.id : undefined
    setChecking(true)
    try {
      const checked = await checkAddress({ city: property.city.code, pincode, area })
      if (checked.pincodeError) {
        setErrors({ pincode: checked.pincodeError })
        return
      }
      if (checked.pincodeWarning && keepPin.current !== pincode) {
        setPinWarning(checked.pincodeWarning)
        return
      }
    } catch {
      // The save checks again; a failed check never blocks the form.
    } finally {
      setChecking(false)
    }
    if (verified) setConfirm("save")
    else save.mutate()
  }

  const error = (key: string) => errors[key]
  return (
    <form
      ref={form}
      noValidate
      onSubmit={(e) => void submit(e)}
      className="space-y-5"
      data-testid="edit-home-form"
    >
      {verified ? (
        <Notice tone="warn" icon="shield-check">
          This home is verified. {VERIFIED_EDIT_WARNING}
        </Notice>
      ) : (
        <Notice tone="info">Not verified yet, so you can change or delete it freely.</Notice>
      )}
      {placed && (
        <LocationPickers
          city={property.city.code}
          value={draft.location}
          onChange={setLocation}
          error={error("area")}
          hint="Pick the area. If it or your society is missing, add it: new names show at once, tagged “Pending review”."
          levelErrors={{ society: error("society"), tower: error("tower"), wing: error("wing") }}
        />
      )}
      {standaloneEdit(draft, placed) && (
        <Field
          id="eh-building"
          label={
            property.propertyType === "APARTMENT" ? "Building name" : <>Building name {optional}</>
          }
          error={error("buildingName")}
        >
          <Input
            id="eh-building"
            value={draft.buildingName}
            maxLength={160}
            onChange={(e) => set("buildingName", e.target.value)}
            className="h-11"
          />
        </Field>
      )}
      <Field id="eh-street" label={<>Street {optional}</>} error={error("street")}>
        <Input
          id="eh-street"
          value={draft.street}
          maxLength={200}
          onChange={(e) => set("street", e.target.value)}
          className="h-11"
        />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field id="eh-unit" label="Flat / house no." error={error("unitLabel")}>
          <Input
            id="eh-unit"
            value={draft.unitLabel}
            maxLength={40}
            onChange={(e) => set("unitLabel", e.target.value)}
            className="h-11"
          />
        </Field>
        <Field id="eh-pincode" label="PIN code" error={error("pincode")}>
          <Input
            id="eh-pincode"
            inputMode="numeric"
            maxLength={6}
            value={draft.pincode}
            onChange={(e) => set("pincode", e.target.value)}
            className="h-11"
            aria-invalid={Boolean(error("pincode"))}
          />
        </Field>
      </div>
      {pinWarning && (
        <div
          data-testid="eh-pincode-warning"
          role="status"
          className="space-y-2 rounded-md bg-warn-100 p-3 text-sm text-[#6f4608]"
        >
          <p>{pinWarning}</p>
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => {
              keepPin.current = draft.pincode.trim()
              setPinWarning(null)
              form.current?.requestSubmit()
            }}
          >
            Keep this PIN
          </Button>
        </div>
      )}
      <HomeSizeChips
        idPrefix="eh"
        legend={<>Home size {optional}</>}
        value={draft.homeSize}
        other={draft.homeSizeOther}
        onChange={(size) => set("homeSize", size)}
        onOtherChange={(text) => set("homeSizeOther", text)}
        otherError={error("homeSizeOther")}
        error={error("homeSize") ?? error("bedrooms")}
      />
      {error("form") && <Notice tone="warn">{error("form")}</Notice>}
      {save.isError && !(isApiError(save.error) && save.error.fieldErrors.length > 0) && (
        <ActionError error={save.error} />
      )}
      {remove.isError && <ActionError error={remove.error} />}
      <div className="flex flex-wrap items-center gap-2">
        <Button type="submit" size="lg" disabled={save.isPending || checking} data-testid="eh-save">
          Save changes
        </Button>
        <Button asChild variant="outline" size="lg">
          <Link to={`/p/${property.id}`}>Cancel</Link>
        </Button>
        <Button
          type="button"
          variant="destructive"
          className="ml-auto"
          data-testid="eh-delete"
          disabled={remove.isPending}
          onClick={() => setConfirm("delete")}
        >
          <TrashIcon />
          Delete home
        </Button>
      </div>

      <ConfirmDialog
        open={confirm === "save"}
        onOpenChange={(open) => !open && setConfirm(null)}
        title="Save and remove the tick?"
        confirmLabel="Save changes"
        busy={save.isPending}
        onConfirm={() => save.mutate()}
      >
        <p>{VERIFIED_EDIT_WARNING}</p>
        <p>It shows as &ldquo;Not verified yet&rdquo; meanwhile.</p>
      </ConfirmDialog>
      <ConfirmDialog
        open={confirm === "delete"}
        onOpenChange={(open) => !open && setConfirm(null)}
        title="Delete this home?"
        confirmLabel="Delete home"
        danger
        busy={remove.isPending}
        onConfirm={() => remove.mutate()}
      >
        {verified && <p>{VERIFIED_DELETE_WARNING}</p>}
        <p>
          Your tenancies and reviews on it are removed too. A home where other people have records
          cannot be deleted. This cannot be undone.
        </p>
      </ConfirmDialog>
    </form>
  )
}

/** `/homes/:id/edit` (D-156): the person who added a home corrects its address or size, or deletes it. */
export default function EditHomePage() {
  const { id = "" } = useParams()
  const query = useProperty(id)

  let body: ReactNode
  if (query.isPending) body = <p className="text-sm text-ink-500">Loading the home…</p>
  else if (query.isError)
    body =
      isApiError(query.error) && query.error.status === 404 ? (
        <EmptyState icon="home" title="Home not found" />
      ) : (
        <ErrorState
          title="The home could not be loaded"
          error={query.error}
          onRetry={() => void query.refetch()}
        />
      )
  else if (!query.data.editable)
    body = (
      <EmptyState
        icon="home"
        title="You cannot change this home"
        description="Only the person who added a home can change it."
        action={
          <Button asChild variant="outline">
            <Link to={`/p/${id}`}>Back to the home</Link>
          </Button>
        }
      />
    )
  else body = <EditForm property={query.data} />

  return (
    <main>
      <Container className="max-w-[640px] space-y-6 py-8">
        <div className="space-y-2">
          <Link to={`/p/${id}`} className="text-sm text-ink-700">
            ← Back to the home
          </Link>
          <h1 className="font-display text-3xl font-semibold">
            Edit home{query.data ? ` · ${propertyTitle(query.data)}` : ""}
          </h1>
        </div>
        {body}
      </Container>
    </main>
  )
}
