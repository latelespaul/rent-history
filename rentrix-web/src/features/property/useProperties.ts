import { keepPreviousData, useQuery } from "@tanstack/react-query"
import {
  fetchProperty,
  fetchPropertyReviews,
  fetchRentHistory,
  propertyKeys,
  searchGrouped,
  searchProperties,
  type PropertySearch,
} from "@/api/properties"
import { useAuth } from "@/features/auth/authContext"

/** How many homes the locality hints read (the API's largest page, D-59). */
export const HINT_SAMPLE = 50

/**
 * `GET /properties` for the current filters; the old page stays on screen while the next loads.
 * `enabled: false` waits, e.g. until the user asks.
 */
export function usePropertySearch(search: PropertySearch, enabled = true) {
  return useQuery({
    queryKey: propertyKeys.search(search),
    queryFn: ({ signal }) => searchProperties(search, signal),
    placeholderData: keepPreviousData,
    enabled,
  })
}

/**
 * `GET /properties/grouped` (D-166): search results with a society of 2+ matching homes as one
 * row; cached per viewer, because only signed-in callers get a society's rent range (D-56).
 */
export function useGroupedSearch(search: PropertySearch, enabled = true) {
  const { ready, viewer } = useSignedIn()
  return useQuery({
    queryKey: propertyKeys.grouped(search, viewer),
    queryFn: ({ signal }) => searchGrouped(search, signal),
    placeholderData: keepPreviousData,
    enabled: ready && enabled,
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

/** Whether the session has resolved, whether someone is signed in, and who (`viewer`). */
export function useSignedIn() {
  const { state } = useAuth()
  return {
    ready: state.status !== "loading",
    signedIn: state.status === "signedIn",
    viewer: state.user?.id ?? null,
  }
}

/** `GET /properties/{id}`, cached per viewer: signed-in callers, owners and admins see more (D-56, D-57). */
export function useProperty(id: string) {
  const { ready, viewer } = useSignedIn()
  return useQuery({
    queryKey: propertyKeys.detail(id, viewer),
    queryFn: ({ signal }) => fetchProperty(id, signal),
    enabled: ready && Boolean(id),
    retry: false,
  })
}

/** Rent history: signed-in only (D-56); `null` data means the API does not serve it yet (B5). */
export function useRentHistory(id: string, enabled: boolean) {
  const { viewer } = useSignedIn()
  return useQuery({
    queryKey: propertyKeys.rentHistory(id, viewer),
    queryFn: ({ signal }) => fetchRentHistory(id, signal),
    enabled,
    retry: false,
  })
}

/**
 * Property reviews, full text for everyone (D-161), cached per viewer because only the author gets
 * edit and delete; `null` until the API serves them (B6).
 */
export function usePropertyReviews(id: string) {
  const { ready, viewer } = useSignedIn()
  return useQuery({
    queryKey: propertyKeys.reviews(id, viewer),
    queryFn: ({ signal }) => fetchPropertyReviews(id, signal),
    enabled: ready && Boolean(id),
    retry: false,
  })
}
