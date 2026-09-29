import { useMutation } from "@tanstack/react-query"
import type { AxiosError } from "axios"
import { authApi } from "@/api/auth"
import type { LoginRequest } from "@/types"
import { useAuthStore } from "../store"

export function useLogin() {
  const setAuth = useAuthStore((s) => s.setAuth)

  return useMutation({
    mutationFn: (payload: LoginRequest) => authApi.login(payload),
    onSuccess: (data) => {
      setAuth(data.user, data.accessToken, data.refreshToken)
    },
  })
}

export function getAuthErrorMessage(error: unknown): string {
  const axiosErr = error as AxiosError<{ message?: string }>
  return axiosErr?.response?.data?.message ?? axiosErr?.message ?? "Something went wrong"
}
