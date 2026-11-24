import { useState, type ReactNode } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link, useParams } from "react-router-dom"
import { isApiError } from "@/api/errors"
import { fetchReviewTasks, myReviewKeys, reviewKeys, submitReviewForm } from "@/api/reviews"
import { tenancyKeys } from "@/api/tenancies"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { BuildingIcon, CheckIcon, ChevronRightIcon } from "@/components/ui/icons.gen"
import { Label } from "@/components/ui/label"
import { ErrorState } from "@/components/ui/states"
import { Textarea } from "@/components/ui/textarea"
import { Stars } from "@/features/property/Rating"
import { ReviewTick } from "@/features/property/ReviewTick"
import {
  clearDraft,
  COMMENT_MAX,
  criterionLabel,
  emptyDraft,
  HOME_CRITERIA,
  LANDLORD_CRITERIA,
  loadDraft,
  missingOverall,
  overallOf,
  PROS_CONS_MAX,
  saveDraft,
  sectionOfField,
  sectionsFor,
  toRequest,
  workedOutOverall,
  type CriterionText,
  type PartDraft,
  type ReviewDraft,
  type Section,
} from "@/features/review/form"
import { StarPicker } from "@/features/review/StarPicker"
import { ActionError } from "@/features/tenancy/ApprovalPrompt"
import { isGateError } from "@/features/tenancy/gate"
import { dateLabel, tenancyHome, today } from "@/features/tenancy/labels"
import { reviewWindow } from "@/features/tenancy/reviewWindow"
import { useTenancy } from "@/features/tenancy/useTenancies"
import { BRAND } from "@/lib/brand"
import { cn } from "@/lib/utils"
import type { ReviewFormResult, ReviewTask, Tenancy } from "@/types/api"

type Parts = { home: boolean; landlord: boolean }
type PartKey = "home" | "landlord"
type Errors = Record<string, string>

const SECTION_TITLES: Record<Section, string> = {
  stay: "Your stay",
  home: "The home",
  landlord: "The landlord",
  overall: "Overall",
  check: "Check and submit",
}

function FieldError({ message, id }: { message?: string; id?: string }) {
  if (!message) return null
  return (
    <p id={id} className="text-xs text-danger-600" data-testid="review-field-error">
      {message}
    </p>
  )
}

/** One parameter: its name, a hint and 1–5 stars, skippable. */
function CriterionRow({
  criterion,
  value,
  error,
  onChange,
}: {
  criterion: CriterionText
  value: number | undefined
  error?: string
  onChange: (n: number | null) => void
}) {
  const hintId = `hint-${criterion.key}`
  return (
    <div className="space-y-1.5 border-t border-line pt-4 first:border-t-0 first:pt-0">
      <p className="text-sm font-medium text-ink-900">{criterion.label}</p>
      <p id={hintId} className="text-xs text-ink-500">
        {criterion.hint}
      </p>
      <StarPicker
        label={criterion.label}
        value={value}
        onChange={onChange}
        testId={`rate-${criterion.key}`}
        describedBy={hintId}
      />
      <FieldError message={error} />
    </div>
  )
}

function TextField({
  id,
  label,
  value,
  max,
  error,
  onChange,
}: {
  id: string
  label: string
  value: string
  max: number
  error?: string
  onChange: (text: string) => void
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>
        {label} <span className="font-normal text-ink-500">(optional)</span>
      </Label>
      <Textarea
        id={id}
        rows={3}
        maxLength={max}
        value={value}
        data-testid={id}
        onChange={(e) => onChange(e.target.value)}
      />
      <p className={cn("text-xs", error ? "text-danger-600" : "text-ink-500")}>
        {error ?? `${value.trim().length} / ${max} · no names, phone numbers or flat numbers`}
      </p>
    </div>
  )
}

