import { Link } from "react-router-dom"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/states"

/** Signed-in pages the nav links to before their screens exist (dashboard, tenancies, admin). */
export default function ComingSoonPage({ title }: { title: string }) {
  return (
    <main>
      <Container className="py-16">
        <EmptyState
          icon="clock"
          title={title}
          description="This page is coming soon. Meanwhile you can search homes or update your settings."
          action={
            <>
              <Button asChild>
                <Link to="/search">Search</Link>
              </Button>
              <Button asChild variant="outline">
                <Link to="/settings">Settings</Link>
              </Button>
            </>
          }
        />
      </Container>
    </main>
  )
}
