import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useSearchParams } from "react-router-dom"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { CheckIcon, XIcon } from "@/components/ui/icons.gen"
import { Notice } from "@/components/ui/notice"
import { EmptyState, ErrorState, errorMessage } from "@/components/ui/states"
import { BRAND } from "@/lib/brand"
import { cn } from "@/lib/utils"
import {
  adminKeys,
  decideQueueItem,
  fetchEditHistory,
  fetchQueue,
  type QueueAction,
  type QueueItem,
  type QueueType,
  type QueueView,
} from "./api"
import {
  QUEUE_TABS,
  TYPE_LABEL,
  ago,
  approveLabel,
  doneMessage,
  reasonLabel,
  reasonsFor,
} from "./queueText"

const REFRESH_MS = 30_000

type Mode = { action: Exclude<QueueAction, "APPROVE"> } | null
type DecisionBody = { code: string; note?: string; noteFallback?: string }

const HISTORY_TYPES = ["TENANCY", "PROPERTY", "REVIEW"] as const
type HistoryType = (typeof HISTORY_TYPES)[number]

/**
 * What the owner changed on this item, field by field (D-156), for admins only: shown on a card
 * flagged `EDITED`, loaded when opened.
 */
function EditHistory({ type, id, now }: { type: HistoryType; id: string; now: number }) {
  const [open, setOpen] = useState(false)
  const history = useQuery({
    queryKey: adminKeys.editHistory(type, id),
    queryFn: ({ signal }) => fetchEditHistory(type, id, signal),
    enabled: open,
  })
  return (
    <div className="mt-3">
      <Button
        type="button"
        variant="ghost"
        size="sm"
        aria-expanded={open}
        data-testid="edit-history-toggle"
        onClick={() => setOpen(!open)}
      >
        {open ? "Hide edit history" : "Edit history"}
      </Button>
      {open && history.isPending && <p className="text-sm text-ink-500">Loading…</p>}
      {open && history.isError && (
        <p className="text-sm text-danger-600">{errorMessage(history.error)}</p>
      )}
      {open && history.data && (
        <ul className="mt-2 space-y-1.5 text-sm text-ink-700" data-testid="edit-history">
          {history.data.map((row, i) => (
            <li key={`${row.editRef}-${i}`} className="break-words">
              <span className="font-medium text-ink-900">{row.field}</span>{" "}
              {row.change === "DELETED"
                ? `deleted (${row.before ?? ""})`
                : `${row.before ?? "empty"} → ${row.after ?? "empty"}`}
              <span className="text-xs text-ink-500">
                {" "}
                · {row.editorName}, {ago(row.at, now)}
                {row.wasVerified && " · was verified"}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * One card (D-136): the facts to decide on, and the answer bar at the bottom where a thumb reaches.
 * Approve is one tap; Reject (or Hide, for abuse) is two: the button, then a reason, which sends it.
 * The note is optional and goes to the owner with the reason.
 */
function QueueCard({
  item,
  now,
  pending,
  onDecide,
}: {
  item: QueueItem
  now: number
  pending: boolean
  onDecide: (action: QueueAction, body?: DecisionBody) => void
}) {
  const [mode, setMode] = useState<Mode>(null)
  const [note, setNote] = useState("")
  const titleId = `card-${item.type}-${item.id}`
  const reasons = mode ? reasonsFor(item, mode.action) : []

  return (
    <li
      aria-labelledby={titleId}
      className="overflow-hidden rounded-xl border border-line bg-surface shadow-sm"
    >
      <div className="p-4">
        <div className="flex flex-wrap items-center gap-1.5">
          <Badge variant="info">{TYPE_LABEL[item.type]}</Badge>
          {item.type !== "CITY" && item.type !== "REPORT" && <Badge>Not verified yet</Badge>}
          {item.flags.map((flag) => (
            <Badge key={flag} variant="warn">
              {reasonLabel(flag)}
            </Badge>
          ))}
          <span className="ml-auto text-xs text-ink-500">{ago(item.createdAt, now)}</span>
        </div>
        <h2 id={titleId} className="mt-2 text-base font-semibold text-ink-900">
          {item.title}
        </h2>
        {item.subtitle && <p className="text-sm text-ink-700">{item.subtitle}</p>}
        {item.details.length > 0 && (
          <ul className="mt-2 space-y-1 text-sm text-ink-700">
            {item.details.map((line, i) => (
              <li key={i} className="break-words">
                {line}
              </li>
            ))}
          </ul>
        )}
        {item.flags.includes("EDITED") &&
          (HISTORY_TYPES as readonly string[]).includes(item.type) && (
            <EditHistory type={item.type as HistoryType} id={item.id} now={now} />
          )}
      </div>

      {mode ? (
        <div className="border-t border-line bg-sunk/60 p-4">
          <label className="block text-sm font-medium text-ink-900" htmlFor={`${titleId}-note`}>
            Note for the owner <span className="font-normal text-ink-500">(optional)</span>
          </label>
          <textarea
            id={`${titleId}-note`}
            value={note}
            maxLength={1000}
            rows={2}
            onChange={(e) => setNote(e.target.value)}
            className="mt-1 w-full rounded-md border border-line-strong bg-surface p-2 text-sm text-ink-900"
          />
          <p className="mt-3 text-sm font-medium text-ink-900">
            {mode.action === "HIDE" ? "Why hide it?" : "Why reject it?"} Tap a reason to send.
          </p>
          <div className="mt-2 grid grid-cols-2 gap-2">
            {reasons.map((code) => (
              <Button
                key={code}
                type="button"
                variant="outline"
                className="h-12 whitespace-normal"
                disabled={pending}
                onClick={() =>
                  onDecide(mode.action, {
                    code,
                    note: note.trim() || undefined,
                    noteFallback: `${reasonLabel(code)}.`,
                  })
                }
              >
                {reasonLabel(code)}
              </Button>
            ))}
          </div>
          <Button
            type="button"
            variant="ghost"
            className="mt-2 h-11 w-full"
            disabled={pending}
            onClick={() => {
              setMode(null)
              setNote("")
            }}
          >
            Cancel
          </Button>
        </div>
      ) : (
        <div className="flex gap-2 border-t border-line p-3">
          {item.actions.includes("APPROVE") && (
            <Button
              type="button"
              className="h-12 flex-1 text-base"
              disabled={pending}
              onClick={() => onDecide("APPROVE")}
              aria-label={`${approveLabel(item.type)}: ${item.title}`}
            >
              <CheckIcon />
              {approveLabel(item.type)}
            </Button>
          )}
          {item.actions.includes("REJECT") && (
            <Button
              type="button"
              variant="outline"
              className="h-12 flex-1 text-base"
              disabled={pending}
              onClick={() => setMode({ action: "REJECT" })}
              aria-label={`Reject: ${item.title}`}
            >
              <XIcon />
              Reject
            </Button>
          )}
          {item.actions.includes("HIDE") && (
            <Button
              type="button"
              variant="destructive"
              className="h-12 px-4 text-base"
              disabled={pending}
              onClick={() => setMode({ action: "HIDE" })}
              aria-label={`Hide: ${item.title}`}
            >
              Hide
            </Button>
          )}
        </div>
      )}
    </li>
  )
}

/**
 * `/approvals` on the admin host (D-134, D-136, W9): everything waiting for the green tick,
 * newest first, with a tab per type and a count on each. Built for a 390 px phone. Approve gives
 * "Verified by Doorway Living"; Reject leaves the item visible with no tick; Hide is for abuse.
 * With `reports`, it is `/reports`: open reports only, answered with Dismiss or Hide (D-51).
 */
export function ApprovalsPage({ reports = false }: { reports?: boolean }) {
  const [params, setParams] = useSearchParams()
  const picked = params.get("type") as QueueType | null
  const type: QueueType | null = reports
    ? "REPORT"
    : QUEUE_TABS.some((t) => t.type === picked)
      ? picked
      : null
  const queryClient = useQueryClient()
  const queryKey = [...adminKeys.queue, type ?? "ALL"] as const
  const queue = useQuery({
    queryKey,
    queryFn: ({ signal }) => fetchQueue({ type }, signal),
    refetchInterval: REFRESH_MS,
  })
  const [busy, setBusy] = useState<string | null>(null)
  const decide = useMutation({
    mutationFn: (v: { item: QueueItem; action: QueueAction; body?: DecisionBody }) =>
      decideQueueItem(v.item, v.action, v.body),
    onMutate: (v) => setBusy(`${v.item.type}-${v.item.id}`),
    onSuccess: (_, v) => {
      toast.success(doneMessage(v.item.type, v.action, BRAND.name))
      // Drop the card at once; the refetch brings the counts up to date.
      queryClient.setQueryData<QueueView>(queryKey, (old) =>
        old
          ? {
              ...old,
              items: old.items.filter((i) => !(i.type === v.item.type && i.id === v.item.id)),
            }
          : old,
      )
      void queryClient.invalidateQueries({ queryKey: adminKeys.queue })
    },
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => setBusy(null),
  })
  // "5 min ago" counts from the last fetch, which refreshes every 30 s.
  const now = queue.dataUpdatedAt
  const counts = queue.data?.counts ?? {}
  const total = QUEUE_TABS.reduce((sum, t) => sum + (counts[t.type] ?? 0), 0)

  const tab = (value: QueueType | null, label: string, count: number) => {
    const active = type === value
    return (
      <button
        key={value ?? "ALL"}
        type="button"
        aria-pressed={active}
        onClick={() => {
          const next = new URLSearchParams(params)
          if (value) next.set("type", value)
          else next.delete("type")
          setParams(next, { replace: true })
        }}
        className={cn(
          "inline-flex h-10 shrink-0 items-center gap-1.5 rounded-full border px-3.5 text-sm font-semibold",
          active
            ? "border-ink-900 bg-ink-900 text-paper"
            : "border-line-strong bg-surface text-ink-700",
        )}
      >
        {label}
        <span className={cn("text-xs", active ? "text-paper/80" : "text-ink-500")}>{count}</span>
      </button>
    )
  }

  return (
    <main className="mx-auto w-full max-w-2xl px-4 py-5 nav:py-8">
      <h1 className="font-display text-2xl font-semibold text-ink-900">
        {reports ? "Reports" : "Approvals"}
      </h1>
      <p className="mt-1 text-sm text-ink-500">
        {reports
          ? "Newest first. Dismiss keeps the item as it is. Hide removes a review from the site, for abuse only."
          : `Newest first. Approve gives the green tick “Verified by ${BRAND.name}”. Reject keeps the item on the site with no tick.`}
      </p>
      {!reports && (
        <div
          role="group"
          aria-label="Filter by type"
          className="-mx-4 mt-4 flex gap-2 overflow-x-auto px-4 pb-1"
        >
          {tab(null, "All", total)}
          {QUEUE_TABS.map((t) => tab(t.type, t.label, counts[t.type] ?? 0))}
        </div>
      )}

      {queue.isPending && (
        <div aria-hidden className="mt-5 space-y-3">
          <div className="h-44 animate-pulse rounded-xl bg-sunk" />
          <div className="h-44 animate-pulse rounded-xl bg-sunk" />
        </div>
      )}
      {queue.isError && (
        <ErrorState className="mt-5" error={queue.error} onRetry={() => void queue.refetch()} />
      )}
      {queue.isSuccess && queue.data.items.length === 0 && (
        <EmptyState
          className="mt-5"
          icon="shield-check"
          title={reports ? "No open reports" : "Nothing waiting"}
          description={
            reports
              ? "Reports people send about reviews, users, homes and tenancies show up here."
              : "New users, ID checks, homes, tenancies, reviews and cities show up here."
          }
        />
      )}
      {decide.isError && (
        <Notice tone="danger" className="mt-4">
          {errorMessage(decide.error)}
        </Notice>
      )}
      {queue.isSuccess && queue.data.items.length > 0 && (
        <ul className="mt-4 space-y-3">
          {queue.data.items.map((item) => (
            <QueueCard
              key={`${item.type}-${item.id}`}
              item={item}
              now={now}
              pending={busy === `${item.type}-${item.id}`}
              onDecide={(action, body) => decide.mutate({ item, action, body })}
            />
          ))}
        </ul>
      )}
    </main>
  )
}
