import { useCallback, useSyncExternalStore } from "react"
import { updateMe } from "@/api/auth"
import { useAuth } from "@/features/auth/authContext"
import { defaultMode, readMode, saveMode, subscribeMode, type Mode } from "./mode"

/**
 * The signed-in user's mode and a setter that remembers it; Tenant for anonymous visitors. A
 * switch shows at once, then is saved to the account so other browsers follow; a failed save
 * keeps the local choice.
 */
export function useMode(): [Mode, (mode: Mode) => void] {
  const { state } = useAuth()
  const user = state.user
  const read = () => (user ? readMode(user) : defaultMode([]))
  const mode = useSyncExternalStore(subscribeMode, read, read)
  const setMode = useCallback(
    (next: Mode) => {
      if (!user) return
      if (readMode(user) === next) return
      saveMode(user.id, next)
      updateMe({ preferredMode: next }).catch((error: unknown) => {
        console.warn("Could not save the mode to the account", error)
      })
    },
    [user],
  )
  return [mode, setMode]
}
