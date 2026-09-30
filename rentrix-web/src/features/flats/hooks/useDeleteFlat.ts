import { useMutation, useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import type { AxiosError } from "axios"
import { flatsApi } from "@/api/flats"
import { queryKeys } from "@/api/queryKeys"

export function useDeleteFlat() {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => flatsApi.remove(id),
    onSuccess: async (_data, id) => {
      toast.success("Flat removed")
      await Promise.all([
        qc.invalidateQueries({ queryKey: queryKeys.flats.detail(id) }),
        qc.invalidateQueries({ queryKey: queryKeys.flats.all }),
      ])
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      toast.error(err.response?.data?.message ?? "Failed to delete flat")
    },
  })
}
