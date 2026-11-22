import { useState, type FormEvent, type ReactNode } from "react"
import { useMutation } from "@tanstack/react-query"
import { Link, useNavigate, useSearchParams } from "react-router-dom"
import { createTenancy } from "@/api/tenancies"
import { isApiError } from "@/api/errors"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Notice } from "@/components/ui/notice"
import { EmptyState } from "@/components/ui/states"
import { useAuth } from "@/features/auth/authContext"
import { placeLine, propertyTitle } from "@/features/property/labels"
import { useProperty } from "@/features/property/useProperties"
import { ActionError } from "@/features/tenancy/ApprovalPrompt"
import { today, wholeNumber } from "@/features/tenancy/labels"
import {
  checkTenancyForm,
  friendlyFieldError,
  type TenancyField,
  type TenancyFieldErrors,
} from "@/features/tenancy/tenancyForm"
import { useMyProperties, useRefreshTenancyData } from "@/features/tenancy/useTenancies"
import { useSessionDraft } from "@/lib/draft"
import type { PropertyDetail, TenancyInput } from "@/types/api"

const selectClass = "h-10 w-full rounded-md border border-line-strong bg-surface px-3 text-sm"

function newKey(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

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

/**
 * Record a tenancy (`/tenancies/new`, DESIGN.md "record-a-tenancy form"). A landlord invites a
 * tenant by email from one of their listed homes (D-34); a tenant opens it from a property page
 * (`?property=`) and the landlord confirms within 14 days. Both sides then see it in
 * `/me/tenancies`. What the user typed is kept in the tab (`useSessionDraft`), so a detour to
 * verify the phone does not lose it.
 */
export default function TenancyRequestPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { state } = useAuth()
  const roles = state.user?.roles ?? []
  const isLandlord = roles.includes("LANDLORD")
  const mine = useMyProperties()
  const propertyParam = params.get("property") ?? ""
  const listed = (mine.data?.items ?? []).filter(
    (p) => p.status === "PUBLISHED" || p.status === "ARCHIVED",
  )
  const ownsParam = listed.some((p) => p.id === propertyParam)
  const asLandlord = isLandlord && (!propertyParam || ownsParam || !roles.includes("TENANT"))
  const target = useProperty(asLandlord ? "" : propertyParam)

  const [draft, setDraft, clearDraft] = useSessionDraft(`tenancy:${propertyParam}`, {
    propertyId: propertyParam,
    email: "",
    startDate: "",
    endDate: "",
    rent: "",
    deposit: "",
  })
  const { propertyId, email, startDate, endDate, rent, deposit } = draft
  const field = (name: keyof typeof draft) => (value: string) =>
    setDraft((current) => ({ ...current, [name]: value }))
  const setPropertyId = field("propertyId")
  const setEmail = field("email")
  const setStartDate = field("startDate")
  const setEndDate = field("endDate")
  const setRent = field("rent")
  const setDeposit = field("deposit")
  const [checked, setChecked] = useState<TenancyFieldErrors>({})
  const [idempotencyKey] = useState(newKey)
  const refresh = useRefreshTenancyData()

  const create = useMutation({
    mutationFn: (body: TenancyInput) => createTenancy(body, idempotencyKey),
    onSuccess: async (tenancy) => {
      clearDraft()
      await refresh()
      navigate(`/tenancies/${tenancy.id}`)
    },
  })
  const apiFieldErrors = isApiError(create.error) ? create.error.fieldErrors : []
  const fieldError = (field: TenancyField) => {
    const fromApi = apiFieldErrors.find((e) => e.field === field)
    return checked[field] ?? (fromApi ? friendlyFieldError(fromApi) : undefined)
  }
  // Field errors show under their fields; the notice then only points at them.
  const shownInline = apiFieldErrors.length > 0

  const chosen = asLandlord ? propertyId || listed[0]?.id || "" : propertyParam

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const errors = checkTenancyForm({ asLandlord, email, startDate, endDate, rent, deposit })
    setChecked(errors)
    if (Object.keys(errors).length > 0) {
      create.reset()
      return
    }
    const depositInr = wholeNumber(deposit)
    create.mutate({
      propertyId: chosen,
      ...(asLandlord ? { counterpartyEmail: email.trim() } : {}),
      startDate,
      ...(endDate ? { endDate } : {}),
      initialRentInr: wholeNumber(rent)!,
      ...(depositInr !== undefined ? { depositInr } : {}),
    })
  }

  if (!asLandlord && !propertyParam) {
    return (
      <main>
        <Container className="py-12">
          <EmptyState
            icon="tenancy"
            title="Start from the home you rent"
            description="Find the home in search, open it and choose “I rent this home”."
            action={
              <Button asChild>
                <Link to="/search">Search homes</Link>
              </Button>
            }
          />
        </Container>
      </main>
    )
  }

  if (asLandlord && mine.isSuccess && listed.length === 0) {
    return (
      <main>
        <Container className="py-12">
          <EmptyState
            icon="home"
            title="No listed property yet"
            description="A tenant can be invited once a property is published. Add one and submit it for review."
            action={
              <Button asChild>
                <Link to="/me/properties/new">Add property</Link>
              </Button>
            }
          />
        </Container>
      </main>
    )
  }

  const home: PropertyDetail | undefined = asLandlord
    ? listed.find((p) => p.id === chosen)
    : target.data

  return (
    <main>
      <Container className="max-w-[720px] space-y-6 py-8">
        <div>
          <h1 className="font-display text-3xl font-semibold text-ink-900">
            {asLandlord ? "Invite a tenant" : "Record your tenancy"}
          </h1>
          <p className="mt-1 text-ink-500">
            {asLandlord
              ? "The tenant gets an email, signs in and confirms. Nothing is public until both sides confirm."
              : "The landlord confirms within 14 days, or the request expires. Nothing is public until both sides confirm."}
          </p>
        </div>

        <form
          noValidate
          onSubmit={onSubmit}
          aria-label="Tenancy request"
          className="space-y-4 rounded-xl border border-line bg-surface p-6 shadow-sm"
        >
          {asLandlord ? (
            <Field
              id="tr-property"
              label="Property"
              error={fieldError("propertyId")}
              hint="Published or archived homes only."
            >
              <select
                id="tr-property"
                className={selectClass}
                value={chosen}
                onChange={(e) => setPropertyId(e.target.value)}
              >
                {listed.map((p) => (
                  <option key={p.id} value={p.id}>
                    {propertyTitle(p)}
                    {p.unitLabel ? ` · ${p.unitLabel}` : ""} · {p.locality}
                  </option>
                ))}
              </select>
            </Field>
          ) : (
            <div className="rounded-lg border border-line bg-sunk/40 p-4">
              <p className="text-xs text-ink-500">Home</p>
              <p className="font-semibold text-ink-900">
                {home ? propertyTitle(home) : target.isError ? "Home not found" : "Loading…"}
              </p>
              {home && <p className="text-sm text-ink-500">{placeLine(home)}</p>}
            </div>
          )}

          {asLandlord && (
            <Field
              id="tr-email"
              label="Tenant email"
              error={fieldError("counterpartyEmail")}
              hint="Never shown to anyone else. You see it masked afterwards."
            >
              <Input
                id="tr-email"
                type="email"
                aria-required="true"
                aria-invalid={fieldError("counterpartyEmail") ? true : undefined}
                autoComplete="off"
                value={email}
                placeholder="name@example.com"
                onChange={(e) => setEmail(e.target.value)}
                className="h-10"
              />
            </Field>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <Field id="tr-start" label="Start date" error={fieldError("startDate")}>
              <Input
                id="tr-start"
                type="date"
                aria-required="true"
                aria-invalid={fieldError("startDate") ? true : undefined}
                max={today()}
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="h-10"
              />
            </Field>
            <Field
              id="tr-end"
              label={
                <>
                  End date <span className="font-normal text-ink-500">(only if it ended)</span>
                </>
              }
              error={fieldError("endDate")}
            >
              <Input
                id="tr-end"
                type="date"
                max={today()}
                min={startDate || undefined}
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="h-10"
              />
            </Field>
            <Field id="tr-rent" label="Monthly rent (₹)" error={fieldError("initialRentInr")}>
              <Input
                id="tr-rent"
                inputMode="numeric"
                aria-required="true"
                aria-invalid={fieldError("initialRentInr") ? true : undefined}
                value={rent}
                onChange={(e) => setRent(e.target.value)}
                className="h-10"
              />
            </Field>
            <Field
              id="tr-deposit"
              label={
                <>
                  Deposit (₹) <span className="font-normal text-ink-500">(optional)</span>
                </>
              }
              error={fieldError("depositInr")}
            >
              <Input
                id="tr-deposit"
                inputMode="numeric"
                value={deposit}
                onChange={(e) => setDeposit(e.target.value)}
                className="h-10"
              />
            </Field>
          </div>

          <Notice tone="info" icon="lock">
            Dates, deposit and the flat number stay private to the two of you. Only the monthly
            rent, by month, joins the home&apos;s rent history once confirmed.
          </Notice>
          <Notice tone="info" icon="document">
            Rental agreement upload (private proof) comes with document uploads.
          </Notice>

          {create.isError &&
            (shownInline ? (
              <Notice tone="danger">Some details need a fix: see the messages above.</Notice>
            ) : (
              <ActionError error={create.error} />
            ))}
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={create.isPending || !chosen}>
              {create.isPending ? "Sending…" : asLandlord ? "Send invite" : "Send to landlord"}
            </Button>
            <Button type="button" variant="ghost" onClick={() => navigate(-1)}>
              Cancel
            </Button>
          </div>
        </form>
      </Container>
    </main>
  )
}
