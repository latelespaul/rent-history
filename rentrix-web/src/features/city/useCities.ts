import { useQuery } from "@tanstack/react-query"
import { cityKeys, fetchCities } from "@/api/cities"
import { fetchStats, statsKeys } from "@/api/stats"

/**
 * The active cities from `GET /cities`, loaded once and cached; every picker, card and copy
 * line reads this (D-82, D-85). Filtering is client-side over this list.
 */
export function useCities() {
  return useQuery({
    queryKey: cityKeys.all,
    queryFn: ({ signal }) => fetchCities(signal),
    staleTime: 1000 * 60 * 10,
  })
}

/**
 * `GET /stats` for the per-city counts. Until the API serves it (B4) the query fails and the
 * UI shows no counts at all rather than a made-up one (D-03).
 */
export function useStats() {
  return useQuery({
    queryKey: statsKeys.all,
    queryFn: ({ signal }) => fetchStats(signal),
  })
}
