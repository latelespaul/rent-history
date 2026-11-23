import { useState, type FormEvent, type ReactNode } from "react"
import { useMutation, useQuery } from "@tanstack/react-query"
import { Link, useParams, useSearchParams } from "react-router-dom"
import {
  agreementKeys,
  confirmAgreement,
  correctAgreement,
  fetchTenancyAgreement,
  uploadAgreement,
} from "@/api/agreements"
import { isApiError } from "@/api/errors"
import { Container } from "@/components/layout/Container"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { CheckIcon, ClockIcon, LockIcon, ShieldCheckIcon } from "@/components/ui/icons.gen"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Notice } from "@/components/ui/notice"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { rupees } from "@/features/property/labels"
import { useSignedIn } from "@/features/property/useProperties"
import { ActionError } from "@/features/tenancy/ApprovalPrompt"
import {
  checkAgreementForm,
  CONSENT_TEXT,
  EMPTY_AGREEMENT_FORM,
  FILE_ACCEPT,
  toFields,
  toForm,
  type AgreementErrors,
  type AgreementForm,
} from "@/features/tenancy/agreementForm"
import { dateLabel, tenancyHome, today } from "@/features/tenancy/labels"
import { rejectionText, reviewHref, reviewStep } from "@/features/tenancy/stepState"
import { useRefreshTenancyData, useReviewTasks, useTenancy } from "@/features/tenancy/useTenancies"
import { BRAND } from "@/lib/brand"
import type { RentalAgreement, Tenancy } from "@/types/api"

function Field({
  id,
  label,
  hint,
  error,
  children,
}: {
  id: string
  label: ReactNode
  hint?: ReactNode
  error?: string
  children: ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? (
        <p className="text-xs text-danger-600" data-testid={`${id}-error`}>
          {error}
        </p>
      ) : (
        hint && <p className="text-xs text-ink-500">{hint}</p>
      )}
    </div>
  )
}

const optional = <span className="font-normal text-ink-500">(optional)</span>

const STEP_TITLES = ["The file", "Its details", "Check and confirm"] as const

function Steps({ index }: { index: number }) {
  return (
    <div>
      <p className="text-xs font-semibold tracking-wide text-teal-700 uppercase">
        Upload your rental agreement
      </p>
      <h1
        data-testid="agreement-step"
        className="mt-1 font-display text-2xl font-semibold text-ink-900"
      >
        Step {index + 1} of {STEP_TITLES.length} · {STEP_TITLES[index]}
      </h1>
      <ol className="mt-3 flex gap-1.5" aria-label="Steps">
        {STEP_TITLES.map((s, i) => (
          <li
            key={s}
            aria-current={i === index ? "step" : undefined}
            className={`h-1.5 flex-1 rounded-full ${i <= index ? "bg-teal-600" : "bg-line"}`}
          />
        ))}
      </ol>
    </div>
  )
}

/** One stored value on the check screen; empty ones say so. */
function Fact({ label, value, testId }: { label: string; value: ReactNode; testId?: string }) {
  return (
    <div className="flex flex-wrap justify-between gap-x-4 gap-y-0.5 border-b border-line py-2 last:border-0">
      <dt className="text-sm text-ink-500">{label}</dt>
      <dd className="text-sm font-semibold break-words text-ink-900" data-testid={testId}>
        {value === null || value === undefined || value === "" ? (
          <span className="font-normal text-ink-500">Not given</span>
        ) : (
          value
        )}
      </dd>
    </div>
  )
}

/** Errors the user can act on next to a field never repeat as a banner (release gate). */
function NonFieldError({ error }: { error: unknown }) {
  if (!error) return null
  if (isApiError(error) && error.fieldErrors.length > 0) return null
  return <ActionError error={error} />
}

