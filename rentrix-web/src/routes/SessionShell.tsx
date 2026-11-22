import { AppLayout } from "@/components/layout/AppLayout"
import { toViewer, useSession } from "@/lib/session"

/** The app shell drawn for the restored session (anonymous until it resolves). */
export function SessionShell() {
  const session = useSession()
  return <AppLayout viewer={toViewer(session.data)} />
}
