import type { Stats } from "@/types/api"
import { api } from "./client"

export const statsKeys = {
  all: ["stats"] as const,
}

/** `GET /stats`: real counts, the only source for any number on Home (D-03). */
export function fetchStats(signal?: AbortSignal): Promise<Stats> {
  return api.get<Stats>("/stats", { signal })
}
