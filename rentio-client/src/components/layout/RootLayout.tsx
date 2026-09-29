import { Link, Outlet } from "react-router-dom"
import { Toaster } from "@/components/ui/sonner"
import { Button } from "@/components/ui/button"
import { useAuthStore } from "@/features/auth/store"
import { useAuthBootstrap } from "@/features/auth/hooks/useAuthBootstrap"
import { env } from "@/lib/env"
import { useLogout } from "@/features/auth/hooks/useLogout.ts"
import { useMe } from "@/features/auth/hooks/useMe.ts"

export function RootLayout() {
  useAuthBootstrap()
  // const navigate = useNavigate()
  const { isAuthenticated, user } = useAuthStore()

  const logout = useLogout()
  useMe()

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b bg-background/95 backdrop-blur supports-backdrop-filter:bg-background/60">
        <div className="container mx-auto flex h-14 items-center justify-between px-4">
          <Link to="/" className="text-lg font-semibold">
            {env.VITE_APP_NAME}
          </Link>

          <nav className="flex items-center gap-2">
            <Button asChild variant="ghost" size="sm">
              <Link to="/flats">Flats</Link>
            </Button>

            {isAuthenticated && (
              <Button asChild variant="ghost" size="sm">
                <Link to="/me/reviews">My Reviews</Link>
              </Button>
            )}

            {user?.role === "ADMIN" && (
              <Button asChild variant="ghost" size="sm">
                <Link to="/admin">Admin</Link>
              </Button>
            )}

            {isAuthenticated ? (
              <>
                <span className="text-sm text-muted-foreground">{user?.name}</span>
                <Button variant="outline" size="sm" onClick={() => logout.mutate()}>
                  Logout
                </Button>
              </>
            ) : (
              <>
                <Button asChild variant="ghost" size="sm">
                  <Link to="/login">Login</Link>
                </Button>
                <Button asChild size="sm">
                  <Link to="/signup">Sign up</Link>
                </Button>
              </>
            )}
          </nav>
        </div>
      </header>

      <main className="flex-1">
        <Outlet />
      </main>

      <footer className="border-t py-6 text-center text-sm text-muted-foreground">
        © {new Date().getFullYear()} {env.VITE_APP_NAME}
      </footer>

      <Toaster richColors position="top-right" />
    </div>
  )
}
