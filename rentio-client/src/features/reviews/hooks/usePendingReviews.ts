import { useQuery } from "@tanstack/react-query"
import { reviewsApi } from "@/api/reviews"
import { queryKeys } from "@/api/queryKeys"

export function usePendingReviews() {
  return useQuery({
    queryKey: queryKeys.reviews.pending,
    queryFn: reviewsApi.pending,
    staleTime: 1000 * 15,
  })
}
