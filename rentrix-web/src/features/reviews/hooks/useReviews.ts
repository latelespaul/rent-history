import { useQuery } from "@tanstack/react-query"
import { reviewsApi } from "@/api/reviews"
import { queryKeys } from "@/api/queryKeys"

export function useReviews(flatId: number, page = 0, size = 10) {
  return useQuery({
    queryKey: queryKeys.reviews.byFlat(flatId, page, size),
    queryFn: () => reviewsApi.listByFlat(flatId, page, size),
    enabled: Number.isFinite(flatId) && flatId > 0,
    staleTime: 1000 * 60,
  })
}
