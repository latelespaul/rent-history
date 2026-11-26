# Landing visual redesign, PR 1: hero + problem

Before (master at #91) and after, signed out, first screen only. Taken with a headless Chromium
(Playwright, throwaway profile) against the local dev stack with the demo seed; the round icon at
the bottom right is the Vite dev server's overlay, not part of the app. Full pages:
[`../landing-v2/`](../landing-v2/).

| File | Page |
|---|---|
| `before-390-fold.png`, `after-390-fold.png` | `/` at 390 x 844 (phone, 2x) |
| `before-1440-fold.png`, `after-1440-fold.png` | `/` at 1440 x 900 |

What changed: live city chips replace the launch line; a shorter headline; the search box first;
three icon badges replace the lead paragraph; one privacy line; on a phone "See how it works" is
gone (the next section follows at once). Below the fold, the problem section is three icon tiles
plus one banner.

| Measure (`document.body.innerText` on `/`) | Before | After |
|---|---|---|
| Words at 390 px | 1,151 | 1,020 |
| Words at 1440 px | 1,194 | 1,067 |
| Page height at 390 px | 11,068 px | 10,055 px |
| Bottom of the Google button at 390 px | 805 px | 591 px |

Later PRs (how it works, audience, trust, coverage, FAQ) cut the rest toward ~380 words.

## Run it locally

See [`../landing-v2/README.md`](../landing-v2/README.md#run-it-locally): API on :8080 (dev profile,
SQLite, demo seed), Vite on :5173; open http://localhost:5173/ signed out.
