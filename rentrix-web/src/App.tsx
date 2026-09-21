import { RouterProvider } from "react-router-dom"
import { QueryProvider } from "@/components/common/QueryProvider"
import { ErrorBoundary } from "@/components/common/ErrorBoundary"
import { router } from "@/routes/router"

export default function App() {
  return (
    <ErrorBoundary>
      <QueryProvider>
        <RouterProvider router={router} />
      </QueryProvider>
    </ErrorBoundary>
  )
}