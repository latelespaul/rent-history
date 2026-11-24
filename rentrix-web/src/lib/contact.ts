import type { IconName } from "@/components/ui/icon"

/**
 * How people reach Doorway Living (the `/contact` page). The email forwards to the team inbox,
 * so only this public address is ever shown.
 */
export const CONTACT = {
  email: "hello@doorwayliving.in",
  linkedinUrl: "https://www.linkedin.com/company/doorwayliving/",
} as const

export interface SocialLink {
  name: string
  /** Empty until the account exists; the page hides an entry without a URL. */
  url: string
  /** An icon of our own set: never a third-party logo (DESIGN.md §5, §8.2). */
  icon: IconName
}

/** Every social account, in page order. Add Instagram or X by filling in its `url`. */
export const SOCIAL_LINKS: readonly SocialLink[] = [
  { name: "LinkedIn", url: CONTACT.linkedinUrl, icon: "link" },
  { name: "Instagram", url: "", icon: "link" },
  { name: "X (Twitter)", url: "", icon: "link" },
]

/** The entries the page shows: only those with a URL. */
export function visibleSocialLinks(links: readonly SocialLink[] = SOCIAL_LINKS): SocialLink[] {
  return links.filter((link) => link.url.trim() !== "")
}
