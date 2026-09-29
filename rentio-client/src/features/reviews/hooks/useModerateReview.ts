import { useMutation, useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import type { AxiosError } from "axios"
import { reviewsApi } from "@/api/reviews"
import { queryKeys } from "@/api/queryKeys"
import type { ReviewStatus } from "@/types"

export function useModerateReview() {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: Exclude<ReviewStatus, "PENDING"> }) =>
      reviewsApi.moderate(id, status),
    onSuccess: async (_data, vars) => {
      toast.success(vars.status === "APPROVED" ? "Review approved" : "Review rejected")
      await Promise.all([
        qc.invalidateQueries({ queryKey: queryKeys.reviews.pending }),
        // Public list on the flat page should refresh
        qc.invalidateQueries({ queryKey: ["reviews", "flat"] }),
      ])
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      toast.error(err.response?.data?.message ?? "Moderation failed")
    },
  })
}
