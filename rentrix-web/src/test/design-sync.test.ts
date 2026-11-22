/// <reference types="node" />
import { existsSync, readFileSync } from "node:fs"
import { createRequire } from "node:module"
import path from "node:path"
import { describe, expect, it } from "vitest"
import { ICON_PATHS } from "@/components/ui/icon-paths.gen"
import * as icons from "@/components/ui/icons.gen"

// The design folder is the source of truth (DESIGN.md §4, §5); `npm run sync:design` copies it.
// Skipped where only rentrix-web/ is present (e.g. the web image build).
const web = path.resolve(import.meta.dirname, "../..")
const design = path.resolve(web, "../rentrix-docs/design")

describe.skipIf(!existsSync(design))("design system copy", () => {
  it("tokens.css matches rentrix-docs/design/tokens.css", () => {
    const source = readFileSync(path.join(design, "tokens.css"), "utf8")
    const copy = readFileSync(path.join(web, "src/styles/tokens.css"), "utf8")
    expect(copy.slice(copy.indexOf("\n") + 1)).toBe(source)
  })

  it("the icon set matches rentrix-docs/design/icons/icons.js", () => {
    const { ICONS } = createRequire(import.meta.url)(path.join(design, "icons/icons.js"))
    expect(ICON_PATHS).toEqual(ICONS)
    expect(Object.keys(icons)).toHaveLength(Object.keys(ICONS).length)
  })
})
