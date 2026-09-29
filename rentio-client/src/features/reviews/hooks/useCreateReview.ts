import { useMutation, useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import type { AxiosError } from "axios"
import { reviewsApi } from "@/api/reviews"
import { queryKeys } from "@/api/queryKeys"
import type { CreateReviewRequest } from "@/types"

export function useCreateReview(flatId: number) {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: (payload: CreateReviewRequest) => reviewsApi.create(flatId, payload),
    onSuccess: async () => {
      toast.success("Review posted")
      await Promise.all([
        qc.invalidateQueries({ queryKey: ["reviews", "flat", flatId] }),
        qc.invalidateQueries({ queryKey: queryKeys.reviews.mine }),
        qc.invalidateQueries({ queryKey: queryKeys.flats.detail(flatId) }),
      ])
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      toast.error(err.response?.data?.message ?? "Failed to post review")
    },
  })
}
