import { Navigate } from "react-router-dom"
import { useAuthStore } from "@/features/auth/store"
import { LoginForm } from "@/features/auth/components/LoginForm"

export default function LoginPage() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  if (isAuthenticated) return <Navigate to="/flats" replace />

  return (
    <section className="container mx-auto max-w-md px-4 py-16">
      <div className="mb-6 space-y-1 text-center">
        <h1 className="text-2xl font-bold">Welcome back</h1>
        <p className="text-sm text-muted-foreground">Sign in to your Rentio account</p>
      </div>
      <LoginForm />

      <div className="mt-8 rounded-md border bg-muted/30 p-3 text-xs text-muted-foreground">
        <p className="mb-1 font-medium">Mock credentials</p>
        <p>tenant@rentio.com or landlord@rentio.com or admin@rentio.com</p>
        <p>Any password with 6+ characters.</p>
      </div>
    </section>
  )
}
