import { useQuery } from "@tanstack/react-query"
import { flatsApi } from "@/api/flats"
import { queryKeys } from "@/api/queryKeys"

export function useFlat(id: number) {
  return useQuery({
    queryKey: queryKeys.flats.detail(id),
    queryFn: () => flatsApi.get(id),
    enabled: Number.isFinite(id) && id > 0,
    staleTime: 1000 * 60,
  })
}
