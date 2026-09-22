import { QueryClient, QueryCache, MutationCache } from "@tanstack/react-query"

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => {
      const status = (error as { response?: { status?: number } })?.response?.status
      // Ignore 401 (handled by interceptor) and 404 (page-level handling)
      if (status === 401 || status === 404) return
      // Silence here — pages show inline errors. Uncomment for global toasts:
      // toast.error('Something went wrong')
    },
  }),
  mutationCache: new MutationCache({
    onError: () => {
      // Mutations already toast in their hooks
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60,
      gcTime: 1000 * 60 * 5,
      retry: (failureCount, error) => {
        const status = (error as { response?: { status?: number } })?.response?.status
        if (status && status >= 400 && status < 500) return false
        return failureCount < 2
      },
      refetchOnWindowFocus: false,
    },
    mutations: { retry: 0 },
  },
})