/** Step 3: what was stored, masked, for the uploader to confirm (D-127, D-131). */
function Check({
  agreement,
  onEdit,
  onConfirmed,
}: {
  agreement: RentalAgreement
  onEdit: () => void
  onConfirmed: (a: RentalAgreement) => void
}) {
  const confirm = useMutation({
    mutationFn: () => confirmAgreement(agreement.id),
    onSuccess: onConfirmed,
  })
  const f = agreement.fields
  return (
    <section
      data-testid="agreement-check"
      className="space-y-4 rounded-xl border border-line bg-surface p-5 shadow-sm"
    >
      <p className="text-sm text-ink-700">
        This is what we stored from your agreement. The team compares it with the file.
      </p>
      {agreement.aadhaarMasked && (
        <Notice tone="teal" data-testid="aadhaar-masked">
          We masked an Aadhaar number you typed. Only its last 4 digits are kept.
        </Notice>
      )}
      <dl>
        <Fact label="Owner" value={f.landlordName} testId="check-landlord" />
        <Fact label="Tenant" value={f.tenantName} testId="check-tenant" />
        <Fact label="Address" value={f.addressText} testId="check-address" />
        <Fact label="PIN code" value={f.pincode} />
        <Fact label="Monthly rent" value={rupees(f.monthlyRentInr)} />
        <Fact label="Deposit" value={f.depositInr != null ? rupees(f.depositInr) : null} />
        <Fact
          label="Period"
          value={`${dateLabel(f.startDate)} – ${f.endDate ? dateLabel(f.endDate) : "no end date"}`}
        />
        <Fact label="Stamp or registration no." value={f.anchorNumber} testId="check-anchor" />
        <Fact
          label="File"
          value={`${agreement.contentType.replace("application/", "").replace("image/", "").toUpperCase()} · ${Math.max(1, Math.round(agreement.sizeBytes / 1024))} KB`}
        />
      </dl>
      <p className="inline-flex items-center gap-1.5 text-xs text-ink-500">
        <LockIcon size={12} />
        Stored encrypted. Only the other party of this tenancy and the {BRAND.name} team can open
        it.
      </p>
      <NonFieldError error={confirm.error} />
      <Button
        size="lg"
        className="w-full"
        disabled={confirm.isPending}
        onClick={() => confirm.mutate()}
        data-testid="agreement-confirm"
      >
        {confirm.isPending ? "Sending…" : "Confirm and send for the team's check"}
      </Button>
      <Button variant="outline" className="w-full" onClick={onEdit} data-testid="agreement-edit">
        Change the details
      </Button>
    </section>
  )
}

/** After the confirm: "Agreement uploaded · awaiting team check", then the review if it is next. */
function Done({ tenancy, goReview }: { tenancy: Tenancy; goReview: boolean }) {
  const tasks = useReviewTasks(tenancy.myRole === "TENANT")
  const review = reviewStep(tenancy, tasks.data, today())
  return (
    <section
      data-testid="agreement-done"
      className="space-y-4 rounded-xl border border-line bg-surface p-6 text-center shadow-sm"
    >
      <span className="mx-auto grid size-14 place-items-center rounded-full bg-success-100 text-success-600">
        <CheckIcon size={30} />
      </span>
      <h1 className="font-display text-2xl font-semibold text-ink-900">Agreement uploaded</h1>
      <p>
        <Badge variant="info" data-testid="agreement-status">
          <ClockIcon />
          Agreement uploaded · awaiting team check
        </Badge>
      </p>
      <p className="text-sm text-ink-700">
        The {BRAND.name} team checks it and gives your tenancy the green tick &ldquo;Verified by{" "}
        {BRAND.name}&rdquo;. Until then it shows as not verified yet.
      </p>
      {review.state === "todo" && (
        <div className="space-y-1 text-left">
          <p className="text-sm font-semibold text-ink-900">
            {goReview ? "Next: 1 optional step left" : "1 optional step left"}
          </p>
          <Button asChild size="lg" className="w-full" data-testid="agreement-next-review">
            <Link to={reviewHref(tenancy.id)}>Rate and review this home</Link>
          </Button>
        </div>
      )}
      <Button asChild variant="outline" className="w-full" data-testid="agreement-view-home">
        <Link to={`/p/${tenancy.property.id}`}>See the home&rsquo;s page</Link>
      </Button>
      <Button asChild variant="ghost" className="w-full">
        <Link to="/me/tenancies">{review.state === "todo" ? "Skip for now" : "My tenancies"}</Link>
      </Button>
    </section>
  )
}

