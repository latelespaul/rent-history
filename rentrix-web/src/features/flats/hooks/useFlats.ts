import { useQuery } from "@tanstack/react-query"
import { useEffect, useState } from "react"
import { flatsApi } from "@/api/flats"
import { queryKeys } from "@/api/queryKeys"
import { useFiltersStore } from "../store"
import { useDebouncedValue } from "@/hooks/useDebouncedValue"

export function useFlats() {
  const filters = useFiltersStore((s) => s.filters)
  const debouncedFilters = useDebouncedValue(filters, 300)
  const [isSlow, setIsSlow] = useState(false)

  const query = useQuery({
    queryKey: queryKeys.flats.list(debouncedFilters),
    queryFn: () => flatsApi.list(debouncedFilters),
    staleTime: 1000 * 30,
    placeholderData: (prev) => prev,
  })

  // Detect slow loads (cold start)
  useEffect(() => {
    if (!query.isFetching) {
      setIsSlow(false)
      return
    }
    const t = setTimeout(() => setIsSlow(true), 5000)
    return () => clearTimeout(t)
  }, [query.isFetching])

  return { ...query, isSlow }
}