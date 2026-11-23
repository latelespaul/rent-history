import { Navigate, createBrowserRouter, type RouteObject } from "react-router-dom"
import { EmptyState } from "@/components/ui/states"
import { RouteError } from "@/routes/RouteError"
import { AdminLoginPage } from "./AdminLoginPage"
import { AdminShell } from "./AdminShell"
import { ApprovalsPage } from "./ApprovalsPage"
import { SmsRelayPage } from "./SmsRelayPage"

/** The admin host's routes (SYSTEM_DESIGN §13.3); the main site's pages are not here. */
export const adminRoutes: RouteObject[] = [
  { path: "/login", element: <AdminLoginPage />, errorElement: <RouteError /> },
  {
    element: <AdminShell />,
    errorElement: <RouteError />,
    children: [
      { path: "/", element: <Navigate to="/approvals" replace /> },
      { path: "/approvals", element: <ApprovalsPage /> },
      { path: "/reports", element: <ApprovalsPage reports /> },
      { path: "/sms-relay", element: <SmsRelayPage /> },
      {
        path: "*",
        element: (
          <main className="mx-auto w-full max-w-2xl px-4 py-8">
            <EmptyState
              icon="search"
              title="Page not found"
              description="This admin page does not exist."
            />
          </main>
        ),
      },
    ],
  },
]

export function createAdminRouter() {
  return createBrowserRouter(adminRoutes)
}
