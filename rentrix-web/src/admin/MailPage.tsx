import { useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { Button } from "@/components/ui/button"
import { Notice } from "@/components/ui/notice"
import { ErrorState } from "@/components/ui/states"
import { sendReviewReminder, type ReminderResult } from "./api"

function users(n: number): string {
  return n === 1 ? "1 user" : `${n} users`
}

/**
 * `/mail` (D-172): the review reminder. "Count recipients" is a dry run; "Send" then emails each
 * of them once (one email per user, never BCC), with an unsubscribe link. Counts only: the page
 * never shows email addresses.
 */
export function MailPage() {
  const [onlyWithoutReview, setOnlyWithoutReview] = useState(true)
  const [count, setCount] = useState<ReminderResult | null>(null)
  const [done, setDone] = useState<ReminderResult | null>(null)
  const dryRun = useMutation({
    mutationFn: () => sendReviewReminder({ onlyWithoutReview, dryRun: true }),
    onSuccess: (result) => {
      setCount(result)
      setDone(null)
    },
  })
  const send = useMutation({
    mutationFn: () => sendReviewReminder({ onlyWithoutReview, dryRun: false }),
    onSuccess: (result) => {
      setDone(result)
      setCount(null)
    },
  })
  const error = dryRun.error ?? send.error

  return (
    <main className="mx-auto w-full max-w-2xl px-4 py-5 nav:py-8">
      <h1 className="font-display text-2xl font-semibold text-ink-900">Review reminder</h1>
      <p className="mt-1 text-sm text-ink-500">
        A short &ldquo;Rate your landlord&rdquo; email, one per user, with an unsubscribe link.
        Users who unsubscribed or already got it are skipped.
      </p>

      <div className="mt-5 space-y-4 rounded-xl border border-line bg-surface p-4 shadow-sm">
        <label className="flex items-center gap-2 text-sm text-ink-900">
          <input
            type="checkbox"
            data-testid="mail-only-without-review"
            checked={onlyWithoutReview}
            onChange={(e) => {
              setOnlyWithoutReview(e.target.checked)
              setCount(null)
            }}
            className="size-4 accent-teal-600"
          />
          Only users who have not written a review yet
        </label>

        {count === null ? (
          <Button
            type="button"
            variant="outline"
            disabled={dryRun.isPending || send.isPending}
            onClick={() => dryRun.mutate()}
          >
            {dryRun.isPending ? "Counting…" : "Count recipients"}
          </Button>
        ) : (
          <div className="space-y-3" data-testid="mail-count">
            <p className="text-ink-900">
              <b>{users(count.recipients)}</b> would get the reminder.
            </p>
            {!count.mailEnabled && (
              <Notice tone="warn">
                Mail is off on this server (no RESEND_API_KEY), so nothing can be sent.
              </Notice>
            )}
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                disabled={!count.mailEnabled || count.recipients === 0 || send.isPending}
                onClick={() => send.mutate()}
              >
                {send.isPending ? "Sending…" : `Send to ${users(count.recipients)}`}
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={send.isPending}
                onClick={() => setCount(null)}
              >
                Cancel
              </Button>
            </div>
            {send.isPending && (
              <p role="status" className="text-sm text-ink-500">
                Emails go out one by one, about two a second. Keep this page open.
              </p>
            )}
          </div>
        )}

        {done && (
          <Notice tone={done.failed > 0 ? "warn" : "teal"} data-testid="mail-done">
            Sent to {users(done.sent)}
            {done.failed > 0 &&
              `; ${users(done.failed)} failed. Send again later to retry only those.`}
          </Notice>
        )}
      </div>

      {error && <ErrorState className="mt-5" error={error} />}
    </main>
  )
}
