import { useMutation } from "@tanstack/react-query"
import { authApi } from "@/api/auth"
import type { SignupRequest } from "@/types"
import { useAuthStore } from "../store"

export function useSignup() {
  const setAuth = useAuthStore((s) => s.setAuth)

  return useMutation({
    mutationFn: (payload: SignupRequest) => authApi.signup(payload),
    onSuccess: (data) => {
      setAuth(data.user, data.accessToken, data.refreshToken)
    },
  })
}
