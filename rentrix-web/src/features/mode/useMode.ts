import { useCallback, useSyncExternalStore } from "react"
import { useAuth } from "@/features/auth/authContext"
import { defaultMode, readMode, saveMode, subscribeMode, type Mode } from "./mode"

/** The signed-in user's mode and a setter that remembers it; Tenant for anonymous visitors. */
export function useMode(): [Mode, (mode: Mode) => void] {
  const { state } = useAuth()
  const user = state.user
  const read = () => (user ? readMode(user) : defaultMode([]))
  const mode = useSyncExternalStore(subscribeMode, read, read)
  const userId = user?.id
  const setMode = useCallback(
    (next: Mode) => {
      if (userId) saveMode(userId, next)
    },
    [userId],
  )
  return [mode, setMode]
}
