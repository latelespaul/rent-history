import path from "path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vitest/config"

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
  server: {
    // GIS needs the sign-in popup to keep its opener (D-76); prod hosts send the same header.
    headers: { "Cross-Origin-Opener-Policy": "same-origin-allow-popups" },
    // Dev only: same-origin /api keeps the refresh cookie first-party (SYSTEM_DESIGN.md §13).
    proxy: {
      "/api": {
        target: process.env.API_PROXY_TARGET ?? "http://localhost:8080",
        changeOrigin: true,
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
