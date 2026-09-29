import type { Flat, FlatFilters, Page } from "@/types"
import { delay, maybeFail } from "./delay"
import { mockFlats } from "./db"

export async function mockListFlats(filters: FlatFilters = {}): Promise<Page<Flat>> {
  await delay()
  await maybeFail(0.02)

  let filtered = [...mockFlats]

  if (filters.city) {
    const q = filters.city.toLowerCase()
    filtered = filtered.filter((f) => f.city.toLowerCase().includes(q))
  }
  if (filters.state) {
    filtered = filtered.filter((f) => f.state.toLowerCase() === filters.state!.toLowerCase())
  }
  if (filters.minRent != null) {
    filtered = filtered.filter((f) => f.rent >= filters.minRent!)
  }
  if (filters.maxRent != null) {
    filtered = filtered.filter((f) => f.rent <= filters.maxRent!)
  }
  if (filters.numberOfRooms != null) {
    filtered = filtered.filter((f) => f.numberOfRooms === filters.numberOfRooms)
  }
  if (filters.available != null) {
    filtered = filtered.filter((f) => f.isAvailable === filters.available)
  }

  const page = filters.page ?? 0
  const size = filters.size ?? 9
  const start = page * size
  const content = filtered.slice(start, start + size)

  return {
    content,
    totalElements: filtered.length,
    totalPages: Math.ceil(filtered.length / size),
    number: page,
    size,
  }
}

export async function mockGetFlat(id: number): Promise<Flat> {
  await delay()
  const flat = mockFlats.find((f) => f.id === id)
  if (!flat) {
    throw { response: { status: 404, data: { message: "Flat not found" } } }
  }
  return flat
}
