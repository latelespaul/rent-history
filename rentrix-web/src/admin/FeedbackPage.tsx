import { useState } from "react"
import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { adminKeys, fetchFeedback, type FeedbackEntry } from "./api"

const TYPE_LABEL: Record<FeedbackEntry["type"], string> = {
  FEEDBACK: "Feedback",
  BUG: "Bug",
  IDEA: "Idea",
  OTHER: "Other",
}

function Row({ entry }: { entry: FeedbackEntry }) {
  return (
    <li
      className="rounded-xl border border-line bg-surface p-4 shadow-sm"
      data-testid="feedback-row"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Badge>{TYPE_LABEL[entry.type]}</Badge>
        <time dateTime={entry.createdAt} className="text-sm text-ink-500">
          {new Date(entry.createdAt).toLocaleString()}
        </time>
      </div>
      <p className="mt-2 text-sm whitespace-pre-wrap text-ink-900">{entry.message}</p>
      <p className="mt-2 text-sm break-all text-ink-500">
        {entry.name ?? "No name"}
        {" · "}
        {entry.email ? (
          <a href={`mailto:${entry.email}`} className="text-teal-700">
            {entry.email}
          </a>
        ) : (
          "no email"
        )}
      </p>
    </li>
  )
}

/** `/feedback`: messages from the "Contact us" form, newest first. Replies go by email. */
export function FeedbackPage() {
  const [page, setPage] = useState(0)
  const list = useQuery({
    queryKey: adminKeys.feedback(page),
    queryFn: ({ signal }) => fetchFeedback(page, signal),
    placeholderData: keepPreviousData,
  })
  const pages = list.data?.page.totalPages ?? 0

  return (
    <main className="mx-auto w-full max-w-2xl px-4 py-5 nav:py-8">
      <h1 className="font-display text-2xl font-semibold text-ink-900">Feedback</h1>
      <p className="mt-1 text-sm text-ink-500">Messages from the Contact us form, newest first.</p>

      {list.isError && (
        <ErrorState className="mt-5" error={list.error} onRetry={() => void list.refetch()} />
      )}
      {list.isPending && (
        <div aria-hidden className="mt-5 space-y-3">
          <div className="h-20 animate-pulse rounded-xl bg-sunk" />
          <div className="h-20 animate-pulse rounded-xl bg-sunk" />
        </div>
      )}
      {list.isSuccess && list.data.items.length === 0 && (
        <EmptyState
          className="mt-5"
          icon="reply"
          title="No messages yet"
          description="Messages show up here when someone sends the Contact us form."
        />
      )}
      {list.isSuccess && list.data.items.length > 0 && (
        <ul className="mt-5 space-y-3">
          {list.data.items.map((entry, i) => (
            <Row key={`${entry.createdAt}-${i}`} entry={entry} />
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
