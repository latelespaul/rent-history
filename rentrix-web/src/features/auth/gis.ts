/**
 * Google Identity Services (GIS), loaded only when `GET /auth/config` returns a client id (D-76).
 * The page that hosts the button must be served with
 * `Cross-Origin-Opener-Policy: same-origin-allow-popups`, and a CSP must allow
 * `https://accounts.google.com/gsi/` for script, frame, style and connect.
 */
export const GIS_SCRIPT_URL = "https://accounts.google.com/gsi/client"

export interface GisCredentialResponse {
  /** The Google ID token (a JWT) the API verifies. */
  credential: string
}

export interface GisButtonOptions {
  type?: "standard" | "icon"
  theme?: "outline" | "filled_blue" | "filled_black"
  size?: "large" | "medium" | "small"
  text?: "signin_with" | "signup_with" | "continue_with" | "signin"
  shape?: "rectangular" | "pill" | "circle" | "square"
  logo_alignment?: "left" | "center"
  /** Pixels, at most 400. */
  width?: number
}

export interface GisIdApi {
  initialize(config: {
    client_id: string
    callback: (response: GisCredentialResponse) => void
    ux_mode?: "popup" | "redirect"
    auto_select?: boolean
    cancel_on_tap_outside?: boolean
  }): void
  renderButton(parent: HTMLElement, options: GisButtonOptions): void
  disableAutoSelect(): void
}

declare global {
  interface Window {
    google?: { accounts?: { id?: GisIdApi } }
  }
}

let loading: Promise<GisIdApi> | null = null

/** Adds the GIS script once and resolves with `google.accounts.id`. A failed load can be retried. */
export function loadGis(): Promise<GisIdApi> {
  const ready = window.google?.accounts?.id
  if (ready) return Promise.resolve(ready)
  loading ??= new Promise<GisIdApi>((resolve, reject) => {
    const script = document.createElement("script")
    script.src = GIS_SCRIPT_URL
    script.async = true
    script.defer = true
    script.onload = () => {
      const id = window.google?.accounts?.id
      if (id) resolve(id)
      else reject(new Error("Google sign-in did not load."))
    }
    script.onerror = () => {
      script.remove()
      reject(new Error("Could not load Google sign-in. Check your connection and try again."))
    }
    document.head.appendChild(script)
  }).catch((error: unknown) => {
    loading = null
    throw error
  })
  return loading
}
