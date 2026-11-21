import { useState } from "react"
import { Link, NavLink } from "react-router-dom"
import { Badge, DemoBadge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Icon } from "@/components/ui/icon"
import { LoginIcon, MenuIcon } from "@/components/ui/icons.gen"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet"
import { cn } from "@/lib/utils"
import { Brand } from "./Brand"
import { Container } from "./Container"
import { initials, navFor, viewerTone, type ShellViewer } from "./nav"
import { ShellCityPicker } from "./ShellCityPicker"

const AVATAR_TONE = {
  admin: "bg-ink-900 text-white",
  landlord: "bg-landlord-100 text-landlord-600",
  tenant: "bg-tenant-100 text-tenant-600",
} as const

function ViewerChip({ viewer, compact }: { viewer: ShellViewer; compact?: boolean }) {
  return (
    <span className="flex items-center gap-2">
      <span
        aria-hidden
        className={cn(
          "grid size-[34px] place-items-center rounded-full text-sm font-bold",
          AVATAR_TONE[viewerTone(viewer)],
        )}
      >
        {initials(viewer.shortName)}
      </span>
      <span className={cn("text-sm font-semibold text-ink-900", compact && "max-sm:sr-only")}>
        {viewer.shortName}
      </span>
    </span>
  )
}

function SignIn({ block }: { block?: boolean }) {
  return (
    <Button asChild size={block ? "block" : "default"}>
      <Link to="/login">
        <LoginIcon />
        Sign in
      </Link>
    </Button>
  )
}

/**
 * The sticky top bar (DESIGN.md §4.4 `.topbar`): brand, role-aware nav, the city picker,
 * "Admin" and "Demo data" badges, and the viewer or "Sign in". Under 860 px the nav, picker and
 * sign-in fold into a menu sheet behind the hamburger.
 */
export function TopBar({ viewer }: { viewer: ShellViewer | null }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const items = navFor(viewer)
  const isAdmin = viewer?.roles.includes("ADMIN") ?? false

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-surface">
      <Container className="flex h-topbar items-center gap-6">
        <Brand />
        <nav aria-label="Main" className="hidden items-center gap-5 nav:flex">
          {items.map((item) => (
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
        <ShellCityPicker className="hidden w-56 nav:block" />
        <span className="flex-1" />
        <div className="flex items-center gap-3">
          {isAdmin && <Badge variant="admin">Admin</Badge>}
          {viewer?.isDemo && <DemoBadge className="max-sm:hidden" />}
          {viewer ? (
            <ViewerChip viewer={viewer} compact />
          ) : (
            <span className="hidden nav:inline-flex">
              <SignIn />
            </span>
          )}
          <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" aria-label="Menu" className="nav:hidden">
                <MenuIcon size={20} />
              </Button>
            </SheetTrigger>
            <SheetContent side="right" className="w-[min(22rem,88vw)] gap-0 bg-surface p-0">
              <SheetHeader className="border-b border-line p-4">
                <SheetTitle asChild>
                  <span>
                    <Brand />
                  </span>
                </SheetTitle>
                <SheetDescription className="sr-only">Site menu</SheetDescription>
              </SheetHeader>
              <div className="flex flex-col gap-5 p-4">
                {viewer && (
                  <div className="flex flex-wrap items-center gap-2">
                    <ViewerChip viewer={viewer} />
                    {viewer.isDemo && <DemoBadge />}
                  </div>
                )}
                <div className="space-y-1.5">
                  <p className="text-xs font-semibold text-ink-500">City</p>
                  <ShellCityPicker />
                </div>
                <nav aria-label="Menu" className="flex flex-col">
                  {items.map((item) => (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      onClick={() => setMenuOpen(false)}
                      className={({ isActive }) =>
                        cn(
                          "flex min-h-11 items-center gap-3 rounded-md px-2 font-medium no-underline hover:bg-sunk hover:no-underline",
                          isActive ? "text-teal-700" : "text-ink-900",
                        )
                      }
                    >
                      <Icon name={item.icon} size={20} />
                      {item.label}
                    </NavLink>
                  ))}
                </nav>
                {!viewer && <SignIn block />}
              </div>
            </SheetContent>
          </Sheet>
        </div>
      </Container>
    </header>
  )
}
