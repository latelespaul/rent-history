import { RouterProvider } from "react-router-dom"
import { QueryProvider } from "@/components/common/QueryProvider"
import { ErrorBoundary } from "@/components/common/ErrorBoundary"
import { AuthProvider } from "@/features/auth/AuthProvider"
import { router } from "@/routes/router"

export default function App() {
  return (
    <ErrorBoundary>
      <QueryProvider>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </QueryProvider>
    </ErrorBoundary>
  )
}
