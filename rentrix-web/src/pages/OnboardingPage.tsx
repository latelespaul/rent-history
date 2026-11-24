import { useState, type FormEvent } from "react"
import { useMutation } from "@tanstack/react-query"
import { Navigate, useNavigate, useSearchParams } from "react-router-dom"
import { completeOnboarding, updateMe } from "@/api/auth"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Notice } from "@/components/ui/notice"
import { errorMessage } from "@/components/ui/states"
import { UserIcon } from "@/components/ui/icons.gen"
import { useAuth } from "@/features/auth/authContext"
import { publicName, safeNext } from "@/features/auth/paths"
import { RoleChoice } from "@/features/auth/RoleChoice"
import { roleHome, type PickableRole } from "@/features/auth/roles"
import { CityPicker } from "@/features/city/CityPicker"
import { LaunchCopy } from "@/features/city/LaunchCopy"
import { BRAND } from "@/lib/brand"
import type { CreatedState } from "@/pages/WelcomeCreatedPage"

/**
 * `/welcome` (DESIGN.md screen 10): the first-sign-in role pick (D-73). It posts
 * `/me/onboarding`, swaps in the fresh access token that carries the new roles, then saves a
 * changed display name with `PATCH /me`, and opens `/welcome/created` (D-118).
 */
export default function OnboardingPage() {
  const { state, acceptUpdate } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const user = state.user
  const [roles, setRoles] = useState<PickableRole[]>([])
  const [homeCity, setHomeCity] = useState<string | null>(user?.homeCity ?? null)
  const [displayName, setDisplayName] = useState(user?.displayName ?? "")
  const [acceptTerms, setAcceptTerms] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)

  const submit = useMutation({
    mutationFn: async () => {
      let done = acceptUpdate(await completeOnboarding({ roles, homeCity, acceptTerms }))
      const name = displayName.trim()
      if (name !== done.displayName) {
        try {
          done = acceptUpdate(await updateMe({ displayName: name }))
        } catch {
          // Onboarding is done; the name can still be changed in settings.
        }
      }
      return done
    },
    // On to the account-created screen (D-118); it keeps `next` for its "Not now" link.
    onSuccess: () =>
      navigate("/welcome/created", {
        replace: true,
        state: { created: true, next: params.get("next") } satisfies CreatedState,
      }),
  })

  if (!user) return null
  if (!user.needsOnboarding && !submit.isPending && !submit.isSuccess) {
    return <Navigate to={safeNext(params.get("next"), roleHome(user))} replace />
  }

  const toggle = (role: PickableRole, on: boolean) =>
    setRoles((current) =>
      on ? [...new Set([...current, role])] : current.filter((r) => r !== role),
    )

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (roles.length === 0) return setProblem("Pick tenant, landlord or both.")
    const nameLength = displayName.trim().length
    if (nameLength < 2 || nameLength > 60)
      return setProblem("Use 2 to 60 characters for your display name.")
    if (!acceptTerms) return setProblem("Accept the Terms and Privacy notice to continue.")
    setProblem(null)
    submit.mutate()
  }

  const apiError = submit.error

  return (
    <main>
      <Container className="max-w-[720px] py-10 nav:py-14">
        <div className="flex items-center gap-3.5">
          <span className="grid size-16 shrink-0 place-items-center overflow-hidden rounded-full bg-sunk text-ink-700">
            {user.avatarUrl ? (
              <img
                src={user.avatarUrl}
                alt=""
                referrerPolicy="no-referrer"
                className="size-full object-cover"
              />
            ) : (
              <UserIcon size={32} />
            )}
          </span>
          <div className="min-w-0">
            <p className="text-xs font-semibold tracking-wide text-teal-700 uppercase">
              {user.demo ? "Signed in with dev login" : "Signed in with Google"}
            </p>
            <h1 className="font-display text-3xl font-semibold text-ink-900">
              Welcome to {BRAND.name}
            </h1>
            {user.email && <p className="truncate text-sm text-ink-500">{user.email}</p>}
          </div>
        </div>

        <form
          noValidate
          onSubmit={onSubmit}
          className="mt-6 rounded-xl border border-line bg-surface p-6 shadow-sm"
        >
          <fieldset>
            <legend className="text-xl font-semibold text-ink-900">
              How will you use {BRAND.name}?
            </legend>
            <p className="mt-1 mb-3.5 text-sm text-ink-500">
              Pick one or both. You can add the other later in settings.
            </p>
            <div className="space-y-3">
              <RoleChoice
                role="TENANT"
                checked={roles.includes("TENANT")}
                onChange={(on) => toggle("TENANT", on)}
              />
              <RoleChoice
                role="LANDLORD"
                checked={roles.includes("LANDLORD")}
                onChange={(on) => toggle("LANDLORD", on)}
              />
            </div>
          </fieldset>

          <hr className="my-6 border-line" />

          <div className="space-y-1.5">
            <p className="text-sm font-semibold text-ink-900">
              Home city <span className="font-normal text-ink-500">(optional)</span>
            </p>
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
            <p className="text-xs text-ink-500">
              <LaunchCopy className="block" />
              Leave empty if your city is not listed yet.
            </p>
          </div>

          <div className="mt-4 space-y-1.5">
            <Label htmlFor="display-name">Display name</Label>
            <Input
              id="display-name"
              value={displayName}
              maxLength={60}
              onChange={(e) => setDisplayName(e.target.value)}
            />
            <p className="text-xs text-ink-500">
              Shown publicly as &ldquo;{publicName(user, displayName)}&rdquo;. You can change it
              later.
            </p>
          </div>

          <label className="mt-4 flex items-start gap-2.5 text-sm text-ink-900">
            <input
              type="checkbox"
              checked={acceptTerms}
              onChange={(e) => setAcceptTerms(e.target.checked)}
              className="mt-0.5 size-[18px] accent-teal-600"
            />
            <span>
              I accept the Terms and Privacy notice. I will review only tenancies I was part of.
            </span>
          </label>

          {(problem || apiError) && (
            <Notice tone="danger" className="mt-4">
              {problem ?? errorMessage(apiError)}
            </Notice>
          )}

          <Button type="submit" size="lg" className="mt-5 w-full" disabled={submit.isPending}>
            {submit.isPending ? "Saving…" : "Continue"}
          </Button>
          <p className="mt-2 text-xs text-ink-500">
            Until you finish this step, only your profile and public pages are available.
          </p>
          <Notice tone="teal" className="mt-3.5">
            <b>Next: verify with any one: phone, Aadhaar or LinkedIn.</b> A phone code takes a
            minute, and then you can post right away. You can read every review meanwhile.
          </Notice>
        </form>
      </Container>
    </main>
  )
}
