# Landing page screenshots: what Doorway Living is, before sign-in

Taken with a headless Chrome (throwaway profile) against the local dev stack with the demo seed,
signed out. The round icon at the bottom right is the Vite dev server's overlay, not part of the app.

| File | Page |
|---|---|
| `landing-390.png` | `/` at 390 px wide (phone), full page |
| `landing-desktop.png` | `/` at 1440 px wide, full page |

The page: hero (name, tagline, "Sign in / Sign up with Google"), ratings from both sides, how it
works in 3 steps, trust and verification, a preview of the top-rated published homes from
`GET /properties` (the seed rows carry "Demo data") with two sample reviews marked "Sample", and
the call to action again. With no published home to read, the preview shows cards marked "Sample".

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
