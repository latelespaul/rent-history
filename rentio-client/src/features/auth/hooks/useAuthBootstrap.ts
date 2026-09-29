import { useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { tokenStore } from "@/api/client.ts"
import { useAuthStore } from "../store.ts"

export function useAuthBootstrap() {
  const navigate = useNavigate()
  const clearAuth = useAuthStore((s) => s.clearAuth)

  useEffect(() => {
    tokenStore.setOnUnauthorized(() => {
      clearAuth()
      navigate("/login", { replace: true })
    })
  }, [clearAuth, navigate])
}
