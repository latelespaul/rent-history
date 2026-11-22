import { useCallback, useEffect, useState } from "react"

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
 * starts empty. `clear` drops the draft once the form is sent.
 */
export function useSessionDraft<T extends object>(key: string, initial: T) {
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
