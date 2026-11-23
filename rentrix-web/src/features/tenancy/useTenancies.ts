import { useQuery, useQueryClient } from "@tanstack/react-query"
import { agreementKeys, fetchReviewTasks } from "@/api/agreements"
import { fetchMyProperties, propertyKeys } from "@/api/properties"
import { fetchMyTenancies, fetchRentRecords, fetchTenancy, tenancyKeys } from "@/api/tenancies"
import { useSignedIn } from "@/features/property/useProperties"
import type { TenancyParty } from "@/types/api"

/** `GET /me/tenancies` (both sides, or one with `role`), newest first. */
export function useMyTenancies(role: TenancyParty | null = null) {
  const { ready, signedIn, viewer } = useSignedIn()
  return useQuery({
    queryKey: tenancyKeys.mine(viewer, role, 0),
    queryFn: ({ signal }) => fetchMyTenancies({ role, size: 50 }, signal),
    enabled: ready && signedIn,
  })
}

export function useTenancy(id: string) {
  const { ready, viewer } = useSignedIn()
  return useQuery({
    queryKey: tenancyKeys.detail(id, viewer),
    queryFn: ({ signal }) => fetchTenancy(id, signal),
    enabled: ready && Boolean(id),
    retry: false,
  })
}

export function useRentRecords(id: string, enabled = true) {
  const { viewer } = useSignedIn()
  return useQuery({
    queryKey: tenancyKeys.rentRecords(id, viewer),
    queryFn: ({ signal }) => fetchRentRecords(id, signal),
    enabled: enabled && Boolean(id),
  })
}

/** `GET /me/properties`: the landlord's listings in every state. */
export function useMyProperties() {
  const { ready, signedIn, viewer } = useSignedIn()
  return useQuery({
    queryKey: propertyKeys.mine(viewer, 0),
    queryFn: ({ signal }) => fetchMyProperties(0, 50, signal),
    enabled: ready && signedIn,
  })
}

/** After a tenancy or listing write: refresh every list and page that shows it. */
export function useRefreshTenancyData() {
  const client = useQueryClient()
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: tenancyKeys.all }),
      client.invalidateQueries({ queryKey: ["properties"] }),
      client.invalidateQueries({ queryKey: ["review-tasks"] }),
      client.invalidateQueries({ queryKey: ["agreements"] }),
    ])
}

/** `GET /me/review-tasks`: which reviews the caller can still write (the review next step). */
export function useReviewTasks(enabled = true) {
  const { ready, signedIn, viewer } = useSignedIn()
  return useQuery({
    queryKey: agreementKeys.reviewTasks(viewer),
    queryFn: ({ signal }) => fetchReviewTasks(signal),
    enabled: enabled && ready && signedIn,
  })
}
