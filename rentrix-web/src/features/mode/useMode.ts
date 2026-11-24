import { useCallback, useSyncExternalStore } from "react"
import { updateMe } from "@/api/auth"
import { useAuth } from "@/features/auth/authContext"
import { defaultMode, readMode, saveMode, subscribeMode, type Mode } from "./mode"

type AccountSave = { inFlight: boolean; next?: Mode }

const accountSaves = new Map<string, AccountSave>()

function saveToAccount(userId: string, mode: Mode) {
  let save = accountSaves.get(userId)
  if (!save) {
    save = { inFlight: false }
    accountSaves.set(userId, save)
  }
  save.next = mode
  if (!save.inFlight) sendNext(userId, save)
}

function sendNext(userId: string, save: AccountSave) {
  const mode = save.next
  save.next = undefined
  if (mode === undefined) {
    accountSaves.delete(userId)
    return
  }
  save.inFlight = true
  updateMe({ preferredMode: mode })
    .catch((error: unknown) => {
      console.warn("Could not save the mode to the account", error)
    })
    .finally(() => {
      save.inFlight = false
      sendNext(userId, save)
    })
}

/**
 * The signed-in user's mode and a setter that remembers it; Tenant for anonymous visitors. A
 * switch shows at once, then is saved to the account so other browsers follow; a failed save
 * keeps the local choice. Saves go one at a time, and a switch made while one is in flight
 * waits for it, so the account ends on the latest choice.
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
      saveToAccount(user.id, next)
    },
    [user],
  )
  return [mode, setMode]
}
