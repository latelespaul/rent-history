import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import type { AxiosError } from "axios"
import { flatsApi } from "@/api/flats"
import { queryKeys } from "@/api/queryKeys"
import { useAuthStore } from "@/features/auth/store"
import imageCompression from "browser-image-compression"

export function useFlatImages(flatId: number) {
  return useQuery({
    queryKey: queryKeys.flats.images(flatId),
    queryFn: () => flatsApi.listImages(flatId),
    enabled: Number.isFinite(flatId) && flatId > 0,
    staleTime: 1000 * 60,
  })
}
async function compressIfNeeded(file: File): Promise<File> {
  if (file.size <= 2 * 1024 * 1024) return file // already small
  return imageCompression(file, {
    maxSizeMB: 2,
    maxWidthOrHeight: 1920,
    useWebWorker: true,
  })
}
export function useUploadFlatImage(flatId: number) {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: async (file: File) => {
      const compressed = await compressIfNeeded(file)
      return flatsApi.uploadImage(flatId, compressed)
    },
    onSuccess: async () => {
      toast.success("Image uploaded")
      await Promise.all([
        qc.invalidateQueries({ queryKey: queryKeys.flats.images(flatId) }),
        qc.invalidateQueries({ queryKey: queryKeys.flats.detail(flatId) }),
        qc.invalidateQueries({ queryKey: queryKeys.flats.all }),
      ])
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      toast.error(err.response?.data?.message ?? "Upload failed")
    },
  })
}

export function useDeleteFlatImage(flatId: number) {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: (imageId: number) => flatsApi.deleteImage(flatId, imageId),
    onSuccess: async () => {
      toast.success("Image deleted")
      await Promise.all([
        qc.invalidateQueries({ queryKey: queryKeys.flats.images(flatId) }),
        qc.invalidateQueries({ queryKey: queryKeys.flats.detail(flatId) }),
        qc.invalidateQueries({ queryKey: queryKeys.flats.all }),
      ])
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      toast.error(err.response?.data?.message ?? "Delete failed")
    },
  })
}

export function useSetPrimaryImage(flatId: number) {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: (imageId: number) => flatsApi.setPrimaryImage(flatId, imageId),
    onSuccess: async () => {
      toast.success("Primary image updated")
      await Promise.all([
        qc.invalidateQueries({ queryKey: queryKeys.flats.images(flatId) }),
        qc.invalidateQueries({ queryKey: queryKeys.flats.detail(flatId) }),
        qc.invalidateQueries({ queryKey: queryKeys.flats.all }),
      ])
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      toast.error(err.response?.data?.message ?? "Failed to set primary image")
    },
  })
}

/** Helper: is the current user allowed to manage this flat's images? */
export function useCanManageFlat(flatOwnerId?: number, flatCreatorId?: number) {
  const user = useAuthStore((s) => s.user)
  if (!user) return false
  if (user.role === "ADMIN") return true
  return user.id === flatOwnerId || user.id === flatCreatorId
}