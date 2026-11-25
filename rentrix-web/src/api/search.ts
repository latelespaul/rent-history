import type { SearchSuggestions } from "@/types/api"
import { api } from "./client"

export const searchKeys = {
  suggestions: (city: string, society: string, q: string, viewer: string | null) =>
    ["search", "suggestions", city, society, q, viewer] as const,
}

/**
 * `GET /search/suggestions` (D-164): what the search box offers while typing. With `society`
 * (a picked society, D-167) the text is a flat number or a tower of that society.
 */
export function fetchSuggestions(
  query: { city?: string; q: string; society?: string },
  signal?: AbortSignal,
): Promise<SearchSuggestions> {
  return api.get<SearchSuggestions>("/search/suggestions", {
    query: { city: query.city, q: query.q, society: query.society },
    signal,
  })
}
