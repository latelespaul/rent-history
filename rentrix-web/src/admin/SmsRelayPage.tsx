import { useEffect, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { toast } from "sonner"
import { isApiError } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { CheckIcon, ClockIcon, MailIcon } from "@/components/ui/icons.gen"
import { Notice } from "@/components/ui/notice"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { adminKeys, fetchSmsRelay, type RelayedCode } from "./api"
import { expiryLabel } from "./relayTime"

/** How often the list reloads, so a new code shows up while the admin is on the phone. */
const REFRESH_MS = 10_000

function useNow(intervalMs: number): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), intervalMs)
    return () => window.clearInterval(id)
  }, [intervalMs])
  return now
}

function CodeCard({ code, now }: { code: RelayedCode; now: number }) {
  const [copied, setCopied] = useState(false)
  const expired = Date.parse(code.expiresAt) <= now
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code.code)
      setCopied(true)
      toast.success("Code copied")
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error("Could not copy. Select the code and copy it by hand.")
    }
  }
  return (
    <li className="rounded-xl border border-line bg-surface p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-semibold text-ink-900">
            {code.displayName ?? "Unknown user"}
          </p>
          {code.email && (
            <p className="mt-0.5 flex items-center gap-1 truncate text-sm text-ink-500">
              <MailIcon size={14} className="shrink-0" />
              <span className="truncate">{code.email}</span>
            </p>
          )}
          <p className="mt-0.5 text-sm text-ink-700">{code.phone}</p>
        </div>
        <p
          className={
            expired
              ? "flex shrink-0 items-center gap-1 text-sm font-semibold text-danger-600"
              : "flex shrink-0 items-center gap-1 text-sm text-ink-500"
          }
        >
          <ClockIcon size={14} />
          {expiryLabel(code.expiresAt, now)}
        </p>
      </div>
      <div className="mt-3 flex items-center gap-3">
        <p
          aria-label={`Code ${code.code.split("").join(" ")}`}
          className="flex-1 font-mono text-3xl font-semibold tracking-[0.2em] text-ink-900 select-all"
        >
          {code.code}
        </p>
        <Button
          type="button"
          variant={copied ? "outline" : "default"}
          className="h-12 min-w-28 px-5 text-base"
          onClick={() => void copy()}
          aria-label={`Copy code for ${code.displayName ?? code.phone}`}
        >
          {copied ? <CheckIcon /> : null}
          {copied ? "Copied" : "Copy"}
        </Button>
      </div>
    </li>
  )
}

/**
 * `/sms-relay` (D-137): the phone codes the preview-only `admin-relay` SMS sender holds, newest
 * first, reloaded every 10 s. The admin copies a code and passes it to the user (WhatsApp or SMS).
 */
export function SmsRelayPage() {
  const now = useNow(5_000)
  const relay = useQuery({
    queryKey: adminKeys.smsRelay,
    queryFn: ({ signal }) => fetchSmsRelay(signal),
    refetchInterval: REFRESH_MS,
    retry: (count, error) => !(isApiError(error) && error.status === 404) && count < 2,
  })
  const off = isApiError(relay.error) && relay.error.status === 404

  return (
    <main className="mx-auto w-full max-w-2xl px-4 py-5 nav:py-8">
      <h1 className="font-display text-2xl font-semibold text-ink-900">OTP relay</h1>
      <p className="mt-1 text-sm text-ink-500">
        Phone codes waiting to be passed on. Send each one to its user on WhatsApp or SMS.
      </p>
      <p role="status" aria-live="polite" className="mt-1 text-xs text-ink-500">
        {relay.dataUpdatedAt > 0
          ? `Updated ${new Date(relay.dataUpdatedAt).toLocaleTimeString()} · reloads every 10 s`
          : "Loading…"}
      </p>

      {off && (
        <Notice tone="info" className="mt-5">
          The OTP relay is off. It works only when the API runs with SMS_BACKEND=admin-relay;
          otherwise codes go out by SMS.
        </Notice>
      )}
      {relay.isError && !off && (
        <ErrorState className="mt-5" error={relay.error} onRetry={() => void relay.refetch()} />
      )}
      {relay.isPending && (
        <div aria-hidden className="mt-5 space-y-3">
          <div className="h-32 animate-pulse rounded-xl bg-sunk" />
          <div className="h-32 animate-pulse rounded-xl bg-sunk" />
        </div>
      )}
      {relay.isSuccess && relay.data.length === 0 && (
        <EmptyState
          className="mt-5"
          icon="message"
          title="No codes waiting"
          description="A code shows up here when someone asks to verify their phone."
        />
      )}
      {relay.isSuccess && relay.data.length > 0 && (
        <ul className="mt-5 space-y-3">
          {relay.data.map((code) => (
            <CodeCard key={`${code.phone}-${code.sentAt}`} code={code} now={now} />
          ))}
        </ul>
      )}
    </main>
  )
}
