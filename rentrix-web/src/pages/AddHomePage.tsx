import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Link, useSearchParams } from "react-router-dom"
import { addHome, checkAddress, fetchHomeMatches } from "@/api/homes"
import { isApiError } from "@/api/errors"
import { Container } from "@/components/layout/Container"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { CheckIcon, LockIcon } from "@/components/ui/icons.gen"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Notice } from "@/components/ui/notice"
import { useAuth } from "@/features/auth/authContext"
import { useCities } from "@/features/city/useCities"
import {
  checkStep,
  EMPTY_HOME,
  matchQuery,
  OTHER_CITY,
  placeFieldErrors,
  standalone,
  STEP_TITLES,
  stepsFor,
  toAddHomeBody,
  type HomeDraft,
  type HomeErrors,
} from "@/features/home/addHome"
import { NextSteps } from "@/features/home/NextSteps"
import { stepsLeft, stepsLeftLabel } from "@/features/home/steps"
import { LocationPickers } from "@/features/landlord/LocationPickers"
import { EMPTY_DRAFT, type LocationDraft } from "@/features/landlord/locationDraft"
import { placeLine, propertyTitle, rupees, typeLabel, unitLine } from "@/features/property/labels"
import { ActionError } from "@/features/tenancy/ApprovalPrompt"
import { today } from "@/features/tenancy/labels"
import { useRefreshTenancyData } from "@/features/tenancy/useTenancies"
import { BRAND } from "@/lib/brand"
import { useSessionDraft } from "@/lib/draft"
import type { AddHomeResult, PropertyCard, PropertyType } from "@/types/api"

const selectClass = "h-11 w-full rounded-md border border-line-strong bg-surface px-3 text-base"
const TYPES: PropertyType[] = ["APARTMENT", "INDEPENDENT_HOUSE", "VILLA", "STUDIO", "ROOM", "PG"]

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

/** The input that shows each form error, focused when a save sends the user back to it. */
const FIELD_IDS: Record<string, string> = {
  city: "ah-city",
  otherCity: "ah-other-city",
  otherState: "ah-other-state",
  locality: "ah-locality",
  area: "pf-area",
  society: "pf-society",
  tower: "pf-tower",
  wing: "pf-wing",
  buildingName: "ah-building",
  unitLabel: "ah-unit",
  pincode: "ah-pincode",
  propertyType: "ah-type",
  bedrooms: "ah-bedrooms",
  relation: "ah-relation-rent",
  startDate: "ah-start",
  endDate: "ah-end",
  rent: "ah-rent",
  deposit: "ah-deposit",
  ownerName: "ah-owner-name",
  ownerEmail: "ah-owner-email",
  ownerPhone: "ah-owner-phone",
}

/** The form errors a draft field's change makes stale. */
const ERRORS_OF: Partial<Record<keyof HomeDraft, string[]>> = {
  location: ["area", "society", "tower", "wing"],
  propertyType: ["propertyType", "buildingName"],
  stillLiving: ["endDate"],
}

/** "Is it this one?" (D-139): homes that match the PIN code, society and flat number. */
function Matches({
  matches,
  onPick,
  onNone,
}: {
  matches: PropertyCard[]
  onPick: (id: string) => void
  onNone: () => void
}) {
  return (
    <div
      data-testid="home-matches"
      className="space-y-3 rounded-lg border border-teal-200 bg-teal-50 p-4"
    >
      <p className="font-semibold text-ink-900">Is it this one?</p>
      <p className="text-sm text-ink-700">
        This home may already be on {BRAND.name}. Pick it so your tenancy goes on the same page.
      </p>
      <ul className="space-y-2">
        {matches.map((home) => (
          <li key={home.id} className="rounded-md border border-line bg-surface p-3">
            <p className="font-medium text-ink-900">{propertyTitle(home)}</p>
            <p className="text-sm text-ink-500">
              {placeLine(home)} · {typeLabel(home.propertyType)}
            </p>
            <Button
              size="sm"
              className="mt-2"
              data-testid={`home-match-pick-${home.id}`}
              onClick={() => onPick(home.id)}
            >
              Yes, this is my home
            </Button>
          </li>
        ))}
      </ul>
      <Button variant="outline" size="sm" data-testid="home-match-none" onClick={onNone}>
        No, add mine as a new home
      </Button>
    </div>
  )
}

