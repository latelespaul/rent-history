import { useState, type FormEvent } from "react"
import { useMutation } from "@tanstack/react-query"
import { Link, useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { deleteAccount } from "@/api/auth"
import { isApiError } from "@/api/errors"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Notice } from "@/components/ui/notice"
import { errorMessage } from "@/components/ui/states"
import { useAuth } from "@/features/auth/authContext"

/** The word the user types to confirm (D-157). */
const CONFIRM_WORD = "DELETE"

const REMOVED = [
  "Your name, email, phone number and Google photo",
  "Your phone, Aadhaar, PAN and LinkedIn checks, and your verification requests",
  "Owner contact details you typed for homes you added",
  "Share links, enquiries and requests nobody has confirmed yet",
  "Every session: you are signed out on all your devices",
]

const KEPT = [
  "Reviews and ratings you wrote, and reviews others wrote about you",
  "Tenancies and rent history, so other people's records stay complete",
  "Homes you added or own; the owner is removed from them",
]

/**
 * `/settings/delete-account` (D-58, D-157 – D-160): what is removed and what stays as "Deleted user",
 * then type DELETE. The API also needs a sign-in from the last 10 minutes; when it is older, the page
 * asks the user to sign in again and comes back here. Deletion is immediate and final (no grace period).
 */
export default function DeleteAccountPage() {
  const { state, signOut } = useAuth()
  const navigate = useNavigate()
  const [confirm, setConfirm] = useState("")
  const remove = useMutation({
    mutationFn: () => deleteAccount(confirm.trim()),
    onSuccess: () => {
      // Leave first, or the guard would send the signed-out user to /login.
      navigate("/", { replace: true, state: { signedOut: true } })
      void signOut()
      toast.success("Your account is deleted.", { duration: 6000 })
    },
  })
  if (!state.user) return null
  const reauth = isApiError(remove.error) && remove.error.code === "reauth_required"
  const ready = confirm.trim() === CONFIRM_WORD

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (ready && !remove.isPending) remove.mutate()
  }

  return (
    <main>
      <Container className="max-w-[720px] space-y-6 py-10 nav:py-14">
        <div>
          <Link to="/settings" className="text-sm font-semibold">
            ← Settings
          </Link>
          <h1 className="mt-2 font-display text-3xl font-semibold text-ink-900">
            Delete my account
          </h1>
          <p className="mt-1 text-ink-500">
            This happens at once and cannot be undone. Signing up again later starts a new, empty
            account.
          </p>
        </div>

        <section
          aria-labelledby="delete-removed"
          className="rounded-xl border border-line bg-surface p-6 shadow-sm"
          data-testid="delete-removed"
        >
          <h2 id="delete-removed" className="text-xl font-semibold text-ink-900">
            Removed
          </h2>
          <ul className="mt-3 list-disc space-y-1.5 pl-5 text-sm text-ink-700">
            {REMOVED.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>

        <section
          aria-labelledby="delete-kept"
          className="rounded-xl border border-line bg-surface p-6 shadow-sm"
          data-testid="delete-kept"
        >
          <h2 id="delete-kept" className="text-xl font-semibold text-ink-900">
            Kept as “Deleted user”
          </h2>
          <p className="mt-1 text-sm text-ink-500">
            These stay so other people's records stay intact. Your name is never shown on them.
          </p>
          <ul className="mt-3 list-disc space-y-1.5 pl-5 text-sm text-ink-700">
            {KEPT.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          <p className="mt-3 text-sm text-ink-700">
            To remove a review's text too, withdraw it first from{" "}
            <Link to="/me/tenancies" className="font-semibold">
              My tenancies
            </Link>
            .
          </p>
        </section>

        <form
          noValidate
          onSubmit={onSubmit}
          className="space-y-4 rounded-xl border border-danger-600/40 bg-surface p-6 shadow-sm"
        >
          <div className="space-y-1.5">
            <Label htmlFor="delete-confirm">Type DELETE to confirm</Label>
            <Input
              id="delete-confirm"
              value={confirm}
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
              maxLength={16}
              onChange={(e) => setConfirm(e.target.value)}
            />
          </div>
          {reauth ? (
            <Notice tone="warn">
              <span className="block">
                For your safety, sign in again, then delete your account. You come back to this
                page.
              </span>
              <Button
                type="button"
                variant="outline"
                className="mt-3"
                data-testid="delete-reauth"
                onClick={() => void signOut()}
              >
                Sign in again
              </Button>
            </Notice>
          ) : (
            remove.isError && <Notice tone="danger">{errorMessage(remove.error)}</Notice>
          )}
          <Button
            type="submit"
            disabled={!ready || remove.isPending}
            className="bg-danger-600 text-white hover:bg-danger-600/90"
            data-testid="delete-submit"
          >
            {remove.isPending ? "Deleting…" : "Delete my account"}
          </Button>
        </form>
      </Container>
    </main>
  )
}
