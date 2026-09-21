import type { AuthResponse, LoginRequest, SignupRequest, User } from "@/types"
import { api } from "./client"
import { USE_MOCKS } from "./config"
import { mockLogin, mockLogout, mockMe, mockRefresh, mockSignup } from "./mock/auth.mock"

export const authApi = {
  login: (req: LoginRequest): Promise<AuthResponse> =>
    USE_MOCKS ? mockLogin(req) : api.post("/auth/login", req).then((r) => r.data),

  signup: (req: SignupRequest): Promise<AuthResponse> =>
    USE_MOCKS ? mockSignup(req) : api.post("/auth/signup", req).then((r) => r.data),

  me: (): Promise<User> => (USE_MOCKS ? mockMe() : api.get("/auth/me").then((r) => r.data)),

  refresh: (): Promise<{ accessToken: string }> =>
    USE_MOCKS ? mockRefresh() : api.post("/auth/refresh").then((r) => r.data),

  logout: (): Promise<void> =>
    USE_MOCKS ? mockLogout() : api.post("/auth/logout").then(() => undefined),
}