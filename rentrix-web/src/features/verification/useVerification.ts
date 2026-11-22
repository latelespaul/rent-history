import { useQuery, useQueryClient } from "@tanstack/react-query"
import { fetchIdentity, fetchVerification, verificationKeys } from "@/api/verification"
import { useAuth } from "@/features/auth/authContext"
import { progressOf } from "./progress"

/** Whether the signed-in user has a verification to show: a tenant or landlord role (D-89). */
export function useHasVerification(): boolean {
  const { state } = useAuth()
  const roles = state.user?.roles ?? []
  return state.status === "signedIn" && (roles.includes("TENANT") || roles.includes("LANDLORD"))
}

/** `GET /me/verification`, refetched when the window regains focus (D-121). */
export function useVerification() {
  const enabled = useHasVerification()
  return useQuery({
    queryKey: verificationKeys.all,
    queryFn: ({ signal }) => fetchVerification(signal),
    enabled,
    refetchOnWindowFocus: true,
  })
}

/** `GET /me/identity`: masked values and which methods can start now. */
export function useIdentity() {
  const enabled = useHasVerification()
  return useQuery({
    queryKey: verificationKeys.identity,
    queryFn: ({ signal }) => fetchIdentity(signal),
    enabled,
  })
}

/** The account-menu row (D-121), or null. */
export function useVerificationProgress() {
  const verification = useVerification()
  return progressOf(verification.data)
}

/** Refetches the stepper state after a step write. */
export function useRefreshVerification() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: verificationKeys.all }),
      queryClient.invalidateQueries({ queryKey: verificationKeys.identity }),
    ])
}
