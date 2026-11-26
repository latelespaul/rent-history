import { api } from "./client"

/** `GET /me/invite`: the caller's own invite code and how many people joined with it, never who. */
export interface InviteView {
  code: string
  joined: number
}

export const inviteKeys = { mine: ["me", "invite"] as const }

export function fetchInvite(signal?: AbortSignal): Promise<InviteView> {
  return api.get<InviteView>("/me/invite", { signal })
}
