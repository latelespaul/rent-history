import { Suspense, lazy } from "react"
import App from "./App"
import { keepRefFromUrl } from "./features/invite/ref"
import { isAdminHost } from "./lib/config"
import { captureLanding } from "./lib/traffic"

// The console is its own chunk, so the main site never downloads it.
const AdminApp = lazy(() => import("./admin/AdminApp"))

/**
 * One build, two hosts (SYSTEM_DESIGN §13.3): admin.* renders the admin console, anything else the
 * main site.
 */
export default function HostRoot() {
  if (!isAdminHost(window.location.hostname)) {
    // Before the router reads the address: note how the visitor arrived, then an invite link's
    // ?ref= is kept and removed.
    captureLanding()
    keepRefFromUrl()
    return <App />
  }
  return (
    <Suspense fallback={null}>
      <AdminApp />
    </Suspense>
  )
}
