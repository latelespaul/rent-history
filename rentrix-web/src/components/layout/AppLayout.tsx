import { Outlet } from "react-router-dom"
import { Footer } from "./Footer"
import type { Mode } from "@/features/mode/mode"
import type { ShellViewer } from "./nav"
import { PhoneBanner } from "./PhoneBanner"
import { TopBar } from "./TopBar"

/**
 * The page shell: top bar, page content, footer. Pages render their own `<main>` content inside.
 * `viewer` is the signed-in user (null when anonymous); `onSignOut` adds "Sign out" to the menus;
 * `onModeChange` adds the Tenant | Owner switch.
 */
export function AppLayout({
  viewer = null,
  onSignOut,
  onModeChange,
}: {
  viewer?: ShellViewer | null
  onSignOut?: () => void
  onModeChange?: (mode: Mode) => void
}) {
  return (
    <div className="flex min-h-screen flex-col bg-background">
      <a
        href="#main"
        className="sr-only z-50 rounded-md bg-surface px-3 py-2 focus:not-sr-only focus:fixed focus:top-2 focus:left-2"
      >
        Skip to content
      </a>
      <TopBar viewer={viewer} onSignOut={onSignOut} onModeChange={onModeChange} />
      {viewer?.progress?.kind === "phone" && <PhoneBanner />}
      <div id="main" tabIndex={-1} className="flex-1 outline-none">
        <Outlet />
      </div>
      <Footer />
    </div>
  )
}
