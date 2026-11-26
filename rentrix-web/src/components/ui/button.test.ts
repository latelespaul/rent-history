// @vitest-environment jsdom
/// <reference types="node" />
import { readFileSync } from "node:fs"
import path from "node:path"
import { describe, expect, it } from "vitest"

// The marigold accent (DESIGN.md §4.1). tokens.css is parsed by jsdom's CSS parser, and the
// expected ratios come from the WCAG 2 contrast formula.
const sheet = (() => {
  const style = document.createElement("style")
  style.textContent = readFileSync(
    path.resolve(import.meta.dirname, "../../styles/tokens.css"),
    "utf8",
  )
  document.head.append(style)
  return style.sheet!
})()

function rule(selector: string): CSSStyleRule {
  const found = [...sheet.cssRules].find(
    (r): r is CSSStyleRule => r instanceof CSSStyleRule && r.selectorText === selector,
  )
  if (!found) throw new Error(`no ${selector} rule in tokens.css`)
  return found
}

function token(name: string): string {
  const value = rule(":root").style.getPropertyValue(`--rx-${name}`).trim()
  if (!/^#[0-9a-f]{6}$/i.test(value)) throw new Error(`--rx-${name} is not a hex colour`)
  return value.toLowerCase()
}

/** WCAG 2 contrast ratio of two #rrggbb colours. */
function contrast(a: string, b: string): number {
  const luminance = (hex: string) => {
    const [r, g, b] = [1, 3, 5].map((i) => {
      const c = parseInt(hex.slice(i, i + 2), 16) / 255
      return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
    })
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
  }
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

describe("accent tokens", () => {
  it("are marigold #F4B23E with near-black text", () => {
    expect(token("cta")).toBe("#f4b23e")
    expect(token("cta-fg")).toBe("#142229")
  })

  it.each([
    ["rest", "cta", "cta-fg"],
    ["hover", "cta-hover", "cta-fg"],
    ["disabled", "cta-disabled", "cta-disabled-fg"],
  ])("pass WCAG AA (4.5:1) for the %s state", (_, bg, fg) => {
    expect(contrast(token(bg), token(fg))).toBeGreaterThanOrEqual(4.5)
  })

  it("would fail AA with white text, so the text is never white", () => {
    expect(contrast(token("cta"), "#ffffff")).toBeLessThan(4.5)
  })

  it("are not overridden by the dark theme, so the ratios hold there too", () => {
    const dark = rule(':root[data-theme="dark"]').style
    const names = Array.from({ length: dark.length }, (_, i) => dark[i])
    expect(names.length).toBeGreaterThan(0)
    expect(names.filter((n) => n.startsWith("--rx-cta"))).toEqual([])
  })
})
