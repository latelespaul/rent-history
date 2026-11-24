import { StarIcon } from "@/components/ui/icons.gen"
import { cn } from "@/lib/utils"

const STAR_WORDS = ["", "Poor", "Below average", "OK", "Good", "Excellent"]

/**
 * Five 44 px star buttons as one radio group. Tapping the picked star again clears it, so every
 * parameter can be left unrated. `testId` prefixes each star's test id (`<testId>-4`).
 */
export function StarPicker({
  label,
  value,
  onChange,
  testId,
  describedBy,
}: {
  label: string
  value: number | null | undefined
  onChange: (n: number | null) => void
  testId: string
  describedBy?: string
}) {
  const current = value ?? 0
  return (
    <div className="space-y-1">
      <div
        role="radiogroup"
        aria-label={label}
        aria-describedby={describedBy}
        className="flex gap-1.5"
      >
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            role="radio"
            aria-checked={current === n}
            aria-label={`${n} star${n > 1 ? "s" : ""}`}
            data-testid={`${testId}-${n}`}
            onClick={() => onChange(current === n ? null : n)}
            className={cn(
              "grid size-11 place-items-center rounded-md border",
              n <= current
                ? "border-warn-600 bg-warn-100 text-warn-600"
                : "border-line text-ink-300",
            )}
          >
            <StarIcon size={22} />
          </button>
        ))}
      </div>
      <p className="text-xs text-ink-500">{STAR_WORDS[current] || "Not rated"}</p>
    </div>
  )
}
