import { useCallback, useEffect, useState } from "react"

const PREFIX = "draft:"

function read<T extends object>(key: string, initial: T): T {
  try {
    const raw = window.sessionStorage.getItem(key)
    return raw ? { ...initial, ...(JSON.parse(raw) as Partial<T>) } : initial
  } catch {
    return initial
  }
}

/**
 * Form state kept in this tab's `sessionStorage`, so a user sent to verify their phone comes back
 * to the form as they left it (D-119). Storage can be missing or blocked: the form then simply
 * starts empty. `clear` drops the draft once the form is sent; sign-out drops them all
 * (`clearSessionDrafts`), so the next user of the tab never sees them.
 */
export function useSessionDraft<T extends object>(name: string, initial: T) {
  const key = PREFIX + name
  const [value, setValue] = useState<T>(() => read(key, initial))
  useEffect(() => {
    try {
      window.sessionStorage.setItem(key, JSON.stringify(value))
    } catch {
      // A convenience only: nothing to do without storage.
    }
  }, [key, value])
  const clear = useCallback(() => {
    try {
      window.sessionStorage.removeItem(key)
    } catch {
      // As above.
    }
  }, [key])
  return [value, setValue, clear] as const
}

/** Drops every form draft of this tab (on sign-out). */
export function clearSessionDrafts() {
  try {
    const storage = window.sessionStorage
    const keys = Array.from({ length: storage.length }, (_, i) => storage.key(i))
    keys.filter((k): k is string => k?.startsWith(PREFIX) ?? false).forEach((k) => storage.removeItem(k))
  } catch {
    // No storage, no drafts.
  }
}
