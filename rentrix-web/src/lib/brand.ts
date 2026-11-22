/**
 * The product brand users see: page title, logo text, copy and meta tags. Every user-visible
 * brand string comes from here (index.html too, through vite.config.ts), so a rename is one edit.
 */
export const BRAND = {
  name: "Doorway Living",
  tagline: "Rent with confidence: ratings only from tenancies both sides confirmed.",
  webDomain: "doorwayliving.in",
  apiDomain: "api.doorwayliving.in",
} as const