function YesNo({
  label,
  value,
  testId,
  onChange,
}: {
  label: string
  value: boolean | null
  testId: string
  onChange: (v: boolean | null) => void
}) {
  return (
    <div className="space-y-1.5">
      <p className="text-sm font-medium text-ink-900">{label}</p>
      <div role="radiogroup" aria-label={label} className="flex gap-2">
        {([true, false] as const).map((v) => (
          <button
            key={String(v)}
            type="button"
            role="radio"
            aria-checked={value === v}
            data-testid={`${testId}-${v ? "yes" : "no"}`}
            onClick={() => onChange(value === v ? null : v)}
            className={cn(
              "h-11 min-w-20 rounded-md border px-4 text-sm font-medium",
              value === v ? "border-teal-700 bg-teal-50 text-teal-700" : "border-line text-ink-700",
            )}
          >
            {v ? "Yes" : "No"}
          </button>
        ))}
      </div>
    </div>
  )
}

/** The overall rating of one part: worked out from its parameters until the user picks one. */
function OverallPicker({
  part,
  draft,
  label,
  error,
  onChange,
}: {
  part: PartKey
  draft: PartDraft
  label: string
  error?: string
  onChange: (own: number | null) => void
}) {
  const worked = workedOutOverall(draft.ratings)
  return (
    <div className="space-y-1.5" data-testid={`overall-${part}`}>
      <p className="text-sm font-medium text-ink-900">{label}</p>
      <StarPicker
        label={label}
        value={overallOf(draft)}
        onChange={(n) => onChange(n)}
        testId={`overall-${part}`}
      />
      {draft.ownOverall === null && worked !== null && (
        <p className="text-xs text-teal-700" data-testid={`overall-${part}-worked-out`}>
          Worked out from your ratings. Tap a star to give your own.
        </p>
      )}
      {draft.ownOverall !== null && worked !== null && draft.ownOverall !== worked && (
        <p className="text-xs text-ink-500">
          Your own rating.{" "}
          <button
            type="button"
            className="font-medium text-teal-700 underline"
            onClick={() => onChange(null)}
          >
            Use the worked-out rating ({worked} stars)
          </button>
        </p>
      )}
      <FieldError message={error} />
    </div>
  )
}

/** One answered line of the check screen. */
function SummaryRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3 text-sm">
      <dt className="min-w-0 text-ink-700">{label}</dt>
      <dd className="shrink-0 text-right text-ink-900">{children}</dd>
    </div>
  )
}

function StarsOrSkipped({ value }: { value: number | null | undefined }) {
  if (!value) return <span className="text-ink-500">Skipped</span>
  return (
    <span className="inline-flex items-center gap-1.5" aria-label={`${value} out of 5`}>
      <Stars value={value} />
      {value}
    </span>
  )
}

function SummaryBlock({
  title,
  onEdit,
  testId,
  children,
}: {
  title: string
  onEdit: () => void
  testId: string
  children: ReactNode
}) {
  return (
    <section className="space-y-2 rounded-lg border border-line p-4" data-testid={testId}>
      <div className="flex items-center justify-between gap-2">
        <h3 className="font-semibold text-ink-900">{title}</h3>
        <Button type="button" size="sm" variant="outline" onClick={onEdit}>
          Edit
        </Button>
      </div>
      <dl className="space-y-1.5">{children}</dl>
    </section>
  )
}

/**
 * "You submitted this review" (captain 21:38): what was published, with its tick state, under the
 * home it is about. The home's name and place open its page, as the button does (captain
 * 2026-11-24 09:44: a tap on the home after submitting must open it).
 */
