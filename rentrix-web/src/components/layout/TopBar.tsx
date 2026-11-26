import { useState } from "react"
import { Link, NavLink } from "react-router-dom"
import { Badge, DemoBadge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Icon } from "@/components/ui/icon"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  AlertIcon,
  ChevronRightIcon,
  ClockIcon,
  LoginIcon,
  LogoutIcon,
  MailIcon,
  MenuIcon,
  MessageIcon,
  PlusIcon,
  ShareIcon,
  ShieldCheckIcon,
  UserIcon,
} from "@/components/ui/icons.gen"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet"
import { useLoginHref } from "@/features/auth/useLoginHref"
import { useInvite } from "@/features/invite/useInvite"
import { addHomePath, type Mode } from "@/features/mode/mode"
import { ModeSwitch } from "@/features/mode/ModeSwitch"
import { STEPS, type Progress } from "@/features/verification/progress"
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
          "relative grid size-[34px] place-items-center rounded-full text-sm font-bold",
          AVATAR_TONE[viewerTone(viewer)],
        )}
      >
        {initials(viewer.shortName)}
        {viewer.progress && (
          <span
            data-testid="progress-dot"
            className={cn(
              "absolute -top-0.5 -right-0.5 size-2.5 rounded-full ring-2 ring-surface",
              viewer.progress.kind === "rejected" ? "bg-danger-600" : "bg-warn-600",
            )}
          />
        )}
      </span>
      <span
        className={cn(
          "text-sm font-semibold text-ink-900",
          // The compact chip shows only the avatar on phones and beside the full nav under 1280 px.
          compact && "max-sm:sr-only nav:max-xl:sr-only",
        )}
      >
        {viewer.shortName}
      </span>
    </span>
  )
}

const ROLE_WORD = { TENANT: "tenant", LANDLORD: "landlord" } as const

/** The roles a viewer holds as small badges (the account menu and the mobile sheet). */
function RoleBadges({ viewer }: { viewer: ShellViewer }) {
  return (
    <span className="flex flex-wrap gap-1">
      {viewer.roles.includes("TENANT") && <Badge variant="tenant">Tenant</Badge>}
      {viewer.roles.includes("LANDLORD") && <Badge variant="landlord">Landlord</Badge>}
    </span>
  )
}

/**
 * The verification status row (D-121, mockup `account-menu-progress.html`): one line, a bar for
 * "Get verified: n of 4 steps" and a hint. Shared by the account menu and the mobile sheet.
 */
function ProgressRow({ progress }: { progress: Progress }) {
  const icon =
    progress.kind === "phone" ? (
      <MessageIcon size={18} />
    ) : progress.kind === "rejected" ? (
      <AlertIcon size={18} className="text-danger-600" />
    ) : progress.kind === "pending" ? (
      <ClockIcon size={18} />
    ) : (
      <ShieldCheckIcon size={18} />
    )
  const role = progress.role ? ROLE_WORD[progress.role] : null
  const hint =
    progress.kind === "pending"
      ? `You can post meanwhile${role ? ` · ${role}` : ""}`
      : progress.kind === "rejected"
        ? `See the reason and resubmit${role ? ` · ${role}` : ""}`
        : `${progress.hint}${role ? ` · ${role}` : ""}`
  return (
    <span className="flex w-full items-start gap-2.5">
      <span className="mt-0.5 shrink-0">{icon}</span>
      <span className="min-w-0 flex-1">
        <b className="block text-sm text-ink-900">{progress.label}</b>
        {progress.kind === "draft" && (
          <span
            role="progressbar"
            aria-label="Verification steps done"
            aria-valuemin={0}
            aria-valuemax={STEPS.length}
            aria-valuenow={progress.done}
            className="mt-1.5 block h-1.5 overflow-hidden rounded-full bg-sunk"
          >
            <span
              className="block h-full rounded-full bg-teal-600"
              style={{ width: `${((progress.done ?? 0) / STEPS.length) * 100}%` }}
            />
          </span>
        )}
        <span className="mt-1 block text-xs text-ink-500">{hint}</span>
      </span>
      <ChevronRightIcon size={16} className="mt-0.5 shrink-0 text-ink-500" />
    </span>
  )
}

