import { QueryClient, QueryCache, MutationCache } from "@tanstack/react-query"

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => {
      const status = (error as { response?: { status?: number } })?.response?.status
      if (status === 401 || status === 404) return
      // Silence here — pages show inline errors
    },
  }),
  mutationCache: new MutationCache({
    onError: () => {
      // Mutations already toast in their hooks
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60, // 1 min
      gcTime: 1000 * 60 * 5, // 5 min
      /**
       * Retry policy tuned for Render free-tier cold starts:
       * - Never retry 4xx (won't fix itself)
       * - Retry up to 2 times on network / 5xx errors
       * - 3s delay between attempts (gives cold start time)
       */
      retry: (failureCount, error) => {
        const status = (error as { response?: { status?: number } })?.response?.status
        if (status && status >= 400 && status < 500) return false
        return failureCount < 2
      },
      retryDelay: (attemptIndex) => Math.min(3000 * (attemptIndex + 1), 15000),
      refetchOnWindowFocus: false,
      refetchOnReconnect: true,
    },
    mutations: { retry: 0 },
  },
})