import { Badge } from "@/components/ui/badge"
import { TrendUpIcon } from "@/components/ui/icons.gen"
import { EmptyState, ErrorState } from "@/components/ui/states"
import type { RentHistoryRow } from "@/types/api"
import { monthLabel, rupees } from "./labels"
import { SignInPrompt } from "./SignInPrompt"
import { useRentHistory } from "./useProperties"

/** Newest first; a change is shown against the row before it in time. */
function rowsWithChange(rows: RentHistoryRow[]) {
  const sorted = [...rows].sort((a, b) => b.fromMonth.localeCompare(a.fromMonth))
  return sorted.map((row, i) => {
    const older = sorted[i + 1]
    const now = row.monthlyRentInr
    const before = older?.monthlyRentInr
    const change =
      now != null && before != null && before > 0 ? ((now - before) / before) * 100 : null
    return { row, change }
  })
}

/**
 * Rent history (D-26): monthly rent from confirmed tenancies, no tenant names, month precision.
 * Signed-in only (D-56). Until the API serves it (B5) the card says so instead of guessing.
 */
export function RentHistory({ propertyId, signedIn }: { propertyId: string; signedIn: boolean }) {
  const history = useRentHistory(propertyId, signedIn)
  // An unclaimed home (D-138) shows its tenant's rent before anyone could confirm it.
  const unconfirmed = history.data?.some((row) => row.confirmed === false) ?? false

  let body
  if (!signedIn) {
    body = (
      <SignInPrompt what="Sign in to see the full rent history: what this home actually rented for." />
    )
  } else if (history.isPending) {
    body = <p className="text-sm text-ink-500">Loading rent history…</p>
  } else if (history.isError) {
    body = (
      <ErrorState
        title="Rent history could not be loaded"
        error={history.error}
        onRetry={() => void history.refetch()}
      />
    )
  } else if (history.data === null) {
    body = (
      <EmptyState
        icon="trend-up"
        title="Rent history is not available yet"
        description="Confirmed rent from tenancies will show here once tenancy records are live."
      />
    )
  } else if (history.data.length === 0) {
    body = (
      <EmptyState
        icon="trend-up"
        title="No confirmed rent yet"
        description="Rent appears here once a tenancy at this home is confirmed."
      />
    )
  } else {
    body = (
      <div className="overflow-x-auto">
        <table className="num w-full text-sm">
          <thead>
            <tr className="border-b border-line text-left text-xs tracking-wide text-ink-500 uppercase">
              <th className="py-2 pr-4 font-semibold">From</th>
              <th className="py-2 pr-4 font-semibold">To</th>
              <th className="py-2 pr-4 text-right font-semibold">Monthly rent</th>
              <th className="py-2 text-right font-semibold">Change</th>
            </tr>
          </thead>
          <tbody>
            {rowsWithChange(history.data).map(({ row, change }) => (
              <tr
                key={`${row.fromMonth}-${row.monthlyRentInr}`}
                className="border-b border-line last:border-0"
              >
                <td className="py-2.5 pr-4">{monthLabel(row.fromMonth)}</td>
                <td className="py-2.5 pr-4">{monthLabel(row.toMonth) ?? "now"}</td>
                <td className="py-2.5 pr-4 text-right font-semibold">
                  {row.monthlyRentInr != null ? rupees(row.monthlyRentInr) : "Hidden"}
                  {row.confirmed === false && (
                    <span className="block text-xs font-normal text-ink-500">
                      added by the tenant · not verified yet
                    </span>
                  )}
                  {row.maintenanceInr ? (
                    <span className="block text-xs font-normal text-ink-500">
                      + {rupees(row.maintenanceInr)} maintenance
                    </span>
                  ) : null}
                  {row.share && (
                    <span className="block text-xs font-normal text-ink-500">per share</span>
                  )}
                </td>
                <td className="py-2.5 text-right text-ink-700">
                  {change === null
                    ? "first record"
                    : `${change > 0 ? "+" : ""}${change.toFixed(1)}%`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )
  }

  return (
    <section
      aria-labelledby="rent-heading"
      className="space-y-3 rounded-xl border border-line bg-surface p-5 shadow-sm"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="rent-heading" className="inline-flex items-center gap-2 text-lg font-semibold">
          <TrendUpIcon size={20} />
          Rent history
        </h2>
        {unconfirmed ? (
          <Badge>Not verified yet</Badge>
        ) : (
          <Badge variant="verified">Confirmed tenancies</Badge>
        )}
      </div>
      <p className="text-sm text-ink-500">
        {unconfirmed
          ? "Monthly rent as the tenant gave it, not verified yet. No tenant names."
          : "Monthly rent from confirmed tenancies. No tenant names."}
      </p>
      {body}
    </section>
  )
}
