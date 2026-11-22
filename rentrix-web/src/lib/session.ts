import { useQuery } from "@tanstack/react-query"
import { api } from "@/api/client"
import type { ShellViewer } from "@/components/layout/nav"
import type { CurrentUser } from "@/types/api"

/**
 * The minimal session W4 needs: on page load trade the refresh cookie for an access token
 * (`POST /auth/refresh`, D-63), then read `GET /me`. Anonymous visitors resolve to `null`.
 * W3 (sign-in screens) owns signing in and out and builds on this query key.
 */
export const sessionKeys = {
  all: ["session"] as const,
}

export async function loadSession(): Promise<CurrentUser | null> {
  if (!(await api.restoreSession())) return null
  try {
    return await api.get<CurrentUser>("/me")
  } catch {
    return null
  }
}

export function useSession() {
  return useQuery({
    queryKey: sessionKeys.all,
    queryFn: loadSession,
    staleTime: Infinity,
    retry: false,
  })
}

/** "Demo Tenant BLR-1" → "Demo B." (first word + last initial, as the API's public names, §5.1). */
export function shortName(displayName: string): string {
  const words = displayName.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return "Rentrix user"
  if (words.length === 1) return words[0]
  return `${words[0]} ${words[words.length - 1].charAt(0).toUpperCase()}.`
}

/** The shell's view of the signed-in user. */
export function toViewer(user: CurrentUser | null | undefined): ShellViewer | null {
  if (!user) return null
  return { shortName: shortName(user.displayName), roles: user.roles, isDemo: user.demo === true }
}
