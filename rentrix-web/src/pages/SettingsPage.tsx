import { useState, type FormEvent, type ReactNode } from "react"
import { useMutation } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { updateMe } from "@/api/auth"
import { isApiError } from "@/api/errors"
import { Container } from "@/components/layout/Container"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Notice } from "@/components/ui/notice"
import { errorMessage } from "@/components/ui/states"
import { LogoutIcon, PlusIcon } from "@/components/ui/icons.gen"
import { useAuth } from "@/features/auth/authContext"
import { shortName } from "@/features/auth/paths"
import { ROLE_COPY, type PickableRole } from "@/features/auth/roles"
import { CityPicker } from "@/features/city/CityPicker"
import type { CurrentUser, UpdateMeRequest, VerificationStatus } from "@/types/api"

const STATUS: Record<
  VerificationStatus,
  { label: string; variant: "neutral" | "warn" | "success" | "danger" }
> = {
  DRAFT: { label: "Not verified yet", variant: "neutral" },
  PENDING: { label: "Pending approval", variant: "warn" },
  APPROVED: { label: "Approved", variant: "success" },
  REJECTED: { label: "Rejected", variant: "danger" },
}

function Section({
  title,
  description,
  children,
}: {
  title: string
  description?: string
  children: ReactNode
}) {
  return (
    <section className="rounded-xl border border-line bg-surface p-6 shadow-sm">
      <h2 className="text-xl font-semibold text-ink-900">{title}</h2>
      {description && <p className="mt-1 text-sm text-ink-500">{description}</p>}
      <div className="mt-4">{children}</div>
    </section>
  )
}

function ProfileForm({ user }: { user: CurrentUser }) {
  const { acceptUpdate } = useAuth()
  const [displayName, setDisplayName] = useState(user.displayName)
  const [phone, setPhone] = useState(user.phone ?? "")
  const [homeCity, setHomeCity] = useState<string | null>(user.homeCity ?? null)
  const save = useMutation({
    mutationFn: (body: UpdateMeRequest) => updateMe(body),
    onSuccess: (update) => acceptUpdate(update),
  })

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const body: UpdateMeRequest = {}
    if (displayName.trim() !== user.displayName) body.displayName = displayName.trim()
    if (phone.trim() !== (user.phone ?? "")) body.phone = phone.trim()
    if ((homeCity ?? "") !== (user.homeCity ?? "")) body.homeCity = homeCity ?? ""
    save.mutate(body)
  }
  const fieldError = (field: string) =>
    isApiError(save.error) ? save.error.fieldError(field) : undefined

  return (
    <form noValidate onSubmit={onSubmit} className="space-y-4">
      <div className="space-y-1.5">
        <Label htmlFor="settings-name">Display name</Label>
        <Input
          id="settings-name"
          value={displayName}
          maxLength={60}
          onChange={(e) => setDisplayName(e.target.value)}
        />
        <p className="text-xs text-ink-500">
          {fieldError("displayName") ??
            `Shown publicly as “${shortName(displayName || user.displayName)}”.`}
        </p>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="settings-phone">
          Phone <span className="font-normal text-ink-500">(optional, never shown publicly)</span>
        </Label>
        <Input
          id="settings-phone"
          type="tel"
          value={phone}
          maxLength={20}
          onChange={(e) => setPhone(e.target.value)}
        />
        {fieldError("phone") && <p className="text-xs text-danger-600">{fieldError("phone")}</p>}
      </div>
      <div className="space-y-1.5">
        <p className="text-sm font-semibold text-ink-900">Home city</p>
        <div className="flex items-center gap-2">
          <CityPicker
            value={homeCity}
            onChange={setHomeCity}
            label="Home city"
            className="max-w-[360px] flex-1"
          />
          {homeCity && (
            <Button type="button" variant="ghost" size="sm" onClick={() => setHomeCity(null)}>
              Clear
            </Button>
          )}
        </div>
      </div>
      {save.isError && <Notice tone="danger">{errorMessage(save.error)}</Notice>}
      {save.isSuccess && (
        <p role="status" className="text-sm text-success-600">
          Saved.
        </p>
      )}
      <Button type="submit" disabled={save.isPending}>
        {save.isPending ? "Saving…" : "Save profile"}
      </Button>
    </form>
  )
}

function Roles({ user }: { user: CurrentUser }) {
  const { acceptUpdate } = useAuth()
  const add = useMutation({
    mutationFn: (role: PickableRole) => updateMe({ addRoles: [role] }),
    onSuccess: (update) => acceptUpdate(update),
  })
  const roles: PickableRole[] = ["TENANT", "LANDLORD"]

  return (
    <div className="space-y-3">
      {roles.map((role) => {
        const held = user.roles.includes(role)
        const status = user.verification[role]
        return (
          <div
            key={role}
            className="flex flex-wrap items-center gap-3 rounded-lg border border-line p-4"
          >
            <div className="min-w-0 flex-1">
              <p className="flex flex-wrap items-center gap-2">
                <b className="text-ink-900">{ROLE_COPY[role].title}</b>
                <Badge variant={role === "TENANT" ? "tenant" : "landlord"}>
                  {ROLE_COPY[role].badge}
                </Badge>
                {held && status && (
                  <Badge variant={STATUS[status].variant}>{STATUS[status].label}</Badge>
                )}
              </p>
              <p className="mt-1 text-sm text-ink-500">{ROLE_COPY[role].text}</p>
            </div>
            {held ? (
              <span className="text-sm font-semibold text-success-600">You have this role</span>
            ) : (
              <Button variant="outline" disabled={add.isPending} onClick={() => add.mutate(role)}>
                <PlusIcon />
                Add {ROLE_COPY[role].badge.toLowerCase()} role
              </Button>
            )}
          </div>
        )
      })}
      {user.roles.includes("ADMIN") && (
        <p className="flex items-center gap-2 text-sm text-ink-500">
          <Badge variant="admin">Admin</Badge> Granted by the site operators, not in settings.
        </p>
      )}
      {add.isError && <Notice tone="danger">{errorMessage(add.error)}</Notice>}
    </div>
  )
}

/**
 * `/settings` (DESIGN.md "settings", not mocked): profile edits, add the other role (D-16),
 * approval status per role (D-89) and sign out. Account deletion comes with W7.
 */
export default function SettingsPage() {
  const { state, signOut } = useAuth()
  const navigate = useNavigate()
  const user = state.user
  if (!user) return null

  return (
    <main>
      <Container className="max-w-[720px] space-y-6 py-10 nav:py-14">
        <div>
          <h1 className="font-display text-3xl font-semibold text-ink-900">Settings</h1>
          <p className="mt-1 text-ink-500">Your account and how others see you.</p>
        </div>
        <Section title="Profile">
          <ProfileForm user={user} />
        </Section>
        <Section
          title="Roles"
          description="Be a tenant, a landlord, or both. Roles can be added, not removed."
        >
          <Roles user={user} />
        </Section>
        <Section title="Account">
          <div className="flex flex-wrap items-center gap-3">
            <div className="min-w-0 flex-1 text-sm">
              <p className="text-ink-500">Signed in as</p>
              <p className="truncate font-semibold text-ink-900">
                {user.email ?? user.displayName}
              </p>
              <p className="text-xs text-ink-500">
                {user.demo
                  ? "Demo seed user (dev login)."
                  : "Google account. Your email is never shown publicly."}
              </p>
            </div>
            <Button
              variant="outline"
              onClick={() => {
                // Leave first, or the guard would send the signed-out user to /login.
                navigate("/", { replace: true, state: { signedOut: true } })
                void signOut()
              }}
            >
              <LogoutIcon />
              Sign out
            </Button>
          </div>
        </Section>
      </Container>
    </main>
  )
}
