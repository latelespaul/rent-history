# Landing visual redesign, PR A: open hero, one-line steps, side cards, spacing

Captain feedback 2026-11-26. Before (master at #95) and after, signed out. Taken with a headless
Chromium (Playwright, throwaway profile) against the local dev stack with the demo seed; the round
icon at the bottom right is the Vite dev server's overlay, not part of the app. Full pages:
[`../landing-v2/`](../landing-v2/).

| File | Page |
|---|---|
| `before-390-first-screen.png`, `after-390-first-screen.png` | first screen of `/` at 390 x 844 (phone, 2x) |
| `before-390-how.png`, `after-390-how.png` | `#how` at 390 px |
| `before-390-audience.png`, `after-390-audience.png` | `#audience` at 390 px |
| `before-1440-*.png`, `after-1440-*.png` | the same at 1440 x 900 |

What changed:
- Hero: "Live in" names only the first two cities from `GET /cities`, then "+N more", on one line
  (it was a sideways-scrolling row of every city).
- How it works: each step is an icon with its number and a label of 3-5 words ("Sign in with
  Google", "Verify once", "Rate your rental"); the chip and star rows are gone.
- For tenants / for landlords: three one-line icon rows per card (was a 2x2 grid of four).
- More space: sections 80 px apart on a phone and 128 px from 1024 px (were 64 / 104), more room
  in the hero and between steps. The problem section is unchanged.

| Measure (`document.body.innerText` on `/`) | Before | After |
|---|---|---|
| Words at 390 px | 858 | 838 |
| Words at 1440 px | 919 | 896 |
| Words in hero + `#how` + `#audience` at 390 px | 106 + 41 + 30 | 103 + 27 + 27 |
| Page height at 390 px | 9,486 px | 9,581 px (more spacing; PR B cuts trust and coverage) |

## Run it locally

See [`../landing-v2/README.md`](../landing-v2/README.md#run-it-locally): API on :8080 (dev profile,
SQLite, demo seed), Vite on :5173; open http://localhost:5173/ signed out at 390 px and 1440 px.
