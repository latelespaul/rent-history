import type { AuthResponse, LoginRequest, SignupRequest, User } from "@/types"
import { api } from "./client"

export const authApi = {
  login: (req: LoginRequest): Promise<AuthResponse> =>
    api.post("/auth/login", req).then((r) => r.data),

  signup: (req: SignupRequest): Promise<AuthResponse> =>
    api.post("/auth/signup", req).then((r) => r.data),

  me: (): Promise<User> => api.get("/auth/me").then((r) => r.data),

  refresh: (): Promise<{ accessToken: string }> => api.post("/auth/refresh").then((r) => r.data),

  logout: (): Promise<void> => api.post("/auth/logout").then(() => undefined),
}