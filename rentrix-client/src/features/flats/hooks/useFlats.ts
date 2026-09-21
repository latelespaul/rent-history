import { useQuery } from "@tanstack/react-query"
import { flatsApi } from "@/api/flats"
import { queryKeys } from "@/api/queryKeys"
import { useFiltersStore } from "../store"
import { useDebouncedValue } from "@/hooks/useDebouncedValue"

export function useFlats() {
  const filters = useFiltersStore((s) => s.filters)
  const debouncedFilters = useDebouncedValue(filters, 300)

  return useQuery({
    queryKey: queryKeys.flats.list(debouncedFilters),
    queryFn: () => flatsApi.list(debouncedFilters),
    staleTime: 1000 * 30,
    placeholderData: (prev) => prev,
  })
}
