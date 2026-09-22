import type { AuthResponse, LoginRequest, SignupRequest, User } from "@/types"
import { delay } from "./delay"
import { mockUsers } from "./db"

const FAKE_ACCESS = "mock-access-token"
const FAKE_REFRESH = "mock-refresh-token"

function makeAuthResponse(user: User): AuthResponse {
  return { user, accessToken: FAKE_ACCESS, refreshToken: FAKE_REFRESH }
}

export async function mockLogin(req: LoginRequest): Promise<AuthResponse> {
  await delay()
  const user = mockUsers.find((u) => u.email === req.email)
  if (!user || req.password.length < 4) {
    throw { response: { status: 401, data: { message: "Invalid credentials" } } }
  }
  // Remember who logged in for subsequent /me calls
  currentUserId = user.id
  return makeAuthResponse(user)
}

export async function mockSignup(req: SignupRequest): Promise<AuthResponse> {
  await delay()
  if (mockUsers.some((u) => u.email === req.email)) {
    throw { response: { status: 409, data: { message: "Email already registered" } } }
  }
  const newUser: User = {
    id: mockUsers.length + 1,
    email: req.email,
    name: req.name,
    role: req.role,
  }
  mockUsers.push(newUser)
  currentUserId = newUser.id
  return makeAuthResponse(newUser)
}

export async function mockMe(): Promise<User> {
  await delay(200)
  const user = mockUsers.find((u) => u.id === currentUserId)
  if (!user) {
    throw { response: { status: 401, data: { message: "Not authenticated" } } }
  }
  return user
}

export async function mockRefresh(): Promise<{ accessToken: string }> {
  await delay(200)
  return { accessToken: FAKE_ACCESS }
}

export async function mockLogout(): Promise<void> {
  await delay(100)
  currentUserId = null
}

/** In-memory session — reset on refresh */
let currentUserId: number | null = null
