import { Link } from "react-router-dom"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/states"
import { BRAND } from "@/lib/brand"

/** `/terms` and `/privacy`, linked from the landing page, until their text is written. */
export default function LegalPage({ title }: { title: string }) {
  return (
    <main>
      <Container className="py-16">
        <EmptyState
          icon="clock"
          title={title}
          description={`The ${BRAND.name} ${title.toLowerCase()} page is coming soon.`}
          action={
            <Button asChild variant="outline">
              <Link to="/">Back to home</Link>
            </Button>
          }
        />
      </Container>
    </main>
  )
}
