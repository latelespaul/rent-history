import { useQuery } from "@tanstack/react-query"
import { reviewsApi } from "@/api/reviews"
import { queryKeys } from "@/api/queryKeys"

export function useMyReviews() {
  return useQuery({
    queryKey: queryKeys.reviews.mine,
    queryFn: reviewsApi.mine,
    staleTime: 1000 * 30,
  })
}