function Submitted({ result, tenancy }: { result: ReviewFormResult; tenancy: Tenancy }) {
  const propertyId = tenancy.property.id
  const home = result.home
  const landlord = result.landlord
  return (
    <section
      data-testid="review-submitted"
      className="rounded-xl border border-line bg-surface p-6 shadow-sm"
    >
      <div className="text-center">
        <span className="mx-auto grid size-14 place-items-center rounded-full bg-success-100 text-success-600">
          <CheckIcon size={30} />
        </span>
        <h1 className="mt-3 font-display text-2xl font-semibold text-ink-900">
          You submitted this review
        </h1>
        <p className="mt-1 text-sm text-ink-500">
          {home ? "The home review is on the home’s page now." : "Thank you for your review."} The{" "}
          {BRAND.name} team verifies it later.
        </p>
      </div>
      <Link
        to={`/p/${propertyId}`}
        data-testid="review-home-link"
        className="mt-5 flex items-center gap-3 rounded-lg border border-line p-3 text-ink-900 no-underline hover:border-teal-600 hover:no-underline"
      >
        <span className="rx-placeholder grid size-11 shrink-0 place-items-center rounded-md">
          <BuildingIcon size={20} className="text-ink-300" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate font-semibold">{tenancyHome(tenancy)}</span>
          <span className="block truncate text-sm text-ink-500">
            {tenancy.property.locality}, {tenancy.property.city.name}
          </span>
        </span>
        <ChevronRightIcon size={20} className="shrink-0 text-ink-500" />
      </Link>
      {home && (
        <figure className="mt-3 space-y-1 rounded-lg border border-line p-4">
          <p className="flex flex-wrap items-center gap-2">
            <span className="font-semibold text-ink-900">Home: {home.ratingOverall} / 5</span>
            <ReviewTick state={home.verification} />
          </p>
          {home.body && (
            <blockquote className="text-sm whitespace-pre-line text-ink-700">
              {home.body}
            </blockquote>
          )}
        </figure>
      )}
      {landlord && (
        <figure className="mt-3 space-y-1 rounded-lg border border-line p-4">
          <p className="font-semibold text-ink-900">Landlord: {landlord.ratingOverall} / 5</p>
          <p className="text-sm text-ink-500" data-testid="review-landlord-state">
            {landlord.status === "PUBLISHED"
              ? "Published together with your landlord’s review of you."
              : "Hidden until your landlord reviews you, or for 14 days, so neither side can react to the other."}
          </p>
        </figure>
      )}
      <Button asChild size="lg" className="mt-5 w-full" data-testid="review-view-home">
        <Link to={`/p/${propertyId}#reviews`}>See it on the home&rsquo;s page</Link>
      </Button>
    </section>
  )
}

