# Landing v2 screenshots: the redesigned signed-out landing

Taken with a headless Chromium (Playwright, throwaway profile) against the local dev stack with
the demo seed, signed out, after the city localities loaded. The round icon at the right of the
hero is the Vite dev server's overlay, not part of the app. Design: `rentrix-docs/design/landing-v2/`.

| File | Page |
|---|---|
| `landing-390.png` | `/` at 390 px wide (phone, 2x), full page |
| `landing-1440.png` | `/` at 1440 px wide, full page |

Top to bottom: sticky header with section links and "Sign in"; hero (headline, subline, "Sign in /
Sign up with Google", "See how it works", a sample app screen marked "Sample screen"); the problem;
how it works in 3 steps; for tenants and for landlords; trust and verification (phone OTP required,
green tick "Verified by Doorway Living", optional Aadhaar/PAN and LinkedIn badges); a swipeable row
of the top-rated published homes from `GET /properties` (seed rows carry "Demo data", no rent) between
example reviews marked "Demo data" and "Not verified yet" (the seeded reviews are not verified
yet, so none has the green tick); the live cities from `GET /cities` with localities from
`GET /areas`; FAQ; the call to action again; footer.

## Run it locally

```sh
# API (Java 21): SQLite file, demo seed, dev login, port 8080
cd rentrix-server
SPRING_PROFILES_ACTIVE=dev SEED_DEMO=true DEV_LOGIN_ENABLED=true ./mvnw spring-boot:run

# Web (Node 20+), second terminal: Vite on port 5173 proxies /api to localhost:8080
cd rentrix-web
npm ci
npm run dev
```

Open http://localhost:5173/ signed out and screenshot `/` at 390 px and 1440 px wide.
