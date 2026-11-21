import { isRouteErrorResponse, Link, useRouteError } from "react-router-dom"
import { Brand } from "@/components/layout/Brand"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { ErrorState } from "@/components/ui/states"

/** Router-level failure (loader, render): the error state under a bare brand bar. */
export function RouteError() {
  const error = useRouteError()
  const response = isRouteErrorResponse(error) ? error : null

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-line bg-surface">
        <Container className="flex h-topbar items-center">
          <Brand />
        </Container>
      </header>
      <main>
        <Container className="py-16">
          <ErrorState
            title={response ? `${response.status} ${response.statusText}` : undefined}
            error={response ? undefined : error}
            description={
              response ? (response.data?.message ?? "The page could not be loaded.") : undefined
            }
            action={
              <Button asChild>
                <Link to="/">Go home</Link>
              </Button>
            }
          />
        </Container>
      </main>
    </div>
  )
}
