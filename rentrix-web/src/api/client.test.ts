import { describe, expect, it, vi } from "vitest"
import { createApiClient } from "./client"
import { ApiError } from "./errors"

const BASE = "https://api.example.test/api/v1"

function json(status: number, body?: unknown, headers: Record<string, string> = {}): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  })
}

function problem(status: number, code: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ status, code, title: "Problem", ...extra }), {
    status,
    headers: { "Content-Type": "application/problem+json" },
  })
}

type Route = (url: string, init: RequestInit) => Response | Promise<Response>

/** A fetch double: each call is answered by the next route, and recorded for assertions. */
function fakeFetch(...routes: Route[]) {
  const calls: { url: string; init: RequestInit }[] = []
  const fn = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input)
    calls.push({ url, init })
    const route = routes.shift()
    if (!route) throw new Error(`unexpected fetch ${init.method} ${url}`)
    return route(url, init)
  })
  return { fn: fn as unknown as typeof fetch, calls }
}

const header = (init: RequestInit, name: string) =>
  (init.headers as Record<string, string> | undefined)?.[name]

describe("api client", () => {
  it("attaches the in-memory access token as a Bearer header", async () => {
    const f = fakeFetch(() => json(200, [{ code: "BLR" }]))
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn })
    api.setAccessToken("tok-1")

    await expect(api.get("/cities")).resolves.toEqual([{ code: "BLR" }])

    expect(f.calls[0].url).toBe(`${BASE}/cities`)
    expect(header(f.calls[0].init, "Authorization")).toBe("Bearer tok-1")
    expect(f.calls[0].init.credentials).toBe("include")
  })

  it("sends no Authorization header when signed out", async () => {
    const f = fakeFetch(() => json(200, []))
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn })

    await api.get("/cities")

    expect(header(f.calls[0].init, "Authorization")).toBeUndefined()
  })

  it("sends JSON bodies, query params and Idempotency-Key", async () => {
    const f = fakeFetch(() => json(201, { id: "t1" }))
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn })

    await api.post(
      "/tenancies",
      { initialRentInr: 25000 },
      { query: { city: "BLR", q: "", type: undefined, page: 0 }, idempotencyKey: "k-1" },
    )

    expect(f.calls[0].url).toBe(`${BASE}/tenancies?city=BLR&page=0`)
    expect(f.calls[0].init.method).toBe("POST")
    expect(f.calls[0].init.body).toBe('{"initialRentInr":25000}')
    expect(header(f.calls[0].init, "Content-Type")).toBe("application/json")
    expect(header(f.calls[0].init, "Idempotency-Key")).toBe("k-1")
  })

  it("reads the base URL on every request", async () => {
    let base = "/api/v1"
    const f = fakeFetch(
      () => json(200, []),
      () => json(200, []),
    )
    const api = createApiClient({ baseUrl: () => base, fetch: f.fn })

    await api.get("/cities")
    base = BASE
    await api.get("/cities")

    expect(f.calls.map((c) => c.url)).toEqual(["/api/v1/cities", `${BASE}/cities`])
  })

  it("refreshes once on 401 and retries the request with the new token", async () => {
    const f = fakeFetch(
      () => problem(401, "unauthenticated"),
      () => json(200, { accessToken: "tok-2" }),
      () => json(200, { id: "me" }),
    )
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn })
    api.setAccessToken("tok-1")

    await expect(api.get("/me")).resolves.toEqual({ id: "me" })

    expect(f.calls.map((c) => `${c.init.method} ${c.url}`)).toEqual([
      `GET ${BASE}/me`,
      `POST ${BASE}/auth/refresh`,
      `GET ${BASE}/me`,
    ])
    expect(f.calls[1].init.credentials).toBe("include")
    expect(header(f.calls[2].init, "Authorization")).toBe("Bearer tok-2")
    expect(api.getAccessToken()).toBe("tok-2")
  })

  it("does not refresh a second time when the retried request is 401 again", async () => {
    const f = fakeFetch(
      () => problem(401, "unauthenticated"),
      () => json(200, { accessToken: "tok-2" }),
      () => problem(401, "unauthenticated"),
    )
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn })
    api.setAccessToken("tok-1")

    await expect(api.get("/me")).rejects.toMatchObject({ status: 401, code: "unauthenticated" })
    expect(f.calls).toHaveLength(3)
  })

  it("shares one refresh between concurrent 401s", async () => {
    let releaseRefresh: () => void = () => {}
    const refreshGate = new Promise<void>((resolve) => (releaseRefresh = resolve))
    const f = fakeFetch(
      () => problem(401, "unauthenticated"),
      () => problem(401, "unauthenticated"),
      async () => {
        await refreshGate
        return json(200, { accessToken: "tok-2" })
      },
      () => json(200, "a"),
      () => json(200, "b"),
    )
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn })
    api.setAccessToken("tok-1")

    const a = api.get("/a")
    const b = api.get("/b")
    await vi.waitFor(() => expect(f.calls).toHaveLength(3))
    releaseRefresh()

    await expect(Promise.all([a, b])).resolves.toEqual(["a", "b"])
    expect(f.calls.filter((c) => c.url.endsWith("/auth/refresh"))).toHaveLength(1)
  })

  it("clears the session and reports expiry when the refresh is refused", async () => {
    const onSessionExpired = vi.fn()
    const f = fakeFetch(
      () => problem(401, "unauthenticated"),
      () => problem(401, "refresh_token_invalid"),
    )
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn, onSessionExpired })
    api.setAccessToken("tok-1")

    const error = await api.get("/me").catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 401, code: "unauthenticated" })
    expect(api.getAccessToken()).toBeNull()
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
  })

  it("keeps the session when the refresh fails transiently", async () => {
    for (const status of [503, 429]) {
      const onSessionExpired = vi.fn()
      const f = fakeFetch(
        () => problem(401, "unauthenticated"),
        () => problem(status, `http_${status}`),
      )
      const api = createApiClient({ baseUrl: BASE, fetch: f.fn, onSessionExpired })
      api.setAccessToken("tok-1")

      await expect(api.get("/me")).rejects.toMatchObject({ status: 401, code: "unauthenticated" })
      expect(api.getAccessToken()).toBe("tok-1")
      expect(onSessionExpired).not.toHaveBeenCalled()
    }
  })

  it("never refreshes on a 401 from an /auth endpoint", async () => {
    const f = fakeFetch(() => problem(401, "google_token_invalid"))
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn })

    await expect(api.post("/auth/google", { idToken: "x" })).rejects.toMatchObject({
      code: "google_token_invalid",
    })
    expect(f.calls).toHaveLength(1)
  })

  it("restoreSession trades the refresh cookie for an access token", async () => {
    const f = fakeFetch(() => json(200, { accessToken: "tok-9", needsOnboarding: false }))
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn })

    await expect(api.restoreSession()).resolves.toBe(true)
    expect(api.getAccessToken()).toBe("tok-9")
  })

  it("restoreSession resolves false without a valid cookie and reports no expiry", async () => {
    const onSessionExpired = vi.fn()
    const f = fakeFetch(() => problem(401, "unauthenticated"))
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn, onSessionExpired })

    await expect(api.restoreSession()).resolves.toBe(false)
    expect(onSessionExpired).not.toHaveBeenCalled()
  })
})

