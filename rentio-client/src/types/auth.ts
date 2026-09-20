export type Role = "TENANT" | "LANDLORD" | "ADMIN"

export interface User {
  id: number
  email: string
  name: string
  role: Role
}

export interface AuthResponse {
  user: User
  accessToken: string
  refreshToken: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface SignupRequest {
  email: string
  password: string
  name: string
  role: Role
}
