import { Suspense, lazy } from "react"
import App from "./App"
import { isAdminHost } from "./lib/config"

// The console is its own chunk, so the main site never downloads it.
const AdminApp = lazy(() => import("./admin/AdminApp"))

/**
 * One build, two hosts (SYSTEM_DESIGN §13.3): admin.* renders the admin console, anything else the
 * main site.
 */
export default function HostRoot() {
  if (!isAdminHost(window.location.hostname)) return <App />
  return (
    <Suspense fallback={null}>
      <AdminApp />
    </Suspense>
  )
}
