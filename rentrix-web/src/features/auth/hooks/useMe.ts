import { useQuery } from "@tanstack/react-query"
import { useEffect } from "react"
import { authApi } from "@/api/auth"
import { queryKeys } from "@/api/queryKeys"
import { useAuthStore } from "../store"

export function useMe() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const setUser = useAuthStore((s) => s.setUser)

  const query = useQuery({
    queryKey: queryKeys.auth.me,
    queryFn: authApi.me,
    enabled: isAuthenticated,
    staleTime: 1000 * 60 * 5,
  })

  useEffect(() => {
    if (query.data) setUser(query.data)
  }, [query.data, setUser])

  return query
}
