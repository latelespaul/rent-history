/// <reference types="node" />
import { readdirSync, readFileSync, statSync } from "node:fs"
import path from "node:path"
import { describe, expect, it } from "vitest"
import { LAUNCH_CITIES } from "@/test/cities"

const SRC = path.resolve(import.meta.dirname, "../..")

/** App source files: everything under `src/` except tests and the `src/test/` fixtures. */
function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const full = path.join(dir, entry)
    if (statSync(full).isDirectory())
      return full === path.join(SRC, "test") ? [] : sourceFiles(full)
    return /\.tsx?$/.test(entry) && !/\.test\.tsx?$/.test(entry) ? [full] : []
  })
}

describe("no city literal in src/ (D-20, D-85)", () => {
  it("names no launch city, alias or code outside tests", () => {
    const words = LAUNCH_CITIES.flatMap((c) => [c.code, c.name, ...c.aliases])
    const pattern = new RegExp(`\\b(${words.join("|")})\\b`, "i")
    const offenders = sourceFiles(SRC)
      .filter((file) => !file.endsWith("api.gen.ts"))
      .flatMap((file) =>
        readFileSync(file, "utf8")
          .split("\n")
          .map((line, i) => ({ file: path.relative(SRC, file), line: i + 1, text: line }))
          .filter(({ text }) => pattern.test(text)),
      )
    expect(offenders).toEqual([])
  })
})
