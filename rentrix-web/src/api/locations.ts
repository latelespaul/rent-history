import type {
  AreaCard,
  Page,
  SocietyCard,
  SocietyDetail,
  SocietyGroup,
  TowerView,
} from "@/types/api"
import { api } from "./client"

/** Picker reads of the location hierarchy (§12.2, D-108). Lists include the caller's own proposals. */
export const locationKeys = {
  areas: (city: string, q: string, viewer: string | null) =>
    ["locations", "areas", city, q, viewer] as const,
  societies: (city: string, area: string, viewer: string | null) =>
    ["locations", "societies", city, area, viewer] as const,
  towers: (society: string, viewer: string | null) =>
    ["locations", "towers", society, viewer] as const,
  society: (id: string, viewer: string | null) => ["locations", "society", id, viewer] as const,
  summary: (id: string, viewer: string | null) =>
    ["locations", "society-summary", id, viewer] as const,
}

/** A 6-digit text is a PIN code; anything else is a word prefix of the name or an alias. */
export function areaQuery(text: string): { q?: string; pincode?: string } {
  const value = text.trim()
  if (!value) return {}
  return /^\d{6}$/.test(value) ? { pincode: value } : { q: value }
}

/** `GET /areas?city=&q=|pincode=`: areas by name or PIN code (D-108). */
export async function searchAreas(
  city: string,
  text: string,
  signal?: AbortSignal,
): Promise<AreaCard[]> {
  const page = await api.get<Page<AreaCard>>("/areas", {
    query: { city, ...areaQuery(text), size: 20 },
    signal,
  })
  return page.items
}

/** `GET /societies?city=&area=`: the societies of one area (D-112). */
export async function fetchSocieties(
  city: string,
  area: string,
  signal?: AbortSignal,
): Promise<SocietyCard[]> {
  const page = await api.get<Page<SocietyCard>>("/societies", {
    query: { city, area, size: 50 },
    signal,
  })
  return page.items
}

/** `GET /societies/{id}/towers`: towers and wings for the form pickers. */
export function fetchTowers(society: string, signal?: AbortSignal): Promise<TowerView[]> {
  return api.get<TowerView[]>(`/societies/${encodeURIComponent(society)}/towers`, { signal })
}

/** `GET /societies/{id}`: the society page header and towers (D-112). */
export function fetchSociety(id: string, signal?: AbortSignal): Promise<SocietyDetail> {
  return api.get<SocietyDetail>(`/societies/${encodeURIComponent(id)}`, { signal })
}

/** `GET /societies/{id}/summary`: flats, reviews and (signed in) rent range (D-166). */
export function fetchSocietySummary(id: string, signal?: AbortSignal): Promise<SocietyGroup> {
  return api.get<SocietyGroup>(`/societies/${encodeURIComponent(id)}/summary`, { signal })
}
