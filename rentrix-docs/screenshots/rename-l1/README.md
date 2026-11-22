# Rename layer 1 screenshots: Doorway Living

Taken with a headless Chrome (throwaway profile) against the local dev stack with the demo seed.
The round icon at the bottom right is the Vite dev server's overlay, not part of the app.

| File | Page | Signed in as |
|---|---|---|
| `landing-desktop.png` | `/`: the minimal landing (brand, one line, "Sign in / Sign up", Terms, Privacy) | nobody |
| `landing-phone.png` | `/` at 390 px wide | nobody |
| `sign-in-desktop.png` | `/login`: "Sign in to Doorway Living", with the dev login panel | nobody |
| `app-header-signed-in-desktop.png` | `/search`, where a signed-in user lands: the app header with the new name | Demo Tenant BLR-1 |

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

Open http://localhost:5173/ , press "Sign in / Sign up", pick a demo seed user in the DEV ONLY panel.
