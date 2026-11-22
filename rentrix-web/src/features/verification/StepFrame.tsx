import type { ReactNode } from "react"
import { Link, useLocation } from "react-router-dom"
import { Badge } from "@/components/ui/badge"
import { CheckIcon } from "@/components/ui/icons.gen"
import { cn } from "@/lib/utils"
import type { VerificationEntry } from "@/types/api"
import { roleWords, useWithNext } from "./next"
import { doneSteps, STEPS, type StepKey } from "./progress"

/**
 * The frame of one stepper step (mockups `verify-v4-*.html`): "Verify your account · tenant",
 * "Step n of 4 · Label" with Required / Optional, and the four steps as links, ticked when done.
 */
export function StepFrame({
  step,
  entries,
  children,
}: {
  step: StepKey
  entries: VerificationEntry[]
  children: ReactNode
}) {
  const location = useLocation()
  const withNext = useWithNext()
  const index = STEPS.findIndex((s) => s.key === step)
  const current = STEPS[index]
  // Profile, phone and ID are per user: any role's entry says the same. Rental details are per
  // role; the step counts when every held role has it.
  const done = entries.map(doneSteps)
  const isDone = (key: StepKey) => done.length > 0 && done.every((d) => d[key])

  return (
    <section className="rounded-xl border border-line bg-surface p-6 shadow-sm nav:p-8">
      <p className="text-xs font-semibold tracking-wide text-teal-700 uppercase">
        Verify your account · {roleWords(entries)}
      </p>
      <h1 className="mt-1 flex flex-wrap items-center gap-2 font-display text-2xl font-semibold text-ink-900">
        Step {index + 1} of {STEPS.length} · {current.label}
        <Badge variant={current.optional ? "neutral" : "warn"}>
          {current.optional ? "Optional" : "Required"}
        </Badge>
      </h1>
      <ol className="mt-4 grid grid-cols-4 gap-2" aria-label="Verification steps">
        {STEPS.map((s, i) => {
          const active = location.pathname === s.path
          const finished = isDone(s.key)
          return (
            <li key={s.key}>
              <Link
                to={withNext(s.path)}
                aria-current={active ? "step" : undefined}
                className={cn(
                  "flex h-full flex-col gap-1.5 rounded-md border-t-4 pt-2 text-xs no-underline hover:no-underline",
                  active ? "border-teal-600 text-ink-900" : "border-line text-ink-500",
                  finished && !active && "border-success-600",
                )}
              >
                <span className="flex items-center gap-1 font-semibold">
                  {finished ? <CheckIcon size={14} className="text-success-600" /> : `${i + 1}`}
                  <span>{s.label}</span>
                </span>
                {s.optional && <span className="max-sm:hidden">(optional)</span>}
              </Link>
            </li>
          )
        })}
      </ol>
      <div className="mt-6">{children}</div>
    </section>
  )
}
