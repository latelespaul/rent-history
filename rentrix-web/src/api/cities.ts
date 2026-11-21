import type { City } from "@/types/api"
import { api } from "./client"

export const cityKeys = {
  all: ["cities"] as const,
}

/** `GET /cities`: active cities only, by name; the sole source of every city in the web (D-20, D-82). */
export function fetchCities(signal?: AbortSignal): Promise<City[]> {
  return api.get<City[]>("/cities", { signal })
}