/** The saved home, with the optional review as the next step (D-139, captain 21:38 / 22:22). */
function Added({ result }: { result: AddHomeResult }) {
  const { property, tenancy } = result
  const left = tenancy ? stepsLeft(tenancy) : 0
  return (
    <section
      data-testid="add-home-done"
      className="rounded-xl border border-line bg-surface p-6 text-center shadow-sm"
    >
      <span className="mx-auto grid size-14 place-items-center rounded-full bg-success-100 text-success-600">
        <CheckIcon size={30} />
      </span>
      <h1 className="mt-3 font-display text-2xl font-semibold text-ink-900">Your home is added</h1>
      <p className="mt-1 text-ink-700">
        <b>{propertyTitle(property)}</b>
      </p>
      {unitLine(property.location, property.unitLabel) && (
        <p data-testid="added-unit-line" className="text-sm font-medium text-ink-700">
          {unitLine(property.location, property.unitLabel)}
        </p>
      )}
      <p className="text-sm text-ink-500">{placeLine(property)}</p>
      <p className="mt-3 flex flex-wrap justify-center gap-1.5">
        {property.addedByTenant && (
          <Badge data-testid="added-by-tenant">Added by a tenant · not verified yet</Badge>
        )}
        {tenancy && (
          <Badge variant="tenant">
            Your tenancy ·{" "}
            {tenancy.initialRentInr === null
              ? "Rent not shared"
              : `${rupees(tenancy.initialRentInr)}/mo`}
          </Badge>
        )}
      </p>
      <p className="mt-4 text-sm text-ink-700">
        It shows on the home&rsquo;s page now. The {BRAND.name} team verifies it later from your
        rental agreement.
      </p>
      {tenancy && left > 0 && (
        <div className="mt-5 space-y-3 text-left">
          <h2
            data-testid="add-home-steps-left"
            className="text-center text-lg font-semibold text-ink-900"
          >
            Home added. {stepsLeftLabel(left)}
          </h2>
          <NextSteps tenancy={tenancy} />
        </div>
      )}
      <Button asChild variant="outline" className="mt-4 w-full" data-testid="add-home-view">
        <Link to={`/p/${property.id}`}>See the home&rsquo;s page</Link>
      </Button>
      {left > 0 && (
        <Button asChild variant="ghost" className="mt-1 w-full" data-testid="add-home-skip">
          <Link to="/me/tenancies">Skip for now</Link>
        </Button>
      )}
    </section>
  )
}

/**
 * `/homes/new`, "Add your home" (D-139, D-142; captain 2026-11-22 21:38 and 22:22): one stepped,
 * mobile-first form for a home the user rents or rented, or owns. 1) where it is, through the
 * location hierarchy, with a new society typed in when it is not listed; a duplicate check offers
 * "Is it this one?". 2) I rent(ed) it / I own it. 3) the tenancy: from-to or "I still live here",
 * rent, deposit. 4) the owner's name and contact, private. Save stores the home, the tenancy and its
 * first rent at once, "not verified yet"; the review is offered next. What was typed stays in the
 * tab, so a detour to verify the account loses nothing.
 */
