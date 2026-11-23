import { useState } from "react"
import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { Button } from "@/components/ui/button"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { adminKeys, fetchAuditLog, type AuditEntry } from "./api"

/** The filters the page offers; account deletions first (D-159). */
const FILTERS: { action: string | null; label: string }[] = [
  { action: "ACCOUNT_DELETED", label: "Account deletions" },
  { action: null, label: "Everything" },
]

/** "ACCOUNT_DELETED" → "Account deleted". */
function actionLabel(action: string): string {
  const words = action.toLowerCase().split("_")
  return [words[0].charAt(0).toUpperCase() + words[0].slice(1), ...words.slice(1)].join(" ")
}

function Row({ entry }: { entry: AuditEntry }) {
  return (
    <li className="rounded-xl border border-line bg-surface p-4 shadow-sm" data-testid="audit-row">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="font-semibold text-ink-900">{actionLabel(entry.action)}</p>
        <time dateTime={entry.createdAt} className="text-sm text-ink-500">
          {new Date(entry.createdAt).toLocaleString()}
        </time>
      </div>
      <p className="mt-1 text-sm break-all text-ink-700">
        {entry.targetType.toLowerCase()} <span className="font-mono">{entry.targetRef}</span>
      </p>
      <p className="mt-0.5 text-sm text-ink-500">
        {entry.self ? "By the user (self-service)" : `By ${entry.actor ?? "an admin"}`}
      </p>
      {entry.reason && <p className="mt-1 text-sm text-ink-700">{entry.reason}</p>}
    </li>
  )
}

/**
 * `/audit-log` (D-87, D-159): the append-only audit log, newest first. Account deletions show by
 * default; each carries only the user id and the time, never a name, email or phone.
 */
export function AuditLogPage() {
  const [action, setAction] = useState<string | null>(FILTERS[0].action)
  const [page, setPage] = useState(0)
  const log = useQuery({
    queryKey: adminKeys.auditLog(action, page),
    queryFn: ({ signal }) => fetchAuditLog({ action, page }, signal),
    placeholderData: keepPreviousData,
  })
  const pages = log.data?.page.totalPages ?? 0

  return (
    <main className="mx-auto w-full max-w-2xl px-4 py-5 nav:py-8">
      <h1 className="font-display text-2xl font-semibold text-ink-900">Audit log</h1>
      <p className="mt-1 text-sm text-ink-500">
        Every admin decision and every account deletion, newest first.
      </p>
      <div role="group" aria-label="Show" className="mt-4 flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <Button
            key={f.label}
            type="button"
            size="sm"
            variant={f.action === action ? "default" : "outline"}
            aria-pressed={f.action === action}
            onClick={() => {
              setAction(f.action)
              setPage(0)
            }}
          >
            {f.label}
          </Button>
        ))}
      </div>

      {log.isError && (
        <ErrorState className="mt-5" error={log.error} onRetry={() => void log.refetch()} />
      )}
      {log.isPending && (
        <div aria-hidden className="mt-5 space-y-3">
          <div className="h-20 animate-pulse rounded-xl bg-sunk" />
          <div className="h-20 animate-pulse rounded-xl bg-sunk" />
        </div>
      )}
      {log.isSuccess && log.data.items.length === 0 && (
        <EmptyState
          className="mt-5"
          icon="clock"
          title="Nothing here yet"
          description="Rows show up here as admins decide and users delete their accounts."
        />
      )}
      {log.isSuccess && log.data.items.length > 0 && (
        <ul className="mt-5 space-y-3">
          {log.data.items.map((entry) => (
            <Row key={`${entry.action}-${entry.targetRef}-${entry.createdAt}`} entry={entry} />
          ))}
        </ul>
      )}
      {pages > 1 && (
        <div className="mt-5 flex items-center justify-between">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
          >
            Newer
          </Button>
          <span className="text-sm text-ink-500">
            Page {page + 1} of {pages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= pages}
            onClick={() => setPage((p) => p + 1)}
          >
            Older
          </Button>
        </div>
      )}
    </main>
  )
}
