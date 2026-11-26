/// <reference types="node" />
import { readFileSync, readdirSync, statSync } from "node:fs"
import path from "node:path"
import { describe, expect, it } from "vitest"
import { buttonVariants } from "./button"

// The marigold accent (DESIGN.md §4.1): one accent, one primary action per screen.
const src = path.resolve(import.meta.dirname, "../..")
const tokens = readFileSync(path.join(src, "styles/tokens.css"), "utf8")

function token(name: string): string {
  const match = tokens.match(new RegExp(`--rx-${name}:\\s*(#[0-9a-f]{6})`, "i"))
  if (!match) throw new Error(`--rx-${name} is not a hex colour in tokens.css`)
  return match[1]
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

describe("accent button", () => {
  it("is marigold #F4B23E with near-black text", () => {
    expect(token("cta").toLowerCase()).toBe("#f4b23e")
    expect(token("cta-fg").toLowerCase()).toBe("#142229")
  })

  // The cta tokens are fixed hex values that the dark theme does not override, so these
  // ratios hold in light and dark mode alike.
  it.each([
    ["rest", "cta", "cta-fg"],
    ["hover", "cta-hover", "cta-fg"],
    ["disabled", "cta-disabled", "cta-disabled-fg"],
  ])("passes WCAG AA (4.5:1) for its %s state", (_, bg, fg) => {
    expect(contrast(token(bg), token(fg))).toBeGreaterThanOrEqual(4.5)
  })

  it("would fail AA with white text, so the text is never white", () => {
    expect(contrast(token("cta"), "#ffffff")).toBeLessThan(4.5)
  })

  it("keeps the cta tokens out of the dark overrides", () => {
    const dark = tokens.slice(tokens.indexOf('[data-theme="dark"]'))
    expect(dark).not.toMatch(/--rx-cta/)
  })

  it("has a visible focus ring and an AA disabled state instead of fading", () => {
    const classes = buttonVariants({ variant: "accent" })
    expect(classes).toContain("focus-visible:outline-solid")
    expect(classes).toContain("focus-visible:outline-ink-900")
    expect(classes).toContain("disabled:opacity-100")
    expect(classes).toContain("bg-cta")
    expect(classes).toContain("text-cta-foreground")
  })

  it('is used only on the three primary actions (variant="accent")', () => {
    const files: string[] = []
    const walk = (dir: string) => {
      for (const name of readdirSync(dir)) {
        const full = path.join(dir, name)
        if (statSync(full).isDirectory()) walk(full)
        else if (/\.tsx$/.test(name) && !/\.test\.tsx$/.test(name)) files.push(full)
      }
    }
    walk(src)
    const uses = files
      .flatMap((file) => {
        const count = readFileSync(file, "utf8").match(/variant="accent"/g)?.length ?? 0
        return count ? [`${path.relative(src, file)} × ${count}`] : []
      })
      .sort()
    expect(uses).toEqual([
      // the WhatsApp button, with its disabled twin while the link loads
      "features/invite/InviteSheet.tsx × 2",
      // the hero's "Sign in / Sign up with Google"
      "pages/HomePage.tsx × 1",
      // "Submit review"
      "pages/WriteReviewPage.tsx × 1",
    ])
  })
})
