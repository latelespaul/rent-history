import { useCallback, useMemo, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { fetchInvite, inviteKeys } from "@/api/invite"
import { inviteLink, inviteMessage, shareInvite } from "./invite"
import { InviteSheet } from "./InviteSheet"

/**
 * The one invite action every entry point uses: the phone's share sheet first, else the fallback
 * sheet. Render `sheet` once next to the button that calls `invite`. The link carries the user's
 * own code (`GET /me/invite`, invite-friends §3); `enabled: false` (signed out) skips the call.
 */
export function useInvite({ enabled = true }: { enabled?: boolean } = {}) {
  const [open, setOpen] = useState(false)
  const mine = useQuery({
    queryKey: inviteKeys.mine,
    queryFn: ({ signal }) => fetchInvite(signal),
    enabled,
    staleTime: 60_000,
  })
  // Without a code (still loading) there is no link yet; if the call failed, the plain site link
  // still helps, it just counts nobody.
  const origin = window.location.origin
  const link = mine.data
    ? inviteLink(origin, mine.data.code)
    : mine.isError
      ? inviteLink(origin)
      : null
  const invite = useCallback(async () => {
    if (!link) {
      setOpen(true)
      return
    }
    // Called straight from the tap, so the browser still counts it as a user gesture.
    const result = await shareInvite(inviteMessage(link))
    if (result === "fallback") setOpen(true)
  }, [link])
  const joined = mine.data?.joined ?? null
  const sheet = useMemo(
    () => <InviteSheet open={open} onOpenChange={setOpen} link={link} joined={joined} />,
    [open, link, joined],
  )
  return { invite, sheet, joined }
}
