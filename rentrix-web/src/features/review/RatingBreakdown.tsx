import { Stars } from "@/features/property/Rating"
import type { RatingDetail } from "@/types/api"
import { criterionLabel, recommendShare } from "./form"

/**
 * The review form's per-parameter averages (D-158): each parameter with its average from 3
 * ratings on (D-52), else "New · n ratings", and the "would recommend" share. Hidden until any
 * parameter has a rating.
 */
export function RatingBreakdown({
  rating,
  title,
  testId,
}: {
  rating: RatingDetail | undefined
  title: string
  testId: string
}) {
  const criteria = rating?.criteria ?? []
  if (!criteria.some((c) => c.ratingCount > 0)) return null
  const share = recommendShare(rating?.recommend)
  return (
    <div className="space-y-2" data-testid={testId}>
      <h3 className="text-sm font-semibold text-ink-900">{title}</h3>
      {share && (
        <p className="text-sm font-medium text-success-600" data-testid={`${testId}-recommend`}>
          {share}
        </p>
      )}
      <ul className="space-y-1.5">
        {criteria.map((c) => (
          <li
            key={c.criterion}
            data-testid={`${testId}-${c.criterion}`}
            className="flex items-center justify-between gap-3 text-sm"
          >
            <span className="min-w-0 text-ink-700">{criterionLabel(c.criterion)}</span>
            {c.average !== undefined && c.average !== null ? (
              <span
                className="inline-flex shrink-0 items-center gap-1.5"
                aria-label={`${c.average.toFixed(1)} out of 5 from ${c.ratingCount} ratings`}
              >
                <Stars value={c.average} />
                <span className="w-7 text-right font-semibold text-ink-900">
                  {c.average.toFixed(1)}
                </span>
              </span>
            ) : (
              <span className="shrink-0 text-xs text-ink-500">
                {c.ratingCount === 0
                  ? "No ratings yet"
                  : `New · ${c.ratingCount} rating${c.ratingCount === 1 ? "" : "s"}`}
              </span>
            )}
          </li>
        ))}
      </ul>
      <p className="text-xs text-ink-500">An average shows after 3 ratings.</p>
    </div>
  )
}
