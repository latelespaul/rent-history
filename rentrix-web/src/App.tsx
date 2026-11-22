import { RouterProvider } from "react-router-dom"
import { QueryProvider } from "@/components/common/QueryProvider"
import { ErrorBoundary } from "@/components/common/ErrorBoundary"
import { Toaster } from "@/components/ui/sonner"
import { AuthProvider } from "@/features/auth/AuthProvider"
import { router } from "@/routes/router"

export default function App() {
  return (
    <ErrorBoundary>
      <QueryProvider>
        <AuthProvider>
          <RouterProvider router={router} />
          <Toaster position="top-center" />
        </AuthProvider>
      </QueryProvider>
    </ErrorBoundary>
  )
}
