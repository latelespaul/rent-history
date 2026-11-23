import type {
  Page,
  RentRecord,
  RentRecordInput,
  Tenancy,
  TenancyInput,
  TenancyParty,
  TenancyStatus,
  TenancyUpdate,
} from "@/types/api"
import { api } from "./client"

export const tenancyKeys = {
  all: ["tenancies"] as const,
  mine: (viewer: string | null, role: TenancyParty | null, page: number) =>
    ["tenancies", "mine", viewer, role, page] as const,
  detail: (id: string, viewer: string | null) => ["tenancies", "detail", id, viewer] as const,
  rentRecords: (id: string, viewer: string | null) =>
    ["tenancies", "rent-records", id, viewer] as const,
}

export interface MyTenanciesQuery {
  role?: TenancyParty | null
  status?: TenancyStatus[]
  page?: number
  size?: number
}

/** `GET /me/tenancies?role=&status=`: the caller's tenancies, newest first (D-59 page). */
export function fetchMyTenancies(
  query: MyTenanciesQuery = {},
  signal?: AbortSignal,
): Promise<Page<Tenancy>> {
  return api.get<Page<Tenancy>>("/me/tenancies", {
    query: {
      role: query.role ?? undefined,
      status: query.status?.length ? query.status.join(",") : undefined,
      page: query.page ?? 0,
      size: query.size ?? 50,
    },
    signal,
  })
}

const path = (id: string) => `/tenancies/${encodeURIComponent(id)}`

/** `GET /tenancies/{id}`: parties and admins only, else 404. */
export function fetchTenancy(id: string, signal?: AbortSignal): Promise<Tenancy> {
  return api.get<Tenancy>(path(id), { signal })
}

/** `POST /tenancies` (idempotent): a landlord invite, or a tenant's request to the landlord. */
export function createTenancy(body: TenancyInput, idempotencyKey: string): Promise<Tenancy> {
  return api.post<Tenancy>("/tenancies", body, { idempotencyKey })
}

/** `PATCH /tenancies/{id}`: the party who added it corrects its period, rent or deposit (D-156). */
export function updateTenancy(id: string, body: TenancyUpdate): Promise<Tenancy> {
  return api.patch<Tenancy>(path(id), body)
}

/** `DELETE /tenancies/{id}`: the party who added it removes it (D-156). */
export function deleteTenancy(id: string): Promise<void> {
  return api.delete<void>(path(id))
}

/** `POST /tenancies/{id}/confirm`: the counterparty, approval-gated (D-35). */
export function confirmTenancy(id: string): Promise<Tenancy> {
  return api.post<Tenancy>(`${path(id)}/confirm`)
}

/** `POST /tenancies/{id}/decline` with an optional reason; never gated (D-35). */
export function declineTenancy(id: string, reason?: string): Promise<Tenancy> {
  return api.post<Tenancy>(`${path(id)}/decline`, reason ? { reason } : {})
}

/** `POST /tenancies/{id}/cancel`: the initiator withdraws a pending request; never gated. */
export function cancelTenancy(id: string): Promise<Tenancy> {
  return api.post<Tenancy>(`${path(id)}/cancel`)
}

/** `POST /tenancies/{id}/end` with the last day (≤ today, D-36). */
export function endTenancy(
  id: string,
  body: { endDate: string; depositRefundedInr?: number },
): Promise<Tenancy> {
  return api.post<Tenancy>(`${path(id)}/end`, body)
}

/** `POST /tenancy-invites/claim`: binds an email invite to the signed-in tenant (D-34). */
export function claimInvite(token: string): Promise<Tenancy> {
  return api.post<Tenancy>("/tenancy-invites/claim", { token })
}

/** `GET /tenancies/{id}/rent-records`: every record, oldest first. */
export function fetchRentRecords(id: string, signal?: AbortSignal): Promise<RentRecord[]> {
  return api.get<RentRecord[]>(`${path(id)}/rent-records`, { signal })
}

/** `POST /tenancies/{id}/rent-records` → `PROPOSED` until the other party confirms (D-25). */
export function addRentRecord(id: string, body: RentRecordInput): Promise<RentRecord> {
  return api.post<RentRecord>(`${path(id)}/rent-records`, body)
}

/** `POST /rent-records/{id}/confirm`. */
export function confirmRentRecord(id: string): Promise<RentRecord> {
  return api.post<RentRecord>(`/rent-records/${encodeURIComponent(id)}/confirm`)
}

/** `POST /rent-records/{id}/dispute`. */
export function disputeRentRecord(id: string): Promise<RentRecord> {
  return api.post<RentRecord>(`/rent-records/${encodeURIComponent(id)}/dispute`)
}

/** `PATCH /rent-records/{id}`: the party who added it corrects it (D-156). */
export function updateRentRecord(id: string, body: RentRecordInput): Promise<RentRecord> {
  return api.patch<RentRecord>(`/rent-records/${encodeURIComponent(id)}`, body)
}

/** `DELETE /rent-records/{id}`: the party who added it removes it (D-156). */
export function deleteRentRecord(id: string): Promise<void> {
  return api.delete<void>(`/rent-records/${encodeURIComponent(id)}`)
}
