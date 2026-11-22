import { useEffect, useRef, useState } from "react"
import { fetchTruecallerRequest, startTruecaller } from "@/api/verification"
import { Button } from "@/components/ui/button"
import { ShieldCheckIcon } from "@/components/ui/icons.gen"
import { Notice } from "@/components/ui/notice"
import { errorMessage } from "@/components/ui/states"
import type { TruecallerRequest } from "@/types/api"
import { openDeepLink } from "./truecaller"

/** Truecaller's own check: the sheet took focus by now if the app is installed. */
const FOCUS_CHECK_MS = 600
/** With the page still focused, wait this long for Truecaller's "flow invoked" call before giving up. */
const HANDSHAKE_MS = 3000
const POLL_MS = 2000
/** A little longer than the 2-minute sheet timeout the API asks Truecaller for. */
const GIVE_UP_MS = 150_000

const sleep = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))

function fallbackMessage(view: TruecallerRequest | null, appMissing: boolean): string {
  if (view?.status === "REJECTED") return "Truecaller verification was cancelled."
  if (view?.reason === "not_indian_mobile")
    return "Your Truecaller number is not an Indian mobile number."
  if (appMissing && view?.status === "PENDING") return "The Truecaller app did not open."
  if (view?.status === "EXPIRED" || view?.status === "PENDING" || view?.status === "INVOKED")
    return "Truecaller did not answer in time."
  return "Truecaller could not verify your number."
}

/**
 * "Verify with Truecaller", the first phone method (Android only): opens the Truecaller sheet, then polls the API
 * until Truecaller's callback has landed. Any other ending (declined, no app, no answer) falls back to the SMS
 * code below.
 */
export function TruecallerCard({ onVerified }: { onVerified: () => Promise<void> }) {
  const [waiting, setWaiting] = useState(false)
  const [fallback, setFallback] = useState<string | null>(null)
  /** Bumped to cancel a running wait (unmount, cancel, a new start). */
  const run = useRef(0)
  useEffect(() => {
    const runs = run
    return () => {
      runs.current += 1
    }
  }, [])

  const stop = (message: string | null) => {
    run.current += 1
    setWaiting(false)
    setFallback(message)
  }

  const begin = async () => {
    const token = ++run.current
    setFallback(null)
    setWaiting(true)
    let requestId: string
    try {
      const started = await startTruecaller()
      requestId = started.requestId
      openDeepLink(started.deepLink)
    } catch (error) {
      if (run.current === token) stop(errorMessage(error))
      return
    }
    const startedAt = Date.now()
    await sleep(FOCUS_CHECK_MS)
    const appMissing = document.hasFocus()
    while (run.current === token) {
      const view: TruecallerRequest | null = await fetchTruecallerRequest(requestId).catch(
        () => null,
      )
      if (run.current !== token) return
      const elapsed = Date.now() - startedAt
      if (view?.status === "VERIFIED") {
        await onVerified()
        if (run.current === token) stop(null)
        return
      }
      const open =
        view !== null &&
        (view.status === "PENDING" || view.status === "INVOKED" || view.status === "VERIFYING")
      const noApp = appMissing && view?.status === "PENDING" && elapsed >= HANDSHAKE_MS
      if (!open || noApp || elapsed >= GIVE_UP_MS) {
        stop(fallbackMessage(view, appMissing))
        return
      }
      await sleep(POLL_MS)
    }
  }

  return (
    <div className="space-y-2 rounded-lg border border-line p-4">
      <p className="flex items-center gap-2 text-ink-900">
        <ShieldCheckIcon size={18} className="text-teal-700" />
        <b>Verify with Truecaller</b>
      </p>
      <p className="text-sm text-ink-500">
        One tap with the number in your Truecaller app. No code to type.
      </p>
      {waiting ? (
        <div className="flex flex-wrap items-center gap-2">
          <p role="status" className="text-sm text-ink-700">
            Waiting for Truecaller… Tap Continue in the Truecaller sheet.
          </p>
          <Button type="button" variant="ghost" onClick={() => stop(null)}>
            Cancel
          </Button>
        </div>
      ) : (
        <Button type="button" onClick={() => void begin()}>
          Verify with Truecaller
        </Button>
      )}
      {fallback && <Notice tone="warn">{fallback} Verify with an SMS code below instead.</Notice>}
    </div>
  )
}
