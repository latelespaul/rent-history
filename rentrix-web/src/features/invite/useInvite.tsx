import { useCallback, useMemo, useState } from "react"
import { inviteLink, inviteMessage, shareInvite } from "./invite"
import { InviteSheet } from "./InviteSheet"

/**
 * The one invite action every entry point uses: the phone's share sheet first, else the fallback
 * sheet. Render `sheet` once next to the button that calls `invite`.
 */
export function useInvite() {
  const [open, setOpen] = useState(false)
  const link = inviteLink(window.location.origin)
  const invite = useCallback(async () => {
    // Called straight from the tap, so the browser still counts it as a user gesture.
    const result = await shareInvite(inviteMessage(link))
    if (result === "fallback") setOpen(true)
  }, [link])
  const sheet = useMemo(
    () => <InviteSheet open={open} onOpenChange={setOpen} link={link} />,
    [open, link],
  )
  return { invite, sheet }
}
