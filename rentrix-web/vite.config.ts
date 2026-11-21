import path from "path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig, loadEnv } from "vite"

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, import.meta.dirname, "VITE_")
  // Dev-only proxy for same-origin setups (VITE_API_BASE_URL empty or
  // relative, e.g. "/api/v1"). With an absolute VITE_API_BASE_URL the app
  // calls the API directly and this proxy is unused. Production builds are
  // plain static files; see README "Deploying as static files".
  const configured = env.VITE_API_BASE_URL?.trim() ?? ""
  const proxyTarget = /^https?:\/\//i.test(configured)
    ? new URL(configured).origin
    : "http://localhost:8080"

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        "@": path.resolve(import.meta.dirname, "./src"),
      },
    },
    server: {
      proxy: {
        "/api": {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
