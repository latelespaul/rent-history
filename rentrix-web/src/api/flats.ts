import type {
  CreateFlatRequest,
  Flat,
  FlatFilters,
  FlatImage,
  FlatSummary,
  UpdateFlatRequest,
} from "@/types"
import type { Page } from "@/types"
import { resolveApiUrl } from "@/lib/apiConfig"
import { api } from "./client"

// Image URLs from the API may be absolute (S3/CDN) or root-relative; resolve
// relative ones against the API origin so they work on any static host.
const withImageUrl = (img: FlatImage): FlatImage => ({ ...img, url: resolveApiUrl(img.url) })

const withSummaryUrls = (page: Page<FlatSummary>): Page<FlatSummary> => ({
  ...page,
  content: page.content.map((f) => ({ ...f, primaryImageUrl: resolveApiUrl(f.primaryImageUrl) })),
})

const withFlatUrls = (flat: Flat): Flat => ({
  ...flat,
  primaryImageUrl: resolveApiUrl(flat.primaryImageUrl),
  images: flat.images?.map(withImageUrl),
})

export const flatsApi = {
  list: (filters: FlatFilters = {}): Promise<Page<FlatSummary>> =>
    api.get("/flats", { params: filters }).then((r) => withSummaryUrls(r.data)),

  listMine: (page = 0, size = 20): Promise<Page<FlatSummary>> =>
    api.get("/flats/me", { params: { page, size } }).then((r) => withSummaryUrls(r.data)),

  get: (id: number): Promise<Flat> => api.get(`/flats/${id}`).then((r) => withFlatUrls(r.data)),

  create: (payload: CreateFlatRequest): Promise<Flat> =>
    api.post("/flats", payload).then((r) => withFlatUrls(r.data)),

  update: (id: number, payload: UpdateFlatRequest): Promise<Flat> =>
    api.patch(`/flats/${id}`, payload).then((r) => withFlatUrls(r.data)),

  remove: (id: number): Promise<void> => api.delete(`/flats/${id}`).then(() => undefined),

  // ── Images ───────────────────────────────────────────────────────────

  listImages: (flatId: number): Promise<FlatImage[]> =>
    api.get(`/flats/${flatId}/images`).then((r) => r.data.map(withImageUrl)),

  uploadImage: (flatId: number, file: File): Promise<FlatImage> => {
    const formData = new FormData()
    formData.append("file", file)
    return api
      .post(`/flats/${flatId}/images`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      })
      .then((r) => withImageUrl(r.data))
  },

  deleteImage: (flatId: number, imageId: number): Promise<void> =>
    api.delete(`/flats/${flatId}/images/${imageId}`).then(() => undefined),

  setPrimaryImage: (flatId: number, imageId: number): Promise<FlatImage> =>
    api
      .patch(`/flats/${flatId}/images/${imageId}/primary`)
      .then((r) => withImageUrl(r.data)),
}