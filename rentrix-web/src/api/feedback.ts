import { api } from "./client"

/** The form's type chips; the API's `FeedbackType`. */
export type FeedbackType = "FEEDBACK" | "BUG" | "IDEA" | "OTHER"

/** Longest message the API takes (`FeedbackService.MESSAGE_MAX`). */
export const FEEDBACK_MESSAGE_MAX = 2000

export interface FeedbackBody {
  name?: string
  email?: string
  type: FeedbackType
  message: string
  /** The honeypot: hidden from people, so only a bot fills it. */
  website?: string
}

/** `POST /feedback`: open to everyone, rate limited per IP; `204` on success. */
export function sendFeedback(body: FeedbackBody): Promise<void> {
  return api.post<void>("/feedback", body)
}
