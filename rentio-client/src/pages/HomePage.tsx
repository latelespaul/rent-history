import { Link } from "react-router-dom"
import { Button } from "@/components/ui/button"

export default function HomePage() {
  return (
    <section className="container mx-auto flex flex-col items-center gap-6 px-4 py-24 text-center">
      <h1 className="text-4xl font-bold tracking-tight">Find your next home with confidence</h1>
      <p className="max-w-xl text-muted-foreground">
        Honest reviews from real tenants. Ratings on rent, area, and landlord behaviour.
      </p>
      <Button asChild size="lg">
        <Link to="/flats">Browse flats</Link>
      </Button>
    </section>
  )
}
