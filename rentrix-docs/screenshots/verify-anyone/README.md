# Verify with any one: landing trust section

Taken with a headless Chromium (Playwright, throwaway profile) against the local Vite dev server,
signed out. Only the `#trust` section is captured; the sticky header is set to static for the
shot. The round icon at the lower right is the Vite dev server's overlay, not part of the app.

| File | Page |
|---|---|
| `trust-390.png` | `/#trust` at 390 px wide (phone, 2x) |
| `trust-1440.png` | `/#trust` at 1440 px wide |

The lead says "Verify with any one: phone, Aadhaar or LinkedIn" and "Green tick = approved by the
Doorway Living team after an ID check". Three cards tagged "Any one" (Phone OTP, Aadhaar,
LinkedIn) and one card tagged "By our team" (Green tick, "Verified by Doorway Living").

## Run it locally

```sh
# Web (Node 20+): the trust section needs no API
cd rentrix-web
npm ci
npm run dev
```

Open http://localhost:5173/#trust signed out at 390 px and 1440 px wide. For the full page with
seeded homes and cities, also start the API: see `../landing-v2/README.md`.
