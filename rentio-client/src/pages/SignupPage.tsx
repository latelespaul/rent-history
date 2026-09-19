import { SignupForm } from "@/features/auth/components/SignupForm"
import { Navigate } from "react-router-dom"
import { useAuthStore } from "@/features/auth/store.ts"

export default function SignupPage() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  if (isAuthenticated) return <Navigate to="/flats" replace />
  return (
    <section className="container mx-auto max-w-md px-4 py-16">
      <div className="mb-6 space-y-1 text-center">
        <h1 className="text-2xl font-bold">Create your account</h1>
        <p className="text-sm text-muted-foreground">
          Join Rentio to rate flats and share your experience
        </p>
      </div>
      <SignupForm />
    </section>
  )
}
