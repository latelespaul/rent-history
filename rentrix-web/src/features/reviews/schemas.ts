import { z } from "zod"

export const createReviewSchema = z.object({
  title: z
    .string()
    .min(3, "Title must be at least 3 characters")
    .max(100, "Title must be under 100 characters"),
  content: z
    .string()
    .min(10, "Review must be at least 10 characters")
    .max(2000, "Review must be under 2000 characters"),
  rating: z
    .number({ error: "Pick a rating" })
    .min(1, "Rating must be at least 1")
    .max(10, "Rating must be at most 10"),
})

export type CreateReviewFormValues = z.infer<typeof createReviewSchema>