/** The viewer chip as a menu: the verification row, invite friends, settings, contact and sign out. */
function AccountMenu({
  viewer,
  onSignOut,
  onInvite,
}: {
  viewer: ShellViewer
  onSignOut?: () => void
  onInvite: () => void
}) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label={`Account menu for ${viewer.shortName}${viewer.progress ? `: ${viewer.progress.label}` : ""}`}
          className="rounded-full outline-none focus-visible:ring-2 focus-visible:ring-teal-600"
        >
          <ViewerChip viewer={viewer} compact />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="max-w-72 min-w-48">
        <DropdownMenuLabel className="flex flex-wrap items-center gap-2">
          {viewer.shortName}
          <RoleBadges viewer={viewer} />
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {viewer.progress && (
          <>
            <DropdownMenuItem asChild className="items-start">
              <Link to={viewer.progress.to}>
                <ProgressRow progress={viewer.progress} />
              </Link>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuItem onSelect={() => onInvite()} data-testid="menu-invite">
          <ShareIcon />
          Invite friends
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link to="/settings">
            <UserIcon />
            Settings
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link to="/contact">
            <MailIcon />
            Contact us
          </Link>
        </DropdownMenuItem>
        {onSignOut && (
          <DropdownMenuItem onSelect={() => onSignOut()}>
            <LogoutIcon />
            Sign out
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function SignIn({ block }: { block?: boolean }) {
  const href = useLoginHref()
  return (
    <Button asChild size={block ? "block" : "default"}>
      <Link to={href}>
        <LoginIcon />
        Sign in
      </Link>
    </Button>
  )
}

/**
 * The sticky top bar (DESIGN.md §4.4 `.topbar`): brand, role-aware nav, the city picker,
 * the "Demo data" badge, and the viewer or "Sign in". Under 860 px the nav, picker and
 * sign-in fold into a menu sheet behind the hamburger. A signed-in viewer gets a slim second row
 * with the Tenant | Owner switch (PLAN R10), at every width, so the 390 px bar never overflows.
 * The brand stays on one line: beside the full nav under 1280 px the "Add your home" label and the
 * viewer's name fold to their icon and avatar, and the city picker narrows.
 */
export function TopBar({
  viewer,
  onSignOut,
  onModeChange,
}: {
  viewer: ShellViewer | null
  onSignOut?: () => void
  /** Draws the Tenant | Owner switch for a viewer who picked a role (PLAN R10). */
  onModeChange?: (mode: Mode) => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const items = navFor(viewer)
  const { invite, sheet: inviteSheet } = useInvite({ enabled: Boolean(viewer) })

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-surface">
      <Container className="flex h-topbar items-center gap-3 nav:gap-6">
        <Brand />
        <nav aria-label="Main" className="hidden items-center gap-5 nav:flex">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
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
        <ShellCityPicker className="hidden w-44 min-w-0 nav:block xl:w-56" />
        <span className="flex-1" />
        <div className="flex shrink-0 items-center gap-2 sm:gap-3">
          {viewer?.isDemo && <DemoBadge className="max-sm:hidden" />}
          {viewer && (
            // "+ Add your home" (D-139, captain 22:22): in both modes, on every page.
            <Button asChild size="sm" data-testid="add-home-button">
              <Link to={addHomePath(viewer.mode ?? "TENANT")}>
                <PlusIcon />
                <span className="max-[420px]:sr-only nav:max-xl:sr-only">Add your home</span>
              </Link>
            </Button>
          )}
          {viewer ? (
            <AccountMenu viewer={viewer} onSignOut={onSignOut} onInvite={() => void invite()} />
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
                    <RoleBadges viewer={viewer} />
                    {viewer.isDemo && <DemoBadge />}
                  </div>
                )}
                {viewer?.progress && (
                  <Link
                    to={viewer.progress.to}
                    onClick={() => setMenuOpen(false)}
                    className="rounded-lg border border-line p-3 no-underline hover:bg-sunk hover:no-underline"
                  >
                    <ProgressRow progress={viewer.progress} />
                  </Link>
                )}
                <div className="space-y-1.5">
                  <p className="text-xs font-semibold text-ink-500">City</p>
                  <ShellCityPicker onNavigate={() => setMenuOpen(false)} />
                </div>
                <nav aria-label="Menu" className="flex flex-col">
                  {items.map((item) => (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      end={item.end}
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
                {viewer ? (
                  <div className="flex flex-col gap-2 border-t border-line pt-4">
                    <Button
                      variant="outline"
                      size="block"
                      data-testid="sheet-invite"
                      onClick={() => {
                        setMenuOpen(false)
                        void invite()
                      }}
                    >
                      <ShareIcon />
                      Invite friends
                    </Button>
                    <Button asChild variant="outline" size="block">
                      <Link to="/settings" onClick={() => setMenuOpen(false)}>
                        <UserIcon />
                        Settings
                      </Link>
                    </Button>
                    {onSignOut && (
                      <Button
                        variant="ghost"
                        size="block"
                        onClick={() => {
                          setMenuOpen(false)
                          onSignOut()
                        }}
                      >
                        <LogoutIcon />
                        Sign out
                      </Button>
                    )}
                  </div>
                ) : (
                  <SignIn block />
                )}
              </div>
            </SheetContent>
          </Sheet>
        </div>
      </Container>
      {viewer && inviteSheet}
      {viewer && viewer.mode && viewer.roles.length > 0 && onModeChange && (
        <div className="border-t border-line bg-background">
          <Container className="flex h-11 items-center gap-3">
            <span className="text-xs font-semibold text-ink-500">Viewing as</span>
            <ModeSwitch mode={viewer.mode} onChange={onModeChange} />
          </Container>
        </div>
      )}
    </header>
  )
}
