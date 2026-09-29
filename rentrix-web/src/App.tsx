import { RouterProvider } from "react-router-dom"
import { QueryProvider } from "@/components/common/QueryProvider"
import { ErrorBoundary } from "@/components/common/ErrorBoundary"
import { ThemeProvider } from "@/components/common/ThemeProvider"
import { router } from "@/routes/router"

export default function App() {
  return (
    <ErrorBoundary>
      <ThemeProvider
        attribute="class"
        defaultTheme="dark"
        enableSystem={false}
        disableTransitionOnChange
      >
        <QueryProvider>
          <RouterProvider router={router} />
        </QueryProvider>
      </ThemeProvider>
    </ErrorBoundary>
  )
}
