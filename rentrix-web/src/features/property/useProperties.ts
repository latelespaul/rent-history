import { keepPreviousData, useQuery } from "@tanstack/react-query"
import {
  fetchProperty,
  fetchPropertyReviews,
  fetchRentHistory,
  propertyKeys,
  searchProperties,
  type PropertySearch,
} from "@/api/properties"
import { useSession } from "@/lib/session"

/** How many homes the locality hints read (the API's largest page, D-59). */
export const HINT_SAMPLE = 50

/** `GET /properties` for the current filters; the old page stays on screen while the next loads. */
export function usePropertySearch(search: PropertySearch) {
  return useQuery({
    queryKey: propertyKeys.search(search),
    queryFn: ({ signal }) => searchProperties(search, signal),
    placeholderData: keepPreviousData,
  })
}

/**
 * Locality hints for the search box: the distinct localities of the city's published homes
 * (the newest `HINT_SAMPLE`, the API's page maximum), so a hint always leads to a result.
 * None until a city is chosen.
 */
export function useLocalityHints(city: string | undefined) {
  return useQuery({
    queryKey: propertyKeys.localities(city ?? ""),
    queryFn: async ({ signal }) => {
      const page = await searchProperties({ city, size: HINT_SAMPLE, sort: "newest" }, signal)
      return [...new Set(page.items.map((p) => p.locality))].sort((a, b) => a.localeCompare(b))
    },
    enabled: Boolean(city),
    staleTime: 1000 * 60 * 5,
  })
}

/** Whether the session has resolved, and whether someone is signed in. */
export function useSignedIn() {
  const session = useSession()
  return { ready: !session.isPending, signedIn: Boolean(session.data) }
}

/** `GET /properties/{id}`, fetched again after sign-in because signed-in callers see more (D-56). */
export function useProperty(id: string) {
  const { ready, signedIn } = useSignedIn()
  return useQuery({
    queryKey: propertyKeys.detail(id, signedIn),
    queryFn: ({ signal }) => fetchProperty(id, signal),
    enabled: ready,
    retry: false,
  })
}

/** Rent history: signed-in only (D-56); `null` data means the API does not serve it yet (B5). */
export function useRentHistory(id: string, enabled: boolean) {
  return useQuery({
    queryKey: propertyKeys.rentHistory(id),
    queryFn: ({ signal }) => fetchRentHistory(id, signal),
    enabled,
    retry: false,
  })
}

/** Property reviews: signed-in only for text (D-56); `null` until the API serves them (B6). */
export function usePropertyReviews(id: string, enabled: boolean) {
  return useQuery({
    queryKey: propertyKeys.reviews(id, enabled),
    queryFn: ({ signal }) => fetchPropertyReviews(id, signal),
    enabled,
    retry: false,
  })
}
