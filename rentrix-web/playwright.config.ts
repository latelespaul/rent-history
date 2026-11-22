import { defineConfig, devices } from "@playwright/test"

/**
 * End-to-end tests (`npm run e2e`) against a running, freshly seeded stack: the docker compose
 * e2e stack by default (web :8081, API :8080), or `E2E_BASE_URL` for another one, such as
 * `npm run dev` at http://localhost:5173. See e2e/README.md.
 *
 * Specs change the shared database (tenancies, new accounts), so they run one at a time and
 * never retry: a retry would meet the data its first try left behind.
 */
/** Stretches every wait on a slow machine, e.g. `E2E_SLOW=4` against `npm run dev` on a busy laptop. */
const slow = Number(process.env.E2E_SLOW ?? 1) || 1

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env.CI,
  timeout: 60_000 * slow,
  expect: { timeout: 10_000 * slow },
  reporter: process.env.CI
    ? [["list"], ["html", { open: "never" }], ["github"]]
    : [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:8081",
    screenshot: "on",
    trace: "retain-on-failure",
    video: "off",
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    {
      // The 390 px phone layout (DESIGN.md mobile width), in the same bundled Chromium.
      name: "mobile",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 390, height: 844 },
        deviceScaleFactor: 3,
        isMobile: true,
        hasTouch: true,
      },
    },
  ],
})
