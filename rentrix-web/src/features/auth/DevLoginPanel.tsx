import { useMemo, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { authKeys, fetchDevUsers } from "@/api/auth"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { LoginIcon, PlusIcon } from "@/components/ui/icons.gen"
import { errorMessage } from "@/components/ui/states"
import { useCities } from "@/features/city/useCities"
import { initials } from "@/components/layout/nav"
import { cn } from "@/lib/utils"
import type { CurrentUser } from "@/types/api"
import { groupDevUsers } from "./roles"

interface Props {
  onSignIn: (userId: string) => void
  /** Signs in as a brand-new user with no role yet (`POST /auth/dev-login/new`). */
  onSignInAsNew?: () => void
  pending: boolean
}

const AVATAR = {
  admin: "bg-ink-900 text-white",
  landlord: "bg-landlord-100 text-landlord-600",
  tenant: "bg-tenant-100 text-tenant-600",
  none: "bg-sunk text-ink-700",
} as const

function tone(user: CurrentUser): keyof typeof AVATAR {
  if (user.roles.includes("ADMIN")) return "admin"
  if (user.roles.includes("LANDLORD")) return "landlord"
  if (user.roles.includes("TENANT")) return "tenant"
  return "none"
}

/** The approval states worth a badge in the picker (D-89): anything not yet approved. */
function statusBadges(user: CurrentUser) {
  return Object.entries(user.verification)
    .filter(([, status]) => status === "PENDING" || status === "REJECTED")
    .map(([role, status]) => (
      <Badge key={role} variant={status === "REJECTED" ? "danger" : "warn"}>
        {role === "LANDLORD" ? "Landlord" : "Tenant"}{" "}
        {status === "REJECTED" ? "rejected" : "pending"}
      </Badge>
    ))
}

/**
 * Dev-only seed-user picker (D-77). It is shown only when `GET /auth/config` says `devLogin: true`
 * and looks deliberately unlike product UI (purple, dashed, "DEV ONLY").
 */
export function DevLoginPanel({ onSignIn, onSignInAsNew, pending }: Props) {
  const users = useQuery({
    queryKey: authKeys.devUsers,
    queryFn: ({ signal }) => fetchDevUsers(signal),
  })
  const cities = useCities()
  const [city, setCity] = useState<string | null>(null)
  const [selected, setSelected] = useState<string | null>(null)

  const cityTabs = useMemo(() => {
    const codes = new Set((users.data ?? []).map((u) => u.homeCity).filter((c): c is string => !!c))
    const names = new Map((cities.data ?? []).map((c) => [c.code, c.name]))
    return [...codes].sort().map((code) => ({ code, name: names.get(code) ?? code }))
  }, [users.data, cities.data])

  const shown = (users.data ?? []).filter(
    (u) => !city || u.homeCity === city || u.roles.includes("ADMIN"),
  )
  const groups = groupDevUsers(shown)

  return (
    <section
      aria-labelledby="dev-login-title"
      className="rounded-xl border border-dashed border-dev-600 bg-dev-100 p-6 text-[#4d2390]"
    >
      <div className="flex items-center justify-between gap-2">
        <Badge variant="dev">DEV ONLY</Badge>
        <span className="font-mono text-xs">devLogin: true</span>
      </div>
      <h2 id="dev-login-title" className="mt-3 text-lg font-semibold">
        Dev login · seed users
      </h2>
      <p className="mt-1 text-sm">
        Shown only when the API runs the <code>dev</code> or <code>test</code> profile with{" "}
        <code>DEV_LOGIN_ENABLED=true</code>. Works only for demo users; in <code>prod</code> these
        endpoints do not exist.
      </p>

      {cityTabs.length > 1 && (
        <div
          role="group"
          aria-label="Filter by city"
          className="mt-4 inline-flex gap-1 rounded-md bg-surface/60 p-1"
        >
          {[{ code: null, name: "All" }, ...cityTabs].map((tab) => (
            <button
              key={tab.code ?? "all"}
              type="button"
              aria-pressed={city === tab.code}
              onClick={() => setCity(tab.code)}
              className={cn(
                "rounded-sm px-3 py-1.5 text-sm font-medium",
                city === tab.code
                  ? "bg-surface font-semibold text-ink-900 shadow-sm"
                  : "text-ink-700",
              )}
            >
              {tab.name}
            </button>
          ))}
        </div>
      )}

      {users.isPending && <p className="mt-4 text-sm">Loading seed users…</p>}
      {users.isError && (
        <p role="alert" className="mt-4 text-sm">
          Could not load seed users: {errorMessage(users.error)}
        </p>
      )}
      {users.isSuccess && users.data.length === 0 && (
        <p className="mt-4 text-sm">
          No seed users in this database. Start the API with <code>SEED_DEMO=true</code> on an empty
          database.
        </p>
      )}

      <form
        className="mt-2"
        onSubmit={(event) => {
          event.preventDefault()
          if (selected) onSignIn(selected)
        }}
      >
        {groups.map((group) => (
          <fieldset key={group.key} className="mt-3">
            <legend className="text-xs font-semibold tracking-wide uppercase">
              {group.label} <span className="font-normal">({group.users.length})</span>
            </legend>
            <div className="mt-1.5 max-h-64 space-y-1.5 overflow-y-auto">
              {group.users.map((user) => (
                <label
                  key={user.id}
                  className={cn(
                    "relative flex cursor-pointer items-center gap-2.5 rounded-md border bg-surface px-2.5 py-2 text-sm text-ink-900 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-dev-600",
                    selected === user.id ? "border-dev-600 ring-1 ring-dev-600" : "border-line",
                  )}
                >
                  <input
                    type="radio"
                    name="dev-user"
                    value={user.id}
                    checked={selected === user.id}
                    onChange={() => setSelected(user.id)}
                    // Invisible but covering the whole row, so a click anywhere on it (including at the
                    // radio's own position) hits the input; an sr-only 1px input was not clickable.
                    className="absolute inset-0 size-full cursor-pointer appearance-none opacity-0"
                  />
                  <span
                    aria-hidden
                    className={cn(
                      "grid size-[30px] shrink-0 place-items-center rounded-full text-[11px] font-bold",
                      AVATAR[tone(user)],
                    )}
                  >
                    {initials(user.displayName)}
                  </span>
                  <span className="min-w-0 flex-1 truncate">{user.displayName}</span>
                  <span className="flex shrink-0 gap-1">{statusBadges(user)}</span>
                </label>
              ))}
            </div>
          </fieldset>
        ))}
        {users.isSuccess && users.data.length > 0 && (
          <Button
            type="submit"
            variant="dev"
            size="block"
            className="mt-4"
            disabled={!selected || pending}
          >
            <LoginIcon />
            {pending ? "Signing in…" : "Sign in as selected seed user"}
          </Button>
        )}
      </form>
      {onSignInAsNew && (
        <Button
          type="button"
          variant="dev"
          size="block"
          className="mt-2"
          disabled={pending}
          onClick={onSignInAsNew}
        >
          <PlusIcon />
          Sign in as a new user (first sign-in)
        </Button>
      )}
    </section>
  )
}
