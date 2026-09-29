import type { Flat, FlatFilters, Page } from "@/types"
import { api } from "./client"
import { USE_MOCKS } from "./config"
import { mockGetFlat, mockListFlats } from "./mock/flats.mock"

export const flatsApi = {
  list: (filters: FlatFilters = {}): Promise<Page<Flat>> =>
    USE_MOCKS ? mockListFlats(filters) : api.get("/flats", { params: filters }).then((r) => r.data),

  get: (id: number): Promise<Flat> =>
    USE_MOCKS ? mockGetFlat(id) : api.get(`/flats/${id}`).then((r) => r.data),
}
