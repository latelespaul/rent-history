import { create } from "zustand"
import { persist, createJSONStorage } from "zustand/middleware"
import type { Role, User } from "@/types"
import { tokenStore } from "@/api/client.ts"

interface AuthState {
  user: User | null
  accessToken: string | null
  refreshToken: string | null
  isAuthenticated: boolean

  setAuth: (user: User, accessToken: string, refreshToken: string) => void
  setUser: (user: User) => void
  clearAuth: () => void
  hasRole: (role: Role | Role[]) => boolean
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,

      setAuth: (user, accessToken, refreshToken) => {
        tokenStore.set(accessToken, refreshToken)
        set({ user, accessToken, refreshToken, isAuthenticated: true })
      },

      setUser: (user) => set({ user }),

      clearAuth: () => {
        tokenStore.clear()
        set({
          user: null,
          accessToken: null,
          refreshToken: null,
          isAuthenticated: false,
        })
      },

      hasRole: (role) => {
        const current = get().user?.role
        if (!current) return false
        return Array.isArray(role) ? role.includes(current) : current === role
      },
    }),
    {
      name: "rentio-auth",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        user: state.user,
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        isAuthenticated: state.isAuthenticated,
      }),
      onRehydrateStorage: () => (state) => {
        // Re-sync tokenStore after localStorage rehydrate
        if (state?.accessToken) {
          tokenStore.set(state.accessToken, state.refreshToken)
        }
      },
    },
  ),
)