describe("Problem Details mapping", () => {
  it("maps code, detail, field errors and traceId", async () => {
    const f = fakeFetch(() =>
      problem(422, "validation_failed", {
        title: "Unprocessable Content",
        detail: "Check the highlighted fields.",
        instance: "/api/v1/tenancies",
        errors: [{ field: "body", code: "too_short", message: "At least 30 characters." }],
        traceId: "trace-1",
      }),
    )
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn })

    const error = (await api.post("/tenancies", {}).catch((e: unknown) => e)) as ApiError

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(422)
    expect(error.code).toBe("validation_failed")
    expect(error.message).toBe("Check the highlighted fields.")
    expect(error.fieldError("body")).toBe("At least 30 characters.")
    expect(error.fieldErrors).toHaveLength(1)
    expect(error.traceId).toBe("trace-1")
    expect(error.problem.instance).toBe("/api/v1/tenancies")
  })

  it("falls back to http_<status> and X-Request-Id for a non-Problem body", async () => {
    const f = fakeFetch(
      () =>
        new Response("<html>Bad gateway</html>", {
          status: 502,
          statusText: "Bad Gateway",
          headers: { "X-Request-Id": "req-7" },
        }),
    )
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn })

    await expect(api.get("/cities")).rejects.toMatchObject({
      status: 502,
      code: "http_502",
      traceId: "req-7",
      message: "Bad Gateway",
    })
  })

  it("maps a fetch rejection to network_error with status 0", async () => {
    const f = fakeFetch(() => {
      throw new TypeError("Failed to fetch")
    })
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn })

    await expect(api.get("/cities")).rejects.toMatchObject({ status: 0, code: "network_error" })
  })

  it("returns undefined for 204 No Content", async () => {
    const f = fakeFetch(() => new Response(null, { status: 204 }))
    const api = createApiClient({ baseUrl: BASE, fetch: f.fn })

    await expect(api.delete("/reviews/r1")).resolves.toBeUndefined()
  })
})