/** The form itself, one section per screen (D-157). */
function ReviewForm({ tenancy, parts }: { tenancy: Tenancy; parts: Parts }) {
  const id = tenancy.id
  const queryClient = useQueryClient()
  const [restored] = useState(() => loadDraft(id) !== null)
  const [draft, setDraft] = useState<ReviewDraft>(() => loadDraft(id) ?? emptyDraft())
  const [errors, setErrors] = useState<Errors>({})
  const [savedNote, setSavedNote] = useState<string | null>(restored ? "restored" : null)
  const sections = sectionsFor(parts)
  const step = Math.min(draft.step, sections.length - 1)
  const section = sections[step]

  const submit = useMutation({
    mutationFn: () => submitReviewForm(id, toRequest(draft, parts)),
    onSuccess: async () => {
      clearDraft(id)
      // Tenancies too: their "Rate and review" next step is now done.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["properties"] }),
        queryClient.invalidateQueries({ queryKey: ["users"] }),
        queryClient.invalidateQueries({ queryKey: reviewKeys.tasks }),
        queryClient.invalidateQueries({ queryKey: tenancyKeys.all }),
        queryClient.invalidateQueries({ queryKey: myReviewKeys.all }),
      ])
      window.scrollTo?.({ top: 0 })
    },
    onError: (error) => {
      if (!isApiError(error) || error.fieldErrors.length === 0) return
      const next: Errors = {}
      for (const e of error.fieldErrors) next[e.field] ??= e.message
      setErrors(next)
      const first = sections.indexOf(sectionOfField(error.fieldErrors[0].field))
      if (first >= 0) goTo(first)
    },
  })

  if (submit.data) {
    return <Submitted result={submit.data} tenancy={tenancy} />
  }

  function goTo(next: number) {
    const moved = { ...draft, step: next }
    setDraft(moved)
    if (saveDraft(id, { ...moved, savedAt: new Date().toISOString() })) setSavedNote(null)
    window.scrollTo?.({ top: 0 })
  }

  function setPart(key: PartKey, change: Partial<PartDraft>) {
    setDraft((d) => ({ ...d, [key]: { ...d[key], ...change } }))
  }

  function rate(key: PartKey, criterion: string, n: number | null) {
    setDraft((d) => {
      const ratings = { ...d[key].ratings }
      if (n === null) delete ratings[criterion as keyof typeof ratings]
      else ratings[criterion as keyof typeof ratings] = n
      return { ...d, [key]: { ...d[key], ratings } }
    })
    setErrors(({ [`${key}.criteria.${criterion}`]: _, ...rest }) => rest)
  }

  function next() {
    if (section === "overall") {
      const missing = missingOverall(draft, parts)
      if (Object.keys(missing).length > 0) return setErrors((e) => ({ ...e, ...missing }))
    }
    goTo(step + 1)
  }

  function onSaveDraft() {
    setSavedNote(
      saveDraft(id, { ...draft, savedAt: new Date().toISOString() }) ? "saved" : "blocked",
    )
  }

  function startOver() {
    clearDraft(id)
    setDraft(emptyDraft())
    setErrors({})
    setSavedNote(null)
  }

  const landlordName = tenancy.landlord.shortName
  const ruleError =
    isApiError(submit.error) && submit.error.fieldErrors.length === 0 ? submit.error : null

  const criteriaSection = (key: PartKey, list: CriterionText[], intro: string) => (
    <div className="space-y-4">
      <p className="text-sm text-ink-500">{intro}</p>
      {list.map((c) => (
        <CriterionRow
          key={c.key}
          criterion={c}
          value={draft[key].ratings[c.key]}
          error={errors[`${key}.criteria.${c.key}`]}
          onChange={(n) => rate(key, c.key, n)}
        />
      ))}
      <TextField
        id={`review-comment-${key}`}
        label={
          key === "home" ? "Anything else about the home?" : "Anything else about the landlord?"
        }
        value={draft[key].comment}
        max={COMMENT_MAX}
        error={errors[`${key}.body`]}
        onChange={(comment) => setPart(key, { comment })}
      />
    </div>
  )

  return (
    <div className="space-y-5">
      <div>
        <p className="text-xs font-semibold tracking-wide text-teal-700 uppercase">
          Write a review
        </p>
        <h1 className="mt-1 font-display text-2xl font-semibold text-ink-900">
          {tenancyHome(tenancy)}
        </h1>
      </div>

      <div className="space-y-1.5" data-testid="review-progress">
        <p className="flex justify-between text-xs font-medium text-ink-700">
          <span>
            Step {step + 1} of {sections.length}
          </span>
          <span>{SECTION_TITLES[section]}</span>
        </p>
        <div
          className="h-2 overflow-hidden rounded-full bg-sunk"
          role="progressbar"
          aria-label="Review progress"
          aria-valuemin={1}
          aria-valuemax={sections.length}
          aria-valuenow={step + 1}
        >
          <div
            className="h-full rounded-full bg-teal-700 transition-all"
            style={{ width: `${((step + 1) / sections.length) * 100}%` }}
          />
        </div>
      </div>

      {savedNote === "restored" && (
        <p
          className="rounded-lg bg-sunk p-3 text-sm text-ink-700"
          data-testid="review-draft-restored"
        >
          We kept your draft on this device.{" "}
          <button type="button" className="font-medium text-teal-700 underline" onClick={startOver}>
            Start over
          </button>
        </p>
      )}

      <form
        noValidate
        aria-label="Review"
        data-testid="review-step"
        data-section={section}
        onSubmit={(event) => {
          event.preventDefault()
          if (section === "check") submit.mutate()
          else next()
        }}
        className="space-y-5 rounded-xl border border-line bg-surface p-5 shadow-sm"
      >
        <h2 className="text-lg font-semibold text-ink-900">{SECTION_TITLES[section]}</h2>

        {section === "stay" && (
          <div className="space-y-3 text-sm text-ink-700">
            <dl className="space-y-1.5 rounded-lg bg-sunk p-4" data-testid="review-period">
              <SummaryRow label="Moved in">{dateLabel(tenancy.startDate)}</SummaryRow>
              <SummaryRow label="Moved out">
                {tenancy.endDate ? dateLabel(tenancy.endDate) : "Still living here"}
              </SummaryRow>
            </dl>
            <p>The dates come from your tenancy. To change them, edit the tenancy.</p>
            <p>
              You rate the home{parts.landlord ? ` and your landlord, ${landlordName}` : ""}, one
              screen each. Every question is optional, except the overall rating.
            </p>
            {!parts.landlord && parts.home && (
              <p className="text-ink-500">
                Your landlord is not on {BRAND.name} yet, so only the home can be rated now.
              </p>
            )}
          </div>
        )}

        {section === "home" &&
          criteriaSection(
            "home",
            HOME_CRITERIA,
            "Rate what you know. Skip a question you cannot answer.",
          )}

        {section === "landlord" &&
          criteriaSection(
            "landlord",
            LANDLORD_CRITERIA,
            `About ${landlordName}. They see it only after they review you, or after 14 days.`,
          )}

        {section === "overall" && (
          <div className="space-y-5">
            {parts.home && (
              <>
                <OverallPicker
                  part="home"
                  draft={draft.home}
                  label="Overall rating of the home"
                  error={errors["home.ratingOverall"]}
                  onChange={(own) => {
                    setPart("home", { ownOverall: own })
                    setErrors(({ "home.ratingOverall": _, ...rest }) => rest)
                  }}
                />
                <YesNo
                  label="Would you recommend this home?"
                  value={draft.home.recommend}
                  testId="recommend-home"
                  onChange={(recommend) => setPart("home", { recommend })}
                />
              </>
            )}
            {parts.landlord && (
              <>
                <OverallPicker
                  part="landlord"
                  draft={draft.landlord}
                  label="Overall rating of the landlord"
                  error={errors["landlord.ratingOverall"]}
                  onChange={(own) => {
                    setPart("landlord", { ownOverall: own })
                    setErrors(({ "landlord.ratingOverall": _, ...rest }) => rest)
                  }}
                />
                <YesNo
                  label="Would you recommend this landlord?"
                  value={draft.landlord.recommend}
                  testId="recommend-landlord"
                  onChange={(recommend) => setPart("landlord", { recommend })}
                />
              </>
            )}
            <TextField
              id="review-pros"
              label="Pros"
              value={draft.pros}
              max={PROS_CONS_MAX}
              error={errors["home.pros"] ?? errors["landlord.pros"]}
              onChange={(pros) => setDraft((d) => ({ ...d, pros }))}
            />
            <TextField
              id="review-cons"
              label="Cons"
              value={draft.cons}
              max={PROS_CONS_MAX}
              error={errors["home.cons"] ?? errors["landlord.cons"]}
              onChange={(cons) => setDraft((d) => ({ ...d, cons }))}
            />
          </div>
        )}

        {section === "check" && (
          <div className="space-y-3">
            <p className="text-sm text-ink-500">
              Check your answers. Reviews are public, without your full name.
            </p>
            {(["home", "landlord"] as const)
              .filter((key) => parts[key])
              .map((key) => (
                <SummaryBlock
                  key={key}
                  title={key === "home" ? "The home" : `The landlord, ${landlordName}`}
                  testId={`review-summary-${key}`}
                  onEdit={() => goTo(sections.indexOf(key))}
                >
                  {(key === "home" ? HOME_CRITERIA : LANDLORD_CRITERIA).map((c) => (
                    <SummaryRow key={c.key} label={criterionLabel(c.key)}>
                      <StarsOrSkipped value={draft[key].ratings[c.key]} />
                    </SummaryRow>
                  ))}
                  <SummaryRow label="Overall">
                    <StarsOrSkipped value={overallOf(draft[key])} />
                  </SummaryRow>
                  <SummaryRow label="Would recommend">
                    {draft[key].recommend === null ? (
                      <span className="text-ink-500">Skipped</span>
                    ) : draft[key].recommend ? (
                      "Yes"
                    ) : (
                      "No"
                    )}
                  </SummaryRow>
                  {draft[key].comment.trim() && (
                    <p className="text-sm whitespace-pre-line text-ink-700">
                      {draft[key].comment.trim()}
                    </p>
                  )}
                </SummaryBlock>
              ))}
            {(draft.pros.trim() || draft.cons.trim()) && (
              <SummaryBlock
                title="Pros and cons"
                testId="review-summary-pros-cons"
                onEdit={() => goTo(sections.indexOf("overall"))}
              >
                {draft.pros.trim() && <SummaryRow label="Pros">{draft.pros.trim()}</SummaryRow>}
                {draft.cons.trim() && <SummaryRow label="Cons">{draft.cons.trim()}</SummaryRow>}
              </SummaryBlock>
            )}
            {ruleError && !isGateError(ruleError) ? (
              <p className="text-sm text-danger-600" data-testid="review-refused">
                {ruleError.problem.detail}
              </p>
            ) : (
              !ruleError && submit.error && <ActionError error={submit.error} />
            )}
            {ruleError && isGateError(ruleError) && <ActionError error={ruleError} />}
          </div>
        )}

        <div className="flex flex-wrap items-center gap-2 border-t border-line pt-4">
          {step > 0 && (
            <Button
              type="button"
              variant="outline"
              size="lg"
              data-testid="review-back"
              onClick={() => goTo(step - 1)}
            >
              Back
            </Button>
          )}
          {section === "check" ? (
            <Button
              type="submit"
              size="lg"
              className="flex-1"
              disabled={submit.isPending}
              data-testid="review-submit"
            >
              {submit.isPending ? "Submitting…" : "Submit review"}
            </Button>
          ) : (
            <Button type="submit" size="lg" className="flex-1" data-testid="review-next">
              Next
            </Button>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            data-testid="review-save-draft"
            onClick={onSaveDraft}
          >
            Save draft
          </Button>
          {savedNote === "saved" && (
            <span className="text-ink-500" data-testid="review-draft-saved">
              Draft saved on this device.
            </span>
          )}
          {savedNote === "blocked" && (
            <span className="text-danger-600">This browser does not let us save a draft.</span>
          )}
        </div>
      </form>
    </div>
  )
}

/** Nothing to write: the window is not open yet, or both reviews are in. */
function Closed({ tenancy }: { tenancy: Tenancy }) {
  const window = reviewWindow(tenancy, today())
  return (
    <section
      className="space-y-3 rounded-xl border border-line bg-surface p-6 shadow-sm"
      data-testid="review-closed"
    >
      <h1 className="font-display text-2xl font-semibold text-ink-900">{tenancyHome(tenancy)}</h1>
      <p className="text-sm text-ink-700">
        {window.state === "not_yet"
          ? `You can review it from ${dateLabel(window.opens)}: reviews open 60 days after you move in, or when you move out.`
          : window.state === "none"
            ? "Only a current or ended tenancy can be reviewed."
            : "You already reviewed this stay. You can change your review on the home’s page."}
      </p>
      <Button asChild variant="outline">
        <Link to={`/p/${tenancy.property.id}#reviews`}>Go to the home&rsquo;s page</Link>
      </Button>
    </section>
  )
}

/**
 * `/tenancies/:id/review`: the tenant's review form (captain 2026-11-23 21:29, D-157). One
 * section per screen: the stay, the home's nine parameters, the landlord's five (only when the
 * landlord can be reviewed, D-139), the overall ratings with "would you recommend" and pros and
 * cons, then a check screen. The draft stays on this device. The home review publishes at once
 * without the green tick (D-132, D-134); the landlord review waits for the reveal (D-46).
 */
export default function WriteReviewPage() {
  const { id = "" } = useParams()
  const tenancy = useTenancy(id)
  const tasks = useQuery({
    queryKey: reviewKeys.tasks,
    queryFn: ({ signal }) => fetchReviewTasks(signal),
  })
  // Kept from the first answer, so the form does not close when a submit refetches the tasks.
  const [parts, setParts] = useState<Parts | null>(null)
  if (parts === null && tasks.data) {
    const mine = tasks.data.filter((t: ReviewTask) => t.tenancyId === id)
    setParts({
      home: mine.some((t) => t.direction === "TENANT_TO_PROPERTY"),
      landlord: mine.some((t) => t.direction === "TENANT_TO_LANDLORD"),
    })
  }

  const error = tenancy.error ?? tasks.error
  let body
  if (error) {
    body = (
      <ErrorState
        error={error}
        onRetry={() => void Promise.all([tenancy.refetch(), tasks.refetch()])}
      />
    )
  } else if (!tenancy.data || parts === null) {
    body = <p className="text-sm text-ink-500">Loading the review form…</p>
  } else if (!parts.home && !parts.landlord) {
    body = <Closed tenancy={tenancy.data} />
  } else {
    body = <ReviewForm tenancy={tenancy.data} parts={parts} />
  }
  return (
    <main>
      <Container className="max-w-[560px] py-6 nav:py-10">{body}</Container>
    </main>
  )
}
