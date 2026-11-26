import { ChevronRightIcon, ShareIcon } from "@/components/ui/icons.gen"
import { cn } from "@/lib/utils"
import { useInvite } from "./useInvite"

/**
 * "Help the next tenant" (design note `invite-friends.md` §6): one bordered row, as the home link
 * above it on the review-submitted screen. A tap opens the share sheet.
 */
export function InviteCard({ className }: { className?: string }) {
  const { invite, sheet } = useInvite()
  return (
    <>
      <button
        type="button"
        onClick={() => void invite()}
        data-testid="invite-card"
        className={cn(
          "flex w-full items-center gap-3 rounded-lg border border-line p-3 text-left text-ink-900 hover:border-teal-600",
          className,
        )}
      >
        <span className="grid size-11 shrink-0 place-items-center rounded-md bg-teal-50 text-teal-700">
          <ShareIcon size={20} />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block font-semibold">Help the next tenant</span>
          <span className="block text-sm text-ink-500">
            Invite a friend to check their landlord before they pay a deposit.
          </span>
        </span>
        <ChevronRightIcon size={20} className="shrink-0 text-ink-500" />
      </button>
      {sheet}
    </>
  )
}
