import { useContext, useId, useState, type FormEvent } from "react"
import { useMutation } from "@tanstack/react-query"
import { FEEDBACK_MESSAGE_MAX, sendFeedback, type FeedbackType } from "@/api/feedback"
import { Button } from "@/components/ui/button"
import { CheckIcon } from "@/components/ui/icons.gen"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { errorMessage } from "@/components/ui/states"
import { Textarea } from "@/components/ui/textarea"
import { AuthContext } from "@/features/auth/authContext"
import { cn } from "@/lib/utils"

const TYPES: { value: FeedbackType; label: string }[] = [
  { value: "FEEDBACK", label: "Feedback" },
  { value: "BUG", label: "Bug" },
  { value: "IDEA", label: "Idea" },
  { value: "OTHER", label: "Other" },
]

/**
 * The short "Contact us" form, on the landing and on `/contact`: name and email are optional (an
 * email is needed only for a reply) and prefilled for a signed-in user, a type chip and the
 * message. A hidden `website` field catches bots; the API answers them as usual and drops it.
 */
export function FeedbackForm({ className }: { className?: string }) {
  // Read the session without requiring a provider, so the form also works in isolation.
  const user = useContext(AuthContext)?.state.user ?? null
  const id = useId()
  const [name, setName] = useState(user?.displayName ?? "")
  const [email, setEmail] = useState(user?.email ?? "")
  const [type, setType] = useState<FeedbackType>("FEEDBACK")
  const [message, setMessage] = useState("")
  const [website, setWebsite] = useState("")
  const send = useMutation({
    mutationFn: () =>
      sendFeedback({
        name: name.trim() || undefined,
        email: email.trim() || undefined,
        type,
        message: message.trim(),
        website: website || undefined,
      }),
  })

  if (send.isSuccess) {
    return (
      <div
        role="status"
        data-testid="feedback-thanks"
        className={cn(
          "flex flex-col items-center gap-3 rounded-3xl border border-line bg-surface px-6 py-12 text-center",
          className,
        )}
      >
        <span className="grid size-14 place-items-center rounded-full bg-teal-50 text-teal-700">
          <CheckIcon size={28} />
        </span>
        <h3 className="font-display text-2xl font-semibold text-ink-900">Thanks, we got it.</h3>
        <p className="max-w-xs text-ink-700">
          {email.trim() ? "We reply by email." : "We read every message."}
        </p>
        <Button
          type="button"
          variant="ghost"
          className="text-teal-700"
          onClick={() => {
            setMessage("")
            send.reset()
          }}
        >
          Send another
        </Button>
      </div>
    )
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (message.trim() && !send.isPending) send.mutate()
  }

  return (
    <form
      onSubmit={onSubmit}
      aria-label="Send us a message"
      data-testid="feedback-form"
      className={cn(
        "relative grid gap-5 rounded-3xl border border-line bg-surface p-5 lg:p-7",
        className,
      )}
    >
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium text-ink-900">About</legend>
        <div className="flex flex-wrap gap-2">
          {TYPES.map((t) => (
            <button
              key={t.value}
              type="button"
              aria-pressed={type === t.value}
              onClick={() => setType(t.value)}
              className={
                type === t.value
                  ? "h-9 rounded-full border border-teal-600 bg-teal-50 px-3.5 text-sm font-semibold text-teal-800"
                  : "h-9 rounded-full border border-line-strong bg-surface px-3.5 text-sm text-ink-700 hover:border-teal-600"
              }
            >
              {t.label}
            </button>
          ))}
        </div>
      </fieldset>
      <div className="grid gap-5 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor={`${id}-name`}>
            Name <span className="font-normal text-ink-500">(optional)</span>
          </Label>
          <Input
            id={`${id}-name`}
            value={name}
            maxLength={80}
            autoComplete="name"
            onChange={(e) => setName(e.target.value)}
            className="h-11"
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor={`${id}-email`}>
            Email <span className="font-normal text-ink-500">(for a reply)</span>
          </Label>
          <Input
            id={`${id}-email`}
            type="email"
            value={email}
            maxLength={320}
            autoComplete="email"
            onChange={(e) => setEmail(e.target.value)}
            className="h-11"
          />
        </div>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor={`${id}-message`}>Message</Label>
        <Textarea
          id={`${id}-message`}
          required
          value={message}
          maxLength={FEEDBACK_MESSAGE_MAX}
          rows={4}
          onChange={(e) => setMessage(e.target.value)}
          className="min-h-28"
        />
        <p className="text-right text-xs text-ink-500" aria-live="polite">
          {message.length}/{FEEDBACK_MESSAGE_MAX}
        </p>
      </div>
      {/* The honeypot: off screen and out of the tab order, so only bots fill it. */}
      <div aria-hidden className="absolute -left-[9999px] h-px w-px overflow-hidden">
        <label htmlFor={`${id}-website`}>Website</label>
        <input
          id={`${id}-website`}
          name="website"
          tabIndex={-1}
          autoComplete="off"
          value={website}
          onChange={(e) => setWebsite(e.target.value)}
        />
      </div>
      {send.isError && (
        <p role="alert" className="text-sm text-danger-600">
          {errorMessage(send.error)}
        </p>
      )}
      <Button
        type="submit"
        variant="accent"
        size="lg"
        disabled={send.isPending || !message.trim()}
        className="h-[52px] rounded-xl sm:justify-self-start"
        data-testid="feedback-send"
      >
        {send.isPending ? "Sending…" : "Send message"}
      </Button>
    </form>
  )
}
