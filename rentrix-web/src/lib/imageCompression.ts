import imageCompression from 'browser-image-compression'

/**
 * Compresses an image file before upload.
 * Skips compression for files under the threshold (already small).
 * Skips compression for PDFs (handled separately for proofs).
 */
export interface CompressionResult {
   file: File
   originalSize: number
   compressedSize: number
   wasCompressed: boolean
}

const COMPRESSION_THRESHOLD_BYTES = 1 * 1024 * 1024   // 1 MB
const TARGET_MAX_SIZE_MB = 0.8                        // target ~800 KB
const TARGET_MAX_DIMENSION = 1920                     // matches server-side resize

export async function compressImageIfNeeded(
    file: File,
): Promise<CompressionResult> {
   // Skip non-images (e.g., PDFs for proof uploads)
   if (!file.type.startsWith('image/')) {
      return {
         file,
         originalSize: file.size,
         compressedSize: file.size,
         wasCompressed: false,
      }
   }

   // Skip small images — no benefit to compressing
   if (file.size < COMPRESSION_THRESHOLD_BYTES) {
      return {
         file,
         originalSize: file.size,
         compressedSize: file.size,
         wasCompressed: false,
      }
   }

   try {
      const compressed = await imageCompression(file, {
         maxSizeMB: TARGET_MAX_SIZE_MB,
         maxWidthOrHeight: TARGET_MAX_DIMENSION,
         useWebWorker: true,
         fileType: 'image/jpeg',
         initialQuality: 0.85,
      })

      // Compress returns a Blob in some browsers; wrap in File
      const compressedFile =
          compressed instanceof File
              ? compressed
              : new File([compressed], file.name.replace(/\.[^.]+$/, '.jpg'), {
                 type: 'image/jpeg',
                 lastModified: Date.now(),
              })

      return {
         file: compressedFile,
         originalSize: file.size,
         compressedSize: compressedFile.size,
         wasCompressed: true,
      }
   } catch (error) {
      // If compression fails, upload the original — don't block the user
      console.warn('Image compression failed, uploading original:', error)
      return {
         file,
         originalSize: file.size,
         compressedSize: file.size,
         wasCompressed: false,
      }
   }
}

/**
 * Format bytes for display in the UI.
 */
export function formatBytes(bytes: number): string {
   if (bytes < 1024) return `${bytes} B`
   if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
   return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}