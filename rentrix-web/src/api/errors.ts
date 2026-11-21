import type { ProblemDetails, ProblemFieldError } from "@/types/api"

/** Codes the client itself produces when the API gave no Problem Details body. */
export const CLIENT_ERROR_CODES = {
  network: "network_error",
  aborted: "aborted",
  invalidResponse: "invalid_response",
} as const

/**
 * Every failed API call rejects with this. `code` is the stable machine code from the API
 * (D-60), so callers switch on it instead of on `status` or `message`.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly problem: ProblemDetails

  constructor(problem: ProblemDetails, options?: { cause?: unknown }) {
    super(problem.detail || problem.title || problem.code, options)
    this.name = "ApiError"
    this.status = problem.status
    this.code = problem.code
    this.problem = problem
  }

  get fieldErrors(): ProblemFieldError[] {
    return this.problem.errors ?? []
  }

  /** The message of the first error on `field`, for showing next to a form input. */
  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((e) => e.field === field)?.message
  }

  get traceId(): string | undefined {
    return this.problem.traceId
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

function isProblemBody(body: unknown): body is Partial<ProblemDetails> {
  return typeof body === "object" && body !== null && !Array.isArray(body)
}

/** Turns a non-2xx response into an ApiError, tolerating bodies that are not Problem Details. */
export async function toApiError(response: Response): Promise<ApiError> {
  let body: unknown
  try {
    const text = await response.text()
    body = text ? JSON.parse(text) : undefined
  } catch {
    body = undefined
  }
  const problem = isProblemBody(body) ? body : {}
  const traceId = problem.traceId ?? response.headers.get("X-Request-Id") ?? undefined
  return new ApiError({
    ...problem,
    status: typeof problem.status === "number" ? problem.status : response.status,
    code:
      typeof problem.code === "string" && problem.code ? problem.code : `http_${response.status}`,
    title: problem.title ?? (response.statusText || undefined),
    errors: Array.isArray(problem.errors) ? problem.errors : undefined,
    traceId,
  })
}

/** Wraps a fetch rejection (offline, DNS, CORS, abort) as an ApiError with status 0. */
export function networkError(cause: unknown): ApiError {
  const aborted = cause instanceof DOMException && cause.name === "AbortError"
  return new ApiError(
    {
      status: 0,
      code: aborted ? CLIENT_ERROR_CODES.aborted : CLIENT_ERROR_CODES.network,
      title: aborted ? "Request cancelled" : "Network error",
      detail: aborted ? "The request was cancelled." : "Could not reach the Rentrix API.",
    },
    { cause },
  )
}
