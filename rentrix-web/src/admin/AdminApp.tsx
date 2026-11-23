import { useState } from "react"
import { RouterProvider } from "react-router-dom"
import { ErrorBoundary } from "@/components/common/ErrorBoundary"
import { QueryProvider } from "@/components/common/QueryProvider"
import { Toaster } from "@/components/ui/sonner"
import { AdminAuthProvider } from "./AdminAuthProvider"
import { createAdminRouter } from "./router"

/** The admin console (SYSTEM_DESIGN §13.3): the same build as the main site, on the admin host. */
export default function AdminApp() {
  const [router] = useState(createAdminRouter)
  return (
    <ErrorBoundary>
      <QueryProvider>
        <AdminAuthProvider>
          <RouterProvider router={router} />
          <Toaster position="top-center" />
        </AdminAuthProvider>
      </QueryProvider>
    </ErrorBoundary>
  )
}
