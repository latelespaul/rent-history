import { useMutation, useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import type { AxiosError } from "axios"
import { flatsApi } from "@/api/flats"
import { queryKeys } from "@/api/queryKeys"
import type { UpdateFlatRequest } from "@/types"

interface Vars {
  id: number
  payload: UpdateFlatRequest
}

export function useUpdateFlat() {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: ({ id, payload }: Vars) => flatsApi.update(id, payload),
    onSuccess: async (_data, vars) => {
      toast.success("Flat updated")
      await Promise.all([
        qc.invalidateQueries({ queryKey: queryKeys.flats.detail(vars.id) }),
        qc.invalidateQueries({ queryKey: queryKeys.flats.all }),
      ])
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      toast.error(err.response?.data?.message ?? "Failed to update flat")
    },
  })
}
