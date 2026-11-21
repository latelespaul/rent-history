import type { City } from "@/types/api"
import { api } from "./client"

export const cityKeys = {
  all: ["cities"] as const,
}

/** `GET /cities`: live cities only, the sole source for city pickers (D-20). */
export function fetchCities(signal?: AbortSignal): Promise<City[]> {
  return api.get<City[]>("/cities", { signal })
}
