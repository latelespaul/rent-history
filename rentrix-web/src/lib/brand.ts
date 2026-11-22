/**
 * The product brand users see: page title, logo text, copy and meta tags. Every user-visible
 * brand string comes from here (index.html too, through vite.config.ts), so a rename is one edit.
 */
export const BRAND = {
  name: "Doorway Living",
  tagline: "Real rents and reviews from real tenants and owners. Green tick = verified.",
  webDomain: "doorwayliving.in",
  apiDomain: "api.doorwayliving.in",
} as const
