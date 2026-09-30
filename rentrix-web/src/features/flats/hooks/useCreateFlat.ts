import { useMutation, useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import type { AxiosError } from "axios"
import { flatsApi } from "@/api/flats"
import { queryKeys } from "@/api/queryKeys"
import type { CreateFlatRequest } from "@/types"

export function useCreateFlat() {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: (payload: CreateFlatRequest) => flatsApi.create(payload),
    onSuccess: async () => {
      toast.success("Flat created")
      await qc.invalidateQueries({ queryKey: queryKeys.flats.all })
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      toast.error(err.response?.data?.message ?? "Failed to create flat")
    },
  })
}
