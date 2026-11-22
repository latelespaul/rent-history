import { Link } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { LockIcon } from "@/components/ui/icons.gen"

/** A locked section for anonymous visitors (D-56): what signing in shows, and the way in. */
export function SignInPrompt({ what }: { what: string }) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-lg bg-sunk p-4 sm:flex-row sm:items-center">
      <LockIcon size={20} className="text-ink-500" />
      <p className="flex-1 text-sm text-ink-700">{what}</p>
      <Button asChild variant="outline" size="sm">
        <Link to="/login">Sign in</Link>
      </Button>
    </div>
  )
}
