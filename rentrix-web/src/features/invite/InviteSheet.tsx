import { useRef } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { LinkIcon, MessageIcon, UsersIcon } from "@/components/ui/icons.gen"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { inviteMessage, joinedLabel, whatsappUrl } from "./invite"

/**
 * The fallback when the browser has no share sheet (design note `invite-friends.md` §6): the
 * message, WhatsApp and Copy link. A bottom sheet, so it sits under the thumb on a phone.
 */
export function InviteSheet({
  open,
  onOpenChange,
  link,
  joined = null,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Null while the user's own link is still loading. */
  link: string | null
  /** How many people joined with the user's code; null when unknown. */
  joined?: number | null
}) {
  const field = useRef<HTMLInputElement>(null)
  const message = link ? inviteMessage(link) : null

  async function copy() {
    if (!link) return
    try {
      await navigator.clipboard.writeText(link)
      toast.success("Link copied")
    } catch {
      field.current?.select()
      toast.error("Could not copy. Press and hold the link to copy it.")
    }
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="bottom"
        className="mx-auto w-full max-w-[480px] gap-0 rounded-t-xl bg-surface p-0 sm:bottom-6 sm:rounded-xl sm:border"
        data-testid="invite-sheet"
      >
        <SheetHeader className="flex-row items-center gap-3 border-b border-line p-4 pr-12">
          <span className="grid size-10 shrink-0 place-items-center rounded-full bg-teal-50 text-teal-700">
            <UsersIcon size={20} />
          </span>
          <span className="min-w-0">
            <SheetTitle className="text-base font-semibold text-ink-900">Invite friends</SheetTitle>
            <SheetDescription className="text-xs text-ink-500" data-testid="invite-joined">
              {joined === null ? "Share it with someone who rents." : joinedLabel(joined)}
            </SheetDescription>
          </span>
        </SheetHeader>
        <div className="space-y-3 p-4">
          <p
            data-testid="invite-message"
            className="rounded-lg border border-line bg-background p-3 text-sm text-ink-700"
          >
            {message ?? "Getting your link…"}
          </p>
          <div className="grid grid-cols-2 gap-2">
            {message ? (
              <Button asChild variant="outline" data-testid="invite-whatsapp">
                <a href={whatsappUrl(message)} target="_blank" rel="noopener noreferrer">
                  <MessageIcon />
                  WhatsApp
                </a>
              </Button>
            ) : (
              <Button variant="outline" disabled data-testid="invite-whatsapp">
                <MessageIcon />
                WhatsApp
              </Button>
            )}
            <Button
              variant="outline"
              disabled={!link}
              onClick={() => void copy()}
              data-testid="invite-copy"
            >
              <LinkIcon />
              Copy link
            </Button>
          </div>
          <label className="block">
            <span className="sr-only">Invite link</span>
            <input
              ref={field}
              readOnly
              value={link ?? ""}
              onFocus={(e) => e.currentTarget.select()}
              className="h-10 w-full rounded-md border border-line bg-sunk px-3 text-sm text-ink-700"
            />
          </label>
        </div>
      </SheetContent>
    </Sheet>
  )
}
