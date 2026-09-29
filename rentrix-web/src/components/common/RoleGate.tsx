import { Navigate, Outlet } from "react-router-dom"
import type { Role } from "@/types"
import { useAuthStore } from "@/features/auth/store"

export function RoleGate({ allow }: { allow: Role[] }) {
  const hasRole = useAuthStore((s) => s.hasRole)
  if (!hasRole(allow)) return <Navigate to="/" replace />
  return <Outlet />
}