export default function AddHomePage() {
  const cities = useCities()
  const { acceptSession } = useAuth()
  const queryClient = useQueryClient()
  const refreshTenancies = useRefreshTenancyData()
  const [params] = useSearchParams()
  // From a home's page ("I rent or rented this home", D-142): that home, as its tenant.
  const fromPage = params.get("property")
  const [draft, setDraft, clearDraft] = useSessionDraft<HomeDraft>(
    fromPage ? `home:${fromPage}` : "home:new",
    fromPage
      ? { ...EMPTY_HOME, propertyId: fromPage, relation: "RENT" }
      : // A landlord's first job (`?own=1` from the welcome screen): "I own it" picked.
        { ...EMPTY_HOME, relation: params.get("own") ? "OWN" : null },
  )
  const [stepIndex, setStepIndex] = useState(fromPage ? 2 : 0)
  const [errors, setErrors] = useState<HomeErrors>({})
  const [matches, setMatches] = useState<PropertyCard[] | null>(null)
  const [checkedKey, setCheckedKey] = useState<string | null>(null)
  const [checking, setChecking] = useState(false)
  const [result, setResult] = useState<AddHomeResult | null>(null)
  const [pinWarning, setPinWarning] = useState<string | null>(null)
  const focusKey = useRef<string | null>(null)
  const keepPin = useRef<string | null>(null)
  const form = useRef<HTMLFormElement>(null)

  /** Clears the errors a change makes stale: no error outlives the value it was about. */
  const clearErrors = (key: keyof HomeDraft) => {
    const stale = ERRORS_OF[key] ?? [key]
    setErrors((current) =>
      stale.some((k) => k in current)
        ? Object.fromEntries(Object.entries(current).filter(([k]) => !stale.includes(k)))
        : current,
    )
  }

  const set = <K extends keyof HomeDraft>(key: K, value: HomeDraft[K]) => {
    clearErrors(key)
    setDraft((current) => ({ ...current, [key]: value }))
  }

  /** The PIN code, typed (`typed`) or picked from the area's list; a new one needs a new check. */
  const setPincode = (pincode: string, typed: boolean) => {
    clearErrors("pincode")
    setPinWarning(null)
    setDraft((current) => ({
      ...current,
      pincode,
      pincodeTyped: typed || current.pincodeTyped,
    }))
  }

  /** A new area fills in its main PIN code, unless the user typed one (firstmate 2026-11-23 14:51). */
  const setLocation = (location: LocationDraft) => {
    clearErrors("location")
    setDraft((current) => {
      const area = location.area
      const before = current.location.area
      const changed = area.kind === "pick" && (before.kind !== "pick" || before.id !== area.id)
      const main = area.kind === "pick" ? area.pincodes?.[0] : undefined
      if (!changed || !main || current.pincodeTyped) return { ...current, location }
      return { ...current, location, pincode: main }
    })
    setPinWarning(null)
  }

  // A save that sends the user back to a step focuses the field with the error.
  useEffect(() => {
    const key = focusKey.current
    if (!key) return
    focusKey.current = null
    document.getElementById(FIELD_IDS[key] ?? "")?.focus()
  }, [stepIndex, errors, pinWarning])

  const save = useMutation({
    mutationFn: () => addHome(toAddHomeBody(draft)),
    onSuccess: async (saved) => {
      if (saved.accessToken) await acceptSession({ accessToken: saved.accessToken })
      clearDraft()
      setResult(saved)
      await Promise.all([
        refreshTenancies(),
        queryClient.invalidateQueries({ queryKey: ["properties"] }),
      ])
      window.scrollTo?.({ top: 0 })
    },
    // A field error goes next to its field, on its step (#53 click-test bug 1), never as a bare
    // "invalid fields" message.
    onError: (failure) => {
      if (!isApiError(failure)) return
      const placed = placeFieldErrors(failure.fieldErrors, stepsFor(draft))
      if (!placed) return
      setErrors(placed.errors)
      setStepIndex(placed.step)
      focusKey.current = Object.keys(placed.errors)[0] ?? null
      window.scrollTo?.({ top: 0 })
    },
  })

  if (result) {
    return (
      <main>
        <Container className="max-w-[560px] py-8">
          <Added result={result} />
        </Container>
      </main>
    )
  }

  const steps = stepsFor(draft)
  const index = Math.min(stepIndex, steps.length - 1)
  const step = steps[index]
  const last = index === steps.length - 1
  const error = (field: string) => errors[field]
  // Field errors are shown by their fields only (see `onError`); never as a banner.
  const fieldFailure = isApiError(save.error) && save.error.fieldErrors.length > 0

  const go = (to: number) => {
    setErrors({})
    if (fieldFailure) save.reset()
    setStepIndex(to)
    window.scrollTo?.({ top: 0 })
  }

  const next = async (event: FormEvent) => {
    event.preventDefault()
    const found = checkStep(step, draft, today())
    setErrors(found)
    if (Object.keys(found).length > 0) return
    // Step 1's own server check (firstmate 2026-11-23 14:50): a PIN code outside the city blocks
    // here, on Next; one the picked area does not list only warns, and the user may keep it.
    if (step === "address" && !draft.propertyId && draft.city !== OTHER_CITY) {
      const pincode = draft.pincode.trim()
      const area = draft.location.area.kind === "pick" ? draft.location.area.id : undefined
      setChecking(true)
      try {
        const checked = await checkAddress({ city: draft.city, pincode, area })
        if (checked.pincodeError) {
          setErrors({ pincode: checked.pincodeError })
          focusKey.current = "pincode"
          return
        }
        const kept = keepPin.current === pincode || draft.keptPincode === pincode
        if (checked.pincodeWarning && !kept) {
          setPinWarning(checked.pincodeWarning)
          focusKey.current = "pincode"
          return
        }
        if (kept && draft.keptPincode !== pincode) set("keptPincode", pincode)
      } catch {
        // The save checks again; a failed check never blocks the form.
      } finally {
        setChecking(false)
      }
    }
    if (step === "address" && !draft.propertyId) {
      const query = matchQuery(draft)
      const key = JSON.stringify(query)
      if (query && key !== checkedKey) {
        setChecking(true)
        try {
          const homes = await fetchHomeMatches(query)
          setCheckedKey(key)
          if (homes.length > 0) {
            setMatches(homes)
            return
          }
        } catch {
          // The check only helps; a failed one never blocks adding the home.
        } finally {
          setChecking(false)
        }
      }
    }
    if (last) save.mutate()
    else go(index + 1)
  }

  const picked = draft.propertyId !== null

  return (
    <main>
      <Container className="max-w-[560px] space-y-5 py-6 nav:py-10">
        <div>
          <p className="text-xs font-semibold tracking-wide text-teal-700 uppercase">
            Add your home
          </p>
          <h1
            data-testid="add-home-step"
            className="mt-1 font-display text-2xl font-semibold text-ink-900"
          >
            Step {index + 1} of {steps.length} · {STEP_TITLES[step]}
          </h1>
          <ol className="mt-3 flex gap-1.5" aria-label="Steps">
            {steps.map((s, i) => (
              <li
                key={s}
                aria-current={i === index ? "step" : undefined}
                className={`h-1.5 flex-1 rounded-full ${i <= index ? "bg-teal-600" : "bg-line"}`}
              />
            ))}
          </ol>
        </div>

        <form
          noValidate
          ref={form}
          onSubmit={(e) => void next(e)}
          aria-label="Add your home"
          className="space-y-4 rounded-xl border border-line bg-surface p-5 shadow-sm"
        >
          {step === "address" && picked && (
            <Notice tone="teal">
              <p>You picked a home that is already listed.</p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="mt-2"
                data-testid="home-match-undo"
                onClick={() => set("propertyId", null)}
              >
                Change the address
              </Button>
            </Notice>
          )}
          {step === "address" && !picked && (
            <>
              <Field id="ah-city" label="City" error={error("city")}>
                <select
                  id="ah-city"
                  className={selectClass}
                  value={draft.city}
                  onChange={(e) => {
                    clearErrors("city")
                    clearErrors("location")
                    setPinWarning(null)
                    setDraft((d) => ({ ...d, city: e.target.value, location: EMPTY_DRAFT }))
                  }}
                >
                  <option value="">Pick a city</option>
                  {cities.data?.map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.name}
                    </option>
                  ))}
                  <option value={OTHER_CITY}>Another city…</option>
                </select>
              </Field>
              {draft.city === OTHER_CITY && (
                <>
                  <div className="grid grid-cols-2 gap-3">
                    <Field id="ah-other-city" label="City" error={error("otherCity")}>
                      <Input
                        id="ah-other-city"
                        value={draft.otherCity}
                        maxLength={80}
                        onChange={(e) => set("otherCity", e.target.value)}
                        className="h-11"
                      />
                    </Field>
                    <Field id="ah-other-state" label="State" error={error("otherState")}>
                      <Input
                        id="ah-other-state"
                        value={draft.otherState}
                        maxLength={80}
                        onChange={(e) => set("otherState", e.target.value)}
                        className="h-11"
                      />
                    </Field>
                  </div>
                  <Field
                    id="ah-locality"
                    label="Area or locality"
                    hint="A new city is checked by the team before it is listed. Your home is saved now."
                    error={error("locality")}
                  >
                    <Input
                      id="ah-locality"
                      value={draft.locality}
                      maxLength={120}
                      onChange={(e) => set("locality", e.target.value)}
                      className="h-11"
                    />
                  </Field>
                </>
              )}
              {draft.city && draft.city !== OTHER_CITY && (
                <LocationPickers
                  city={draft.city}
                  value={draft.location}
                  onChange={setLocation}
                  error={error("area")}
                  hint="Pick the area. If it or your society is missing, add it: new names show at once, tagged “Pending review”, and the team reviews them."
                  levelErrors={{
                    society: error("society"),
                    tower: error("tower"),
                    wing: error("wing"),
                  }}
                />
              )}
              {standalone(draft) && (
                <Field
                  id="ah-building"
                  label={
                    draft.propertyType === "APARTMENT" ? (
                      "Building name"
                    ) : (
                      <>Building name {optional}</>
                    )
                  }
                  hint="The name on the building, e.g. Sai Residency."
                  error={error("buildingName")}
                >
                  <Input
                    id="ah-building"
                    value={draft.buildingName ?? ""}
                    maxLength={160}
                    onChange={(e) => set("buildingName", e.target.value)}
                    className="h-11"
                  />
                </Field>
              )}
              <div className="grid grid-cols-2 gap-3">
                <Field id="ah-unit" label="Flat / house no." error={error("unitLabel")}>
                  <Input
                    id="ah-unit"
                    value={draft.unitLabel}
                    maxLength={40}
                    placeholder="e.g. C-1204"
                    onChange={(e) => set("unitLabel", e.target.value)}
                    className="h-11"
                  />
                </Field>
                <Field id="ah-pincode" label="PIN code" error={error("pincode")}>
                  <Input
                    id="ah-pincode"
                    inputMode="numeric"
                    maxLength={6}
                    value={draft.pincode}
                    onChange={(e) => setPincode(e.target.value, true)}
                    className="h-11"
                    aria-invalid={Boolean(error("pincode"))}
                  />
                </Field>
              </div>
              {draft.location.area.kind === "pick" &&
                (draft.location.area.pincodes?.length ?? 0) > 1 && (
                  <div className="flex flex-wrap items-center gap-1.5" data-testid="ah-pin-choices">
                    <span className="text-xs text-ink-500">
                      {draft.location.area.name} PIN codes:
                    </span>
                    {draft.location.area.pincodes!.map((pin) => (
                      <Button
                        key={pin}
                        type="button"
                        size="xs"
                        variant={pin === draft.pincode.trim() ? "default" : "outline"}
                        onClick={() => setPincode(pin, false)}
                      >
                        {pin}
                      </Button>
                    ))}
                  </div>
                )}
              {pinWarning && (
                <div
                  data-testid="ah-pincode-warning"
                  role="status"
                  className="space-y-2 rounded-md bg-warn-100 p-3 text-sm text-[#6f4608]"
                >
                  <p>{pinWarning}</p>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    data-testid="ah-keep-pincode"
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
              <div className="grid grid-cols-2 gap-3">
                <Field id="ah-type" label="Type" error={error("propertyType")}>
                  <select
                    id="ah-type"
                    className={selectClass}
                    value={draft.propertyType}
                    onChange={(e) => set("propertyType", e.target.value as PropertyType)}
                  >
                    {TYPES.map((t) => (
                      <option key={t} value={t}>
                        {typeLabel(t)}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field id="ah-bedrooms" label={<>Bedrooms {optional}</>} error={error("bedrooms")}>
                  <Input
                    id="ah-bedrooms"
                    inputMode="numeric"
                    maxLength={2}
                    value={draft.bedrooms}
                    onChange={(e) => set("bedrooms", e.target.value)}
                    className="h-11"
                  />
                </Field>
              </div>
              {matches && matches.length > 0 && (
                <Matches
                  matches={matches}
                  onPick={(id) => {
                    set("propertyId", id)
                    setMatches(null)
                    go(1)
                  }}
                  onNone={() => {
                    setMatches(null)
                    go(1)
                  }}
                />
              )}
            </>
          )}

          {step === "relation" && (
            <fieldset className="space-y-2">
              <legend className="mb-1 text-sm font-medium text-ink-900">This home is…</legend>
              {(
                [
                  [
                    "RENT",
                    "I rent it, or rented it",
                    "Your tenancy and rent go on the home's page.",
                  ],
                  ["OWN", "I own it", "You become its landlord on " + BRAND.name + "."],
                ] as const
              ).map(([value, title, hint]) => (
                <label
                  key={value}
                  data-testid={`ah-relation-${value.toLowerCase()}`}
                  className={`flex cursor-pointer gap-3 rounded-lg border p-4 ${
                    draft.relation === value ? "border-teal-600 bg-teal-50" : "border-line"
                  }`}
                >
                  <input
                    type="radio"
                    name="relation"
                    value={value}
                    checked={draft.relation === value}
                    disabled={value === "OWN" && picked}
                    onChange={() => set("relation", value)}
                    className="mt-1 size-4 accent-teal-600"
                  />
                  <span>
                    <span className="block font-medium text-ink-900">{title}</span>
                    <span className="block text-sm text-ink-500">{hint}</span>
                  </span>
                </label>
              ))}
              {error("relation") && <p className="text-xs text-danger-600">{error("relation")}</p>}
            </fieldset>
          )}

          {step === "tenancy" && (
            <>
              <Field id="ah-start" label="Moved in" error={error("startDate")}>
                <Input
                  id="ah-start"
                  type="date"
                  max={today()}
                  value={draft.startDate}
                  onChange={(e) => set("startDate", e.target.value)}
                  className="h-11"
                />
              </Field>
              <label className="flex items-center gap-2 text-sm text-ink-900">
                <input
                  id="ah-still"
                  type="checkbox"
                  checked={draft.stillLiving}
                  onChange={(e) => set("stillLiving", e.target.checked)}
                  className="size-4 accent-teal-600"
                />
                I still live here
              </label>
              {!draft.stillLiving && (
                <Field id="ah-end" label="Moved out" error={error("endDate")}>
                  <Input
                    id="ah-end"
                    type="date"
                    value={draft.endDate}
                    onChange={(e) => set("endDate", e.target.value)}
                    className="h-11"
                  />
                </Field>
              )}
              <div className="grid grid-cols-2 gap-3">
                <Field
                  id="ah-rent"
                  label={<>Monthly rent (₹) {optional}</>}
                  hint="Leave it empty to keep it private: the home shows “Rent not shared”."
                  error={error("rent")}
                >
                  <Input
                    id="ah-rent"
                    inputMode="numeric"
                    value={draft.rent}
                    onChange={(e) => set("rent", e.target.value)}
                    className="h-11"
                  />
                </Field>
                <Field id="ah-deposit" label={<>Deposit (₹) {optional}</>} error={error("deposit")}>
                  <Input
                    id="ah-deposit"
                    inputMode="numeric"
                    value={draft.deposit}
                    onChange={(e) => set("deposit", e.target.value)}
                    className="h-11"
                  />
                </Field>
              </div>
            </>
          )}

          {step === "owner" && (
            <>
              <p className="flex gap-2 text-sm text-ink-700">
                <LockIcon size={16} className="mt-0.5 shrink-0 text-ink-500" />
                Private: only you and the {BRAND.name} team see this. It lets the owner claim the
                home later. We do not contact them yet.
              </p>
              <Field
                id="ah-owner-name"
                label={<>Owner&rsquo;s name {optional}</>}
                error={error("ownerName")}
              >
                <Input
                  id="ah-owner-name"
                  value={draft.ownerName}
                  maxLength={80}
                  onChange={(e) => set("ownerName", e.target.value)}
                  className="h-11"
                />
              </Field>
              <Field
                id="ah-owner-email"
                label={<>Owner&rsquo;s email {optional}</>}
                error={error("ownerEmail")}
              >
                <Input
                  id="ah-owner-email"
                  type="email"
                  value={draft.ownerEmail}
                  onChange={(e) => set("ownerEmail", e.target.value)}
                  className="h-11"
                />
              </Field>
              <Field
                id="ah-owner-phone"
                label={<>Owner&rsquo;s phone {optional}</>}
                error={error("ownerPhone")}
              >
                <Input
                  id="ah-owner-phone"
                  type="tel"
                  maxLength={20}
                  value={draft.ownerPhone}
                  onChange={(e) => set("ownerPhone", e.target.value)}
                  className="h-11"
                />
              </Field>
            </>
          )}

          {error("form") && <p className="text-sm text-danger-600">{error("form")}</p>}
          {!fieldFailure && <ActionError error={save.error} startDate={draft.startDate} />}

          {!(step === "address" && matches && matches.length > 0) && (
            <div className="flex gap-2 pt-1">
              {index > 0 && (
                <Button
                  type="button"
                  variant="outline"
                  size="lg"
                  onClick={() => go(index - 1)}
                  data-testid="ah-back"
                >
                  Back
                </Button>
              )}
              <Button
                type="submit"
                size="lg"
                className="flex-1"
                disabled={checking || save.isPending}
                data-testid={last ? "ah-save" : "ah-next"}
              >
                {last
                  ? save.isPending
                    ? "Saving…"
                    : "Save my home"
                  : checking
                    ? "Checking…"
                    : "Next"}
              </Button>
            </div>
          )}
        </form>
      </Container>
    </main>
  )
}
