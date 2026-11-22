import { useEffect, useRef, useState } from "react"
import { Notice } from "@/components/ui/notice"
import { loadGis } from "./gis"

interface Props {
  clientId: string
  /** Receives the Google ID token after the user picks an account. */
  onCredential: (idToken: string) => void
}

/**
 * The official "Sign in with Google" button, rendered by GIS into a placeholder (D-76). The
 * button's look is Google's; Rentrix only sizes it to the card.
 */
export function GoogleButton({ clientId, onCredential }: Props) {
  const slot = useRef<HTMLDivElement>(null)
  const callback = useRef(onCredential)
  const [failed, setFailed] = useState<string | null>(null)

  useEffect(() => {
    callback.current = onCredential
  }, [onCredential])

  useEffect(() => {
    let cancelled = false
    loadGis()
      .then((gis) => {
        if (cancelled || !slot.current) return
        gis.initialize({
          client_id: clientId,
          callback: (response) => callback.current(response.credential),
          ux_mode: "popup",
          auto_select: false,
          cancel_on_tap_outside: true,
        })
        gis.renderButton(slot.current, {
          type: "standard",
          theme: "outline",
          size: "large",
          text: "signin_with",
          shape: "rectangular",
          logo_alignment: "left",
          width: Math.min(400, Math.max(200, Math.round(slot.current.offsetWidth || 320))),
        })
      })
      .catch((error: unknown) => {
        if (!cancelled) setFailed(error instanceof Error ? error.message : String(error))
      })
    return () => {
      cancelled = true
    }
  }, [clientId])

  if (failed) {
    return (
      <Notice tone="danger" className="mt-6">
        {failed}
      </Notice>
    )
  }
  return (
    <div
      ref={slot}
      data-testid="google-button"
      aria-label="Sign in with Google"
      className="mt-6 flex min-h-11 w-full justify-center"
    />
  )
}
