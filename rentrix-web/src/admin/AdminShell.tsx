import { Link, NavLink, Navigate, Outlet, useLocation } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { Icon, type IconName } from "@/components/ui/icon"
import { LogoutIcon, ShieldCheckIcon } from "@/components/ui/icons.gen"
import { BRAND } from "@/lib/brand"
import { cn } from "@/lib/utils"
import { useAdminAuth } from "./authContext"

interface AdminNavItem {
  to: string
  label: string
  icon: IconName
}

/** The console's pages. PLAN W9 adds the approvals queue in front of the relay. */
const ADMIN_NAV: AdminNavItem[] = [{ to: "/sms-relay", label: "OTP relay", icon: "message" }]

/**
 * The admin console's frame (SYSTEM_DESIGN §13.3), phone first: a slim top bar with the brand and
 * "Sign out", and the page links in a bottom bar where a thumb reaches them (a top row from 860 px).
 * Anonymous visitors go to `/login`.
 */
export function AdminShell() {
  const { state, signOut } = useAdminAuth()
  const location = useLocation()

  if (state.status === "loading") {
    return (
      <main aria-busy="true" className="grid min-h-dvh place-items-center bg-paper p-4">
        <p role="status" className="text-sm text-ink-500">
          Checking your sign-in…
        </p>
      </main>
    )
  }
  if (state.status === "anonymous") {
    const next = `${location.pathname}${location.search}`
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace />
  }

  return (
    <div className="min-h-dvh bg-paper pb-[calc(4.5rem+env(safe-area-inset-bottom))] nav:pb-0">
      <header className="sticky top-0 z-40 border-b border-line bg-surface">
        <div className="mx-auto flex h-14 w-full max-w-page items-center gap-3 px-4 nav:px-6">
          <Link
            to="/"
            className="flex min-w-0 items-center gap-2 font-display text-lg font-bold text-ink-900 no-underline hover:no-underline"
          >
            <span className="grid size-8 shrink-0 place-items-center rounded-[9px] bg-ink-900 text-paper">
              <ShieldCheckIcon size={18} />
            </span>
            {/* On a phone the shield and "Admin" say enough; the name would be cut. */}
            <span className="sr-only sm:not-sr-only sm:truncate">{BRAND.name}</span>
            <span className="rounded-full bg-ink-900 px-2 py-0.5 text-xs font-semibold text-paper">
              Admin
            </span>
          </Link>
          <nav aria-label="Admin" className="ml-4 hidden items-center gap-5 nav:flex">
            {ADMIN_NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  cn(
                    "inline-flex items-center gap-1.5 text-sm font-medium no-underline hover:no-underline",
                    isActive ? "text-teal-700" : "text-ink-700 hover:text-ink-900",
                  )
                }
              >
                <Icon name={item.icon} size={18} />
                {item.label}
              </NavLink>
            ))}
          </nav>
          <span className="flex-1" />
          <span className="hidden max-w-48 truncate text-sm text-ink-500 sm:inline">
            {state.user.email}
          </span>
          <Button variant="ghost" className="h-11 px-3" onClick={() => void signOut()}>
            <LogoutIcon />
            Sign out
          </Button>
        </div>
      </header>
      <Outlet />
      <nav
        aria-label="Admin pages"
        className="fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface pb-[env(safe-area-inset-bottom)] nav:hidden"
      >
        <ul className="mx-auto flex max-w-md">
          {ADMIN_NAV.map((item) => (
            <li key={item.to} className="flex-1">
              <NavLink
                to={item.to}
                className={({ isActive }) =>
                  cn(
                    "flex h-16 flex-col items-center justify-center gap-1 text-xs font-semibold no-underline hover:no-underline",
                    isActive ? "text-teal-700" : "text-ink-700",
                  )
                }
              >
                <Icon name={item.icon} size={22} />
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  )
}
