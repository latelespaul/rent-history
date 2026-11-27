// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { captureLanding, createPageViewTracker, sendPageView, shouldTrack } from "./traffic"

const nav = (over: Partial<Navigator> = {}) =>
  ({
    userAgent: "Mozilla/5.0 Chrome/120",
    doNotTrack: null,
    webdriver: false,
    ...over,
  }) as Navigator

const readBlob = (blob: Blob) =>
  new Promise<string>((resolve) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.readAsText(blob)
  })

afterEach(() => vi.restoreAllMocks())

describe("shouldTrack", () => {
  it("counts an ordinary browser", () => {
    expect(shouldTrack(nav())).toBe(true)
  })
  it("respects Do-Not-Track", () => {
    expect(shouldTrack(nav({ doNotTrack: "1" }))).toBe(false)
  })
  it("skips bots and automation", () => {
    expect(shouldTrack(nav({ userAgent: "Googlebot/2.1" }))).toBe(false)
    expect(shouldTrack(nav({ webdriver: true }))).toBe(false)
  })
  it("skips prerendering", () => {
    expect(
      shouldTrack(nav(), { prerendering: true, visibilityState: "visible" } as unknown as Document),
    ).toBe(false)
    expect(shouldTrack(nav(), { visibilityState: "prerender" } as unknown as Document)).toBe(false)
  })
})

describe("page view tracker", () => {
  it("sends one view per route change, never a ref code", () => {
    captureLanding("https://doorwayliving.in/?ref=K7QM2XWR4D")
    const send = vi.fn()
    const track = createPageViewTracker(send)
    track("/")
    track("/")
    track("/search")
    expect(send).toHaveBeenCalledTimes(2)
    expect(send.mock.calls[0][0]).toMatchObject({ path: "/", source: "invite" })
    expect(send.mock.calls[1][0]).toEqual({ path: "/search", referrer: "", source: "internal" })
    expect(JSON.stringify(send.mock.calls)).not.toContain("K7QM2XWR4D")
  })

  it("uses utm_source when there is no ref", () => {
    captureLanding("https://doorwayliving.in/?utm_source=Newsletter")
    const send = vi.fn()
    createPageViewTracker(send)("/")
    expect(send.mock.calls[0][0].source).toBe("newsletter")
  })
})

describe("sendPageView", () => {
  it("posts JSON with sendBeacon and survives a missing beacon", async () => {
    const sendBeacon = vi.fn(() => true)
    sendPageView({ path: "/search", referrer: "", source: null }, {
      sendBeacon,
    } as unknown as Navigator)
    const [url, blob] = sendBeacon.mock.calls[0] as unknown as [string, Blob]
    expect(url).toMatch(/\/metrics\/pageview$/)
    expect(JSON.parse(await readBlob(blob))).toEqual({
      path: "/search",
      referrer: "",
      source: null,
    })
    expect(() =>
      sendPageView({ path: "/", referrer: "", source: null }, {} as Navigator),
    ).not.toThrow()
  })
})
