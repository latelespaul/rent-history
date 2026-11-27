import { useQuery } from "@tanstack/react-query"
import { ErrorState } from "@/components/ui/states"
import { adminKeys, fetchTraffic, type TrafficTop, type TrafficWindow } from "./api"

function Stat({ label, value }: { label: string; value: TrafficWindow }) {
  return (
    <div className="rounded-lg bg-sunk p-3">
      <p className="text-xs font-medium text-ink-500">{label}</p>
      <p className="mt-1 text-xl font-semibold text-ink-900">{value.views.toLocaleString()}</p>
      <p className="text-xs text-ink-500">{value.visitors.toLocaleString()} visitors</p>
    </div>
  )
}

function Top({ title, rows }: { title: string; rows: TrafficTop[] }) {
  return (
    <div>
      <h3 className="text-sm font-semibold text-ink-900">{title}</h3>
      {rows.length === 0 ? (
        <p className="mt-1 text-sm text-ink-500">Nothing yet</p>
      ) : (
        <ol className="mt-1 space-y-1">
          {rows.map((r) => (
            <li key={r.name} className="flex justify-between gap-3 text-sm text-ink-700">
              <span className="min-w-0 truncate font-mono">{r.name}</span>
              <span className="shrink-0">{r.views.toLocaleString()}</span>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

/**
 * `/traffic` (D-175): the site's visit counts. Daily totals only, kept without cookies, IPs or
 * user agents. A visitor who comes on two days counts once on each, because the daily hashes do
 * not link.
 */
export function TrafficPage() {
  const traffic = useQuery({
    queryKey: adminKeys.traffic,
    queryFn: ({ signal }) => fetchTraffic(signal),
  })
  const data = traffic.data

  return (
    <main className="mx-auto w-full max-w-2xl px-4 py-5 nav:py-8">
      <h1 className="font-display text-2xl font-semibold text-ink-900">Traffic</h1>
      <section
        aria-label="Traffic"
        className="mt-4 rounded-xl border border-line bg-surface p-4 shadow-sm"
      >
        {traffic.isError && (
          <ErrorState error={traffic.error} onRetry={() => void traffic.refetch()} />
        )}
        {traffic.isPending && <div aria-hidden className="h-24 animate-pulse rounded-lg bg-sunk" />}
        {data && (
          <div className="space-y-5">
            <div className="grid grid-cols-3 gap-3">
              <Stat label="Today" value={data.today} />
              <Stat label="7 days" value={data.last7Days} />
              <Stat label="30 days" value={data.last30Days} />
            </div>
            <div className="grid gap-5 sm:grid-cols-2">
              <Top title="Top pages (30 days)" rows={data.topPages} />
              <Top title="Top sources (30 days)" rows={data.topSources} />
            </div>
          </div>
        )}
      </section>
      <p className="mt-3 text-xs text-ink-500">
        Views per page, with visitors counted once a day. No cookies, no IP addresses, nothing tied
        to a user or a review.
      </p>
    </main>
  )
}
