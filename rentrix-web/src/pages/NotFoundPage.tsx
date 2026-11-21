import { Link } from "react-router-dom"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/states"

export default function NotFoundPage() {
  return (
    <main>
      <Container className="py-16">
        <EmptyState
          icon="map-pin"
          title="Page not found"
          description="This page does not exist. Check the address, or start again from Home."
          action={
            <>
              <Button asChild>
                <Link to="/">Go home</Link>
              </Button>
              <Button asChild variant="outline">
                <Link to="/search">Search</Link>
              </Button>
            </>
          }
        />
      </Container>
    </main>
  )
}
