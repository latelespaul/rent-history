import type { AgreementFields, RentalAgreement, ReviewTask, SignedUrl } from "@/types/api"
import { api } from "./client"

export const agreementKeys = {
  forTenancy: (tenancyId: string, viewer: string | null) =>
    ["agreements", "tenancy", tenancyId, viewer] as const,
  reviewTasks: (viewer: string | null) => ["review-tasks", viewer] as const,
}

const path = (id: string) => `/me/agreements/${encodeURIComponent(id)}`

/**
 * `POST /me/agreements` (D-123, D-131): multipart `file` plus the JSON `data` part with the
 * tenancy, the explicit consent and the typed fields. Answers the stored `DRAFT` (fields masked).
 */
export function uploadAgreement(input: {
  tenancyId: string
  consent: boolean
  fields: AgreementFields
  file: File
}): Promise<RentalAgreement> {
  const body = new FormData()
  body.append("file", input.file)
  body.append(
    "data",
    new Blob(
      [
        JSON.stringify({
          tenancyId: input.tenancyId,
          consent: input.consent,
          fields: input.fields,
        }),
      ],
      { type: "application/json" },
    ),
  )
  return api.post<RentalAgreement>("/me/agreements", body)
}

/** `GET /tenancies/{id}/agreement`: the live agreement of the tenancy, else 404. */
export function fetchTenancyAgreement(
  tenancyId: string,
  signal?: AbortSignal,
): Promise<RentalAgreement> {
  return api.get<RentalAgreement>(`/tenancies/${encodeURIComponent(tenancyId)}/agreement`, {
    signal,
  })
}

/** `PATCH /me/agreements/{id}`: the uploader corrects the fields before the team decides. */
export function correctAgreement(id: string, fields: AgreementFields): Promise<RentalAgreement> {
  return api.patch<RentalAgreement>(path(id), fields)
}

/** `POST /me/agreements/{id}/confirm`: the uploader confirms; it waits for the team's check. */
export function confirmAgreement(id: string): Promise<RentalAgreement> {
  return api.post<RentalAgreement>(`${path(id)}/confirm`)
}

/** `GET /me/agreements/{id}/url`: a 5-minute signed link to the file (D-131). */
export function agreementFileUrl(id: string): Promise<SignedUrl> {
  return api.get<SignedUrl>(`${path(id)}/url`)
}

/** `GET /me/review-tasks`: the reviews the caller can write now. */
export function fetchReviewTasks(signal?: AbortSignal): Promise<ReviewTask[]> {
  return api.get<ReviewTask[]>("/me/review-tasks", { signal })
}
