import { BRAND } from "@/lib/brand"

/**
 * "Invite friends" (design note `invite-friends.md` §3, §6). The link always points at the site
 * root, never at a home or review page: a link to the home a user just reviewed would tie them to
 * that review (IF-4).
 */
export function inviteLink(origin: string, code?: string | null): string {
  const root = `${origin.replace(/\/+$/, "")}/`
  return code ? `${root}?ref=${encodeURIComponent(code)}` : root
}

/** "1 friend joined", "3 friends joined". */
export function joinedLabel(joined: number): string {
  return `${joined} ${joined === 1 ? "friend" : "friends"} joined`
}

/** The prefilled text (IF-9): honest claims only, two short sentences and the link. */
export function inviteMessage(link: string): string {
  return (
    `Renting in Bangalore or Pune? Check the landlord on ${BRAND.name} before you pay a deposit. ` +
    `Tenants review anonymously. ${link}`
  )
}

/** WhatsApp's share link: it opens the app (or WhatsApp Web) with the text ready to send. */
export function whatsappUrl(text: string): string {
  return `https://wa.me/?text=${encodeURIComponent(text)}`
}

export type ShareResult = "shared" | "cancelled" | "fallback"

/**
 * Opens the phone's own share sheet. "fallback" means the browser has none, or it failed: the
 * caller then shows WhatsApp and Copy link. Closing the share sheet is "cancelled", not a failure.
 */
export async function shareInvite(text: string, nav: Navigator = navigator): Promise<ShareResult> {
  if (typeof nav.share !== "function") return "fallback"
  const data = { text }
  if (typeof nav.canShare === "function" && !nav.canShare(data)) return "fallback"
  try {
    await nav.share(data)
    return "shared"
  } catch (error) {
    return error instanceof DOMException && error.name === "AbortError" ? "cancelled" : "fallback"
  }
}