/**
 * `/tenancies/:id/agreement`, "Upload your rental agreement" (D-123 – D-131; captain 2026-11-23
 * 14:53): 1) the file, after a warning to cover Aadhaar numbers, and the explicit consent; 2) its
 * key fields, typed from the document (manual extractor, D-127); 3) what was stored, Aadhaar
 * numbers masked, to confirm. Confirming sends it to the team ("Agreement uploaded · awaiting team
 * check"); the review is offered next. Parties of the tenancy only.
 */
export default function AgreementUploadPage() {
  const { id = "" } = useParams()
  const [params] = useSearchParams()
  const goReview = params.get("next") === "review"
  const tenancy = useTenancy(id)
  const { ready, viewer } = useSignedIn()
  const refresh = useRefreshTenancyData()
  const existing = useQuery({
    queryKey: agreementKeys.forTenancy(id, viewer),
    queryFn: ({ signal }) => fetchTenancyAgreement(id, signal),
    enabled: ready && Boolean(id),
    retry: false,
  })

  const [file, setFile] = useState<File | null>(null)
  const [consent, setConsent] = useState(false)
  const [form, setForm] = useState<AgreementForm>(EMPTY_AGREEMENT_FORM)
  const [errors, setErrors] = useState<AgreementErrors>({})
  const [step, setStep] = useState<0 | 1 | 2 | 3>(0)
  const [stored, setStored] = useState<RentalAgreement | null>(null)
  const [editing, setEditing] = useState(false)

  // A draft the user left half-way opens on the check step.
  const draft =
    stored ??
    (existing.data?.status === "DRAFT" && existing.data.uploadedByMe ? existing.data : null)
  const current = step === 0 && draft && !editing ? 2 : step

  const upload = useMutation({
    mutationFn: () =>
      uploadAgreement({ tenancyId: id, consent, fields: toFields(form), file: file! }),
    onSuccess: (saved) => {
      setStored(saved)
      setEditing(false)
      setStep(2)
      window.scrollTo?.({ top: 0 })
    },
  })
  const correct = useMutation({
    mutationFn: (agreementId: string) => correctAgreement(agreementId, toFields(form)),
    onSuccess: (saved) => {
      setStored(saved)
      setEditing(false)
      setStep(2)
      window.scrollTo?.({ top: 0 })
    },
  })
  const sending = upload.isPending || correct.isPending
  const sendError = upload.error ?? correct.error

  if (tenancy.isPending) {
    return (
      <main>
        <Container className="py-12">
          <p className="text-sm text-ink-500">Loading the tenancy…</p>
        </Container>
      </main>
    )
  }
  if (tenancy.isError) {
    return (
      <main>
        <Container className="py-12">
          {isApiError(tenancy.error) && tenancy.error.status === 404 ? (
            <EmptyState
              icon="tenancy"
              title="Tenancy not found"
              description="Only the landlord and tenant of a tenancy can upload its agreement."
            />
          ) : (
            <ErrorState error={tenancy.error} onRetry={() => void tenancy.refetch()} />
          )}
        </Container>
      </main>
    )
  }
  const t = tenancy.data

  if (current === 3) {
    return (
      <main>
        <Container className="max-w-[560px] py-8">
          <Done tenancy={t} goReview={goReview} />
        </Container>
      </main>
    )
  }

  if (t.verificationStatus === "VERIFIED") {
    return (
      <main>
        <Container className="max-w-[560px] py-8">
          <Notice tone="teal" icon="shield-check" data-testid="agreement-verified">
            <p className="font-semibold">Verified by {BRAND.name}</p>
            <p className="mt-1">The team already verified this tenancy from its agreement.</p>
          </Notice>
        </Container>
      </main>
    )
  }

  const awaiting = existing.data && existing.data.status === "SUBMITTED" && !stored && !editing

  const fieldError = (key: string) =>
    errors[key] ?? (isApiError(sendError) ? sendError.fieldError(key) : undefined)
  const set = (key: keyof AgreementForm, value: string) => setForm((f) => ({ ...f, [key]: value }))

  const onFile = (event: FormEvent) => {
    event.preventDefault()
    const found = checkAgreementForm(form, file, consent)
    const fileOnly = Object.fromEntries(
      Object.entries(found).filter(([k]) => k === "file" || k === "consent"),
    )
    setErrors(fileOnly)
    if (Object.keys(fileOnly).length === 0) {
      setStep(1)
      window.scrollTo?.({ top: 0 })
    }
  }

  const onDetails = (event: FormEvent) => {
    event.preventDefault()
    const found = checkAgreementForm(form, file, consent, !draft)
    setErrors(found)
    if (Object.keys(found).length > 0) return
    if (draft) correct.mutate(draft.id)
    else upload.mutate()
  }

  return (
    <main>
      <Container className="max-w-[560px] space-y-5 py-6 nav:py-10">
        <Link to={`/tenancies/${t.id}`} className="text-sm text-ink-700">
          ← {tenancyHome(t)}
        </Link>
        <Steps index={current} />

        {awaiting && (
          <Notice tone="info" icon="clock" data-testid="agreement-awaiting">
            <p className="font-semibold">Agreement uploaded · awaiting team check</p>
            <p className="mt-1">
              {existing.data?.uploadedByMe ? "You" : "The other party"} uploaded it on{" "}
              {dateLabel(existing.data?.uploadedAt)}. A new upload replaces it.
            </p>
          </Notice>
        )}
        {t.verificationStatus === "REJECTED" && t.verificationReason && (
          <Notice tone="warn" data-testid="agreement-rejected">
            <p className="font-semibold">The team could not verify the last upload</p>
            <p className="mt-1">{rejectionText(t.verificationReason)}</p>
          </Notice>
        )}

        {current === 0 && (
          <form
            noValidate
            onSubmit={onFile}
            aria-label="Agreement upload"
            className="space-y-4 rounded-xl border border-line bg-surface p-5 shadow-sm"
          >
            <p className="text-sm text-ink-700">
              The team verifies your tenancy from the agreement and gives it the green tick. One
              file: a PDF, or a photo (JPEG, PNG, HEIC), up to 10 MB. Several pages? Your phone can
              combine them into one PDF.
            </p>
            <Notice tone="warn" icon="shield-check" data-testid="cover-aadhaar">
              <p className="font-semibold">Cover any Aadhaar number first</p>
              <p className="mt-1">
                Many agreements show the Aadhaar numbers, photos and thumbprints of both sides.
                Cover them with paper before you scan or take the photo.
              </p>
            </Notice>
            <Field id="ag-file" label="Agreement file" error={fieldError("file")}>
              <input
                id="ag-file"
                type="file"
                accept={FILE_ACCEPT}
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                className="block w-full text-sm text-ink-700 file:mr-3 file:h-11 file:rounded-md file:border file:border-line-strong file:bg-surface file:px-4 file:font-semibold"
              />
            </Field>
            <div className="space-y-1.5">
              <label className="flex items-start gap-2.5 text-sm text-ink-900">
                <input
                  id="ag-consent"
                  type="checkbox"
                  checked={consent}
                  onChange={(e) => setConsent(e.target.checked)}
                  className="mt-0.5 size-[18px] shrink-0 accent-teal-600"
                  data-testid="agreement-consent"
                />
                <span>{CONSENT_TEXT}</span>
              </label>
              {fieldError("consent") && (
                <p className="text-xs text-danger-600" data-testid="ag-consent-error">
                  {fieldError("consent")}
                </p>
              )}
            </div>
            <Button type="submit" size="lg" className="w-full" data-testid="agreement-next">
              Next: its details
            </Button>
            <Button asChild variant="ghost" className="w-full">
              <Link to="/me/tenancies">Skip for now</Link>
            </Button>
          </form>
        )}

        {current === 1 && (
          <form
            noValidate
            onSubmit={onDetails}
            aria-label="Agreement details"
            className="space-y-4 rounded-xl border border-line bg-surface p-5 shadow-sm"
          >
            <p className="text-sm text-ink-700">
              Type these from the agreement, as it shows them. Never type a full Aadhaar or PAN
              number.
            </p>
            <Field id="ag-landlord" label="Owner's name" error={fieldError("fields.landlordName")}>
              <Input
                id="ag-landlord"
                value={form.landlordName}
                maxLength={120}
                autoComplete="off"
                onChange={(e) => set("landlordName", e.target.value)}
                className="h-11"
              />
            </Field>
            <Field id="ag-tenant" label="Tenant's name" error={fieldError("fields.tenantName")}>
              <Input
                id="ag-tenant"
                value={form.tenantName}
                maxLength={120}
                autoComplete="off"
                onChange={(e) => set("tenantName", e.target.value)}
                className="h-11"
              />
            </Field>
            <Field
              id="ag-address"
              label={<>Address of the home {optional}</>}
              error={fieldError("fields.addressText")}
            >
              <Input
                id="ag-address"
                value={form.addressText}
                maxLength={500}
                onChange={(e) => set("addressText", e.target.value)}
                className="h-11"
              />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field
                id="ag-pin"
                label={<>PIN code {optional}</>}
                error={fieldError("fields.pincode")}
              >
                <Input
                  id="ag-pin"
                  inputMode="numeric"
                  maxLength={6}
                  value={form.pincode}
                  onChange={(e) => set("pincode", e.target.value)}
                  className="h-11"
                />
              </Field>
              <Field
                id="ag-rent"
                label="Monthly rent (₹)"
                error={fieldError("fields.monthlyRentInr")}
              >
                <Input
                  id="ag-rent"
                  inputMode="numeric"
                  value={form.monthlyRent}
                  onChange={(e) => set("monthlyRent", e.target.value)}
                  className="h-11"
                />
              </Field>
              <Field
                id="ag-deposit"
                label={<>Deposit (₹) {optional}</>}
                error={fieldError("fields.depositInr")}
              >
                <Input
                  id="ag-deposit"
                  inputMode="numeric"
                  value={form.deposit}
                  onChange={(e) => set("deposit", e.target.value)}
                  className="h-11"
                />
              </Field>
              <Field id="ag-start" label="Start date" error={fieldError("fields.startDate")}>
                <Input
                  id="ag-start"
                  type="date"
                  value={form.startDate}
                  onChange={(e) => set("startDate", e.target.value)}
                  className="h-11"
                />
              </Field>
              <Field
                id="ag-end"
                label={<>End date {optional}</>}
                error={fieldError("fields.endDate")}
              >
                <Input
                  id="ag-end"
                  type="date"
                  value={form.endDate}
                  onChange={(e) => set("endDate", e.target.value)}
                  className="h-11"
                />
              </Field>
            </div>
            <Field
              id="ag-anchor"
              label={<>Stamp or registration number {optional}</>}
              hint="Pune and Mumbai: the registration document number, like 4567/2025. Bangalore: the e-stamp certificate number, like IN-KA…. Leave it empty if the agreement has none."
              error={fieldError("fields.anchorNumber")}
            >
              <Input
                id="ag-anchor"
                value={form.anchorNumber}
                maxLength={64}
                autoComplete="off"
                onChange={(e) => set("anchorNumber", e.target.value)}
                className="h-11"
              />
            </Field>
            {fieldError("file") && (
              <p className="text-sm text-danger-600" data-testid="ag-file-error">
                {fieldError("file")}{" "}
                <button type="button" className="underline" onClick={() => setStep(0)}>
                  Choose another file
                </button>
              </p>
            )}
            <NonFieldError error={sendError} />
            <Button
              type="submit"
              size="lg"
              className="w-full"
              disabled={sending}
              data-testid="agreement-upload"
            >
              {sending ? "Uploading…" : draft ? "Save the details" : "Upload agreement"}
            </Button>
            <Button
              type="button"
              variant="outline"
              className="w-full"
              onClick={() => {
                if (draft) {
                  setEditing(false)
                  setStep(2)
                } else setStep(0)
              }}
            >
              Back
            </Button>
          </form>
        )}

        {current === 2 && draft && (
          <Check
            agreement={draft}
            onEdit={() => {
              setForm(toForm(draft))
              setEditing(true)
              setErrors({})
              setStep(1)
            }}
            onConfirmed={(confirmed) => {
              setStored(confirmed)
              setStep(3)
              void refresh()
              window.scrollTo?.({ top: 0 })
            }}
          />
        )}

        <p className="inline-flex items-center gap-1.5 text-xs text-ink-500">
          <ShieldCheckIcon size={12} />
          Optional. Your tenancy works without it; it only adds the team&rsquo;s green tick.
        </p>
      </Container>
    </main>
  )
}
