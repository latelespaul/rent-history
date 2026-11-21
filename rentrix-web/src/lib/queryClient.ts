import { QueryClient } from "@tanstack/react-query"
import { isApiError } from "@/api/errors"

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60,
      gcTime: 1000 * 60 * 5,
      // A 4xx will not fix itself; retry network errors (status 0) and 5xx up to twice.
      retry: (failureCount, error) => {
        if (isApiError(error) && error.status >= 400 && error.status < 500) return false
        return failureCount < 2
      },
      refetchOnWindowFocus: false,
    },
    mutations: { retry: 0 },
  },
})
