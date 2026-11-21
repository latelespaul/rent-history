import { z } from "zod"

const envSchema = z.object({
  // API origin, e.g. https://api.example.com. Optional: see src/lib/apiConfig.ts
  // for the runtime override and the http://localhost:8080 default.
  VITE_API_BASE_URL: z.string().optional(),
  VITE_APP_NAME: z.string().min(1).default("Rentrix"),
})

const parsed = envSchema.safeParse(import.meta.env)

if (!parsed.success) {
  console.error("❌ Invalid environment variables:")
  console.error(parsed.error.flatten().fieldErrors)
  throw new Error("Invalid environment variables")
}

export const env = parsed.data