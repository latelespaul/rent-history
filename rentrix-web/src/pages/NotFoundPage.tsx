import { Link } from "react-router-dom"
import { Compass } from "lucide-react"
import { Button } from "@/components/ui/button"

export default function NotFoundPage() {
  return (
    <section className="mx-auto flex flex-col items-center gap-5 px-4 py-24 text-center">
      <Compass className="h-12 w-12 text-muted-foreground" aria-hidden />
      <div className="space-y-2">
        <p className="text-sm font-medium text-muted-foreground">404</p>
        <h1 className="text-3xl font-bold tracking-tight">Page not found</h1>
        <p className="text-muted-foreground">This page does not exist.</p>
      </div>
      <Button asChild variant="outline">
        <Link to="/">Home</Link>
      </Button>
    </section>
  )
}
