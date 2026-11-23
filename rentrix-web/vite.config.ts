import path from "path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig, type Plugin } from "vitest/config"
import { BRAND } from "./src/lib/brand"

/** Fills the %BRAND_*% placeholders in index.html from src/lib/brand.ts. */
function brandHtml(): Plugin {
  const values: Record<string, string> = {
    BRAND_NAME: BRAND.name,
    BRAND_TAGLINE: BRAND.tagline,
    BRAND_WEB_DOMAIN: BRAND.webDomain,
  }
  return {
    name: "brand-html",
    transformIndexHtml: (html) =>
      html.replace(/%(BRAND_[A-Z_]+)%/g, (match, key: string) => values[key] ?? match),
  }
}

export default defineConfig({
  plugins: [react(), tailwindcss(), brandHtml()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
  server: {
    // GIS needs the sign-in popup to keep its opener (D-76); prod hosts send the same header.
    headers: { "Cross-Origin-Opener-Policy": "same-origin-allow-popups" },
    // Dev only: same-origin /api keeps the refresh cookie first-party (SYSTEM_DESIGN.md §13).
    // xfwd sends X-Forwarded-Host, so the API (which trusts loopback proxies) sees the page's host:
    // http://admin.localhost:5173 is the admin console (ADMIN_HOSTS, §13.3).
    proxy: {
      "/api": {
        target: process.env.API_PROXY_TARGET ?? "http://localhost:8080",
        changeOrigin: true,
        xfwd: true,
      },
    },
  },
  preview: {
    headers: { "Cross-Origin-Opener-Policy": "same-origin-allow-popups" },
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.{ts,tsx}"],
  },
})
