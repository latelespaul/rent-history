import { useRouteError, isRouteErrorResponse, Link } from "react-router-dom"
import { AlertTriangle } from "lucide-react"
import { Button } from "@/components/ui/button"

export function RouteError() {
  const error = useRouteError()

  let title = "Something went wrong"
  let message = "An unexpected error occurred."

  if (isRouteErrorResponse(error)) {
    title = `${error.status} ${error.statusText}`
    message = error.data?.message ?? "The page could not be loaded."
  } else if (error instanceof Error) {
    message = error.message
  }

  return (
    <div className="container mx-auto flex flex-col items-center gap-4 px-4 py-24 text-center">
      <AlertTriangle className="h-10 w-10 text-destructive" />
      <div className="space-y-1">
        <h1 className="text-xl font-semibold">{title}</h1>
        <p className="text-sm text-muted-foreground">{message}</p>
      </div>
      <Button asChild>
        <Link to="/">Go home</Link>
      </Button>
    </div>
  )
}
