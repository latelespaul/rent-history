# Landing visual redesign, PR 2: how it works + audience

Before (master at #92) and after, signed out, the two sections only. Taken with a headless Chromium
(Playwright, throwaway profile) against the local dev stack with the demo seed; the round icon at the
bottom right is the Vite dev server's overlay, not part of the app. Full pages:
[`../landing-v2/`](../landing-v2/).

| File | Page |
|---|---|
| `before-390-how.png`, `after-390-how.png` | `#how` at 390 x 844 (phone, 2x) |
| `before-390-audience.png`, `after-390-audience.png` | `#audience` at 390 x 844 (phone, 2x) |
| `before-1440-how.png`, `after-1440-how.png` | `#how` at 1440 x 900 |
| `before-1440-audience.png`, `after-1440-audience.png` | `#audience` at 1440 x 900 |

What changed: each step is a title and one row of chips (Tenant / Landlord / Both; "Any one" Phone /
Aadhaar / LinkedIn) or stars, with no body text, then one "no account to search" line. Each side card
is four icon tiles and one link; on a phone an "I rent / I own" toggle shows one card at a time.

| Measure (`document.body.innerText` on `/`) | Before | After |
|---|---|---|
| Words at 390 px | 1,020 | 858 |
| Words at 1440 px | 1,067 | 919 |
| Words in `#how` + `#audience` at 390 px | 84 + 149 | 41 + 30 |
| Page height at 390 px | 10,055 px | 9,486 px |
| `#audience` height at 390 px | 1,357 px | 777 px |

Later PRs (trust, coverage, preview, FAQ) cut the rest toward ~380 words.

## Run it locally

See [`../landing-v2/README.md`](../landing-v2/README.md#run-it-locally): API on :8080 (dev profile,
SQLite, demo seed), Vite on :5173; open http://localhost:5173/ signed out, tap "I own" at 390 px.
