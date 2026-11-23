import { useState, type FormEvent, type ReactNode } from "react"
import { useMutation } from "@tanstack/react-query"
import { Link, useNavigate, useParams } from "react-router-dom"
import { deleteTenancy, updateTenancy } from "@/api/tenancies"
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
  checkTenancyDraft,
  rentOptional,
  tenancyDraft,
  tenancyErrorKey,
  toTenancyUpdate,
  type TenancyDraft,
} from "@/features/edit/tenancyEdit"
import {
  needsWarning,
  VERIFIED_DELETE_WARNING,
  VERIFIED_EDIT_WARNING,
} from "@/features/edit/verified"
import type { HomeErrors } from "@/features/home/addHome"
import { ActionError } from "@/features/tenancy/ApprovalPrompt"
import { tenancyHome, today } from "@/features/tenancy/labels"
import { useRefreshTenancyData, useTenancy } from "@/features/tenancy/useTenancies"
import type { Tenancy } from "@/types/api"

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

function EditForm({ tenancy }: { tenancy: Tenancy }) {
  const navigate = useNavigate()
  const refresh = useRefreshTenancyData()
  const [draft, setDraft] = useState<TenancyDraft>(() => tenancyDraft(tenancy))
  const [errors, setErrors] = useState<HomeErrors>({})
  const [confirm, setConfirm] = useState<null | "save" | "delete">(null)
  const verified = needsWarning(tenancy.verificationStatus)

  const set = <K extends keyof TenancyDraft>(key: K, value: TenancyDraft[K]) => {
    setDraft((d) => ({ ...d, [key]: value }))
    const stale = key === "stillLiving" ? "endDate" : key
    setErrors((e) => ({ ...e, [stale]: undefined, form: undefined }))
  }

  const save = useMutation({
    mutationFn: () => updateTenancy(tenancy.id, toTenancyUpdate(draft)),
    onSuccess: async (saved) => {
      await refresh()
      navigate(`/tenancies/${saved.id}`, { state: { saved: true } })
    },
    onError: (error) => {
      setConfirm(null)
      if (!isApiError(error) || error.fieldErrors.length === 0) return
      const next: HomeErrors = {}
      for (const { field, message } of error.fieldErrors) next[tenancyErrorKey(field)] ??= message
      setErrors(next)
    },
  })
  const remove = useMutation({
    mutationFn: () => deleteTenancy(tenancy.id),
    onSuccess: async () => {
      await refresh()
      navigate("/me/tenancies", { state: { deleted: true } })
    },
    onError: () => setConfirm(null),
  })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const found = checkTenancyDraft(draft, tenancy, today())
    if (Object.values(found).some(Boolean)) {
      setErrors(found)
      return
    }
    if (verified) setConfirm("save")
    else save.mutate()
  }

  return (
    <form noValidate onSubmit={submit} className="space-y-5" data-testid="edit-tenancy-form">
      {verified ? (
        <Notice tone="warn" icon="shield-check" data-testid="edit-verified-note">
          This tenancy is verified. {VERIFIED_EDIT_WARNING}
        </Notice>
      ) : (
        <Notice tone="info">Not verified yet, so you can change or delete it freely.</Notice>
      )}
      <Field id="et-start" label="Moved in" error={errors.startDate}>
        <Input
          id="et-start"
          type="date"
          value={draft.startDate}
          max={today()}
          onChange={(e) => set("startDate", e.target.value)}
          className="h-11"
        />
      </Field>
      <label className="flex items-center gap-2 text-sm text-ink-900">
        <input
          type="checkbox"
          data-testid="et-still-living"
          checked={draft.stillLiving}
          onChange={(e) => set("stillLiving", e.target.checked)}
          className="size-4 accent-teal-600"
        />
        I still live here
      </label>
      {!draft.stillLiving && (
        <Field id="et-end" label="Moved out" error={errors.endDate}>
          <Input
            id="et-end"
            type="date"
            value={draft.endDate}
            max={today()}
            min={draft.startDate}
            onChange={(e) => set("endDate", e.target.value)}
            className="h-11"
          />
        </Field>
      )}
      <Field
        id="et-rent"
        label={<>Monthly rent (₹) {rentOptional(tenancy) && optional}</>}
        error={errors.rent}
      >
        <Input
          id="et-rent"
          inputMode="numeric"
          value={draft.rent}
          onChange={(e) => set("rent", e.target.value)}
          className="h-11"
        />
      </Field>
      <Field id="et-deposit" label={<>Deposit (₹) {optional}</>} error={errors.deposit}>
        <Input
          id="et-deposit"
          inputMode="numeric"
          value={draft.deposit}
          onChange={(e) => set("deposit", e.target.value)}
          className="h-11"
        />
      </Field>
      {errors.form && <Notice tone="warn">{errors.form}</Notice>}
      {save.isError && !(isApiError(save.error) && save.error.fieldErrors.length > 0) && (
        <ActionError error={save.error} startDate={draft.startDate} />
      )}
      {remove.isError && <ActionError error={remove.error} />}
      <div className="flex flex-wrap items-center gap-2">
        <Button type="submit" size="lg" disabled={save.isPending} data-testid="et-save">
          Save changes
        </Button>
        <Button asChild variant="outline" size="lg">
          <Link to={`/tenancies/${tenancy.id}`}>Cancel</Link>
        </Button>
        {tenancy.actions.delete && (
          <Button
            type="button"
            variant="destructive"
            className="ml-auto"
            data-testid="et-delete"
            disabled={remove.isPending}
            onClick={() => setConfirm("delete")}
          >
            <TrashIcon />
            Delete tenancy
          </Button>
        )}
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
        title="Delete this tenancy?"
        confirmLabel="Delete tenancy"
        danger
        busy={remove.isPending}
        onConfirm={() => remove.mutate()}
      >
        {verified && <p>{VERIFIED_DELETE_WARNING}</p>}
        <p>
          It leaves the home&apos;s rent history, and your reviews on it are withdrawn. This cannot
          be undone.
        </p>
      </ConfirmDialog>
    </form>
  )
}

/** `/tenancies/:id/edit` (D-156): the party who added a tenancy corrects or deletes it. */
export default function EditTenancyPage() {
  const { id = "" } = useParams()
  const query = useTenancy(id)

  let body: ReactNode
  if (query.isPending) body = <p className="text-sm text-ink-500">Loading the tenancy…</p>
  else if (query.isError)
    body =
      isApiError(query.error) && query.error.status === 404 ? (
        <EmptyState icon="tenancy" title="Tenancy not found" />
      ) : (
        <ErrorState
          title="The tenancy could not be loaded"
          error={query.error}
          onRetry={() => void query.refetch()}
        />
      )
  else if (!query.data.actions.edit)
    body = (
      <EmptyState
        icon="tenancy"
        title="You cannot change this tenancy"
        description="Only the person who added a tenancy can change it."
        action={
          <Button asChild variant="outline">
            <Link to={`/tenancies/${id}`}>Back to the tenancy</Link>
          </Button>
        }
      />
    )
  else body = <EditForm tenancy={query.data} />

  return (
    <main>
      <Container className="max-w-[640px] space-y-6 py-8">
        <div className="space-y-2">
          <Link to={`/tenancies/${id}`} className="text-sm text-ink-700">
            ← Back to the tenancy
          </Link>
          <h1 className="font-display text-3xl font-semibold">
            Edit tenancy{query.data ? ` · ${tenancyHome(query.data)}` : ""}
          </h1>
        </div>
        {body}
      </Container>
    </main>
  )
}
