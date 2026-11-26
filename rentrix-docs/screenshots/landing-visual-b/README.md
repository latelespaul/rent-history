# Landing visual redesign, PR B: trust ladder + city tiles

Captain feedback 2026-11-26 and brief §3.5 / §3.7. Before (PR A) and after, signed out. Taken with a
headless Chromium (Playwright, throwaway profile) against the local dev stack with the demo seed
(7 live cities); the round icon at the bottom right is the Vite dev server's overlay, not part of
the app. Full pages: [`../landing-v2/`](../landing-v2/).

| File | Page |
|---|---|
| `before-390-trust.png`, `after-390-trust.png` | `#trust` at 390 px (phone, 2x) |
| `before-390-coverage.png`, `after-390-coverage.png` | `#coverage` at 390 px |
| `before-1440-*.png`, `after-1440-*.png` | the same at 1440 x 900 |

What changed:
- Trust: the dark teal band keeps its colour; the lead paragraph, four text cards and footnote
  become a three-stage ladder of the app's own badges, a caption each: "Not verified yet" (Post
  at once) → any one of Phone / Aadhaar / LinkedIn and "ID verified" (Verify once) → "Verified by
  Doorway Living" (Checked by our team); then one line, "Others never see your numbers." Top to
  bottom on a phone, left to right from 1024 px. On the band the green tick sits on white, because
  success-600 on its success-100 tint is 4.23:1 (below AA).
- Coverage: "Live in N cities.", the count from `GET /cities`; one tile per city (name, a "Live"
  dot, a 4 px teal or marigold top strip) linking to `/search?city=`. Two columns on a phone, four
  from 1024 px. The six locality chips per city and their `GET /areas` calls are gone from `/`.

| Measure (`document.body.innerText` on `/`) | Before (PR A) | After |
|---|---|---|
| Words at 390 px | 838 | 620 |
| Words at 1440 px | 896 | 678 |
| Words in `#trust` + `#coverage` | 151 + 130 | 38 + 25 |
| Page height at 390 px | 9,581 px | 6,831 px |
| `#trust` / `#coverage` height at 390 px | 1,238 / 3,052 px | 890 / 650 px |

## Run it locally

See [`../landing-v2/README.md`](../landing-v2/README.md#run-it-locally): API on :8080 (dev profile,
SQLite, demo seed), Vite on :5173; open http://localhost:5173/ signed out at 390 px and 1440 px.
