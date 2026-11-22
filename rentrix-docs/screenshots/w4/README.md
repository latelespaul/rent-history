# W4 screenshots: Home, search, property page

Taken with a headless Chromium against the local dev stack with the B4a demo seed
(4 Bangalore + 4 Pune fictional homes). Rent history and reviews show their empty state
until B5 and B6 serve them.

| File | Page |
|---|---|
| `home-desktop.png`, `home-mobile.png` | Home `/`: search entry, city cards with `/stats` counts |
| `search-bangalore-desktop.png` | `/search?city=BLR` |
| `search-bangalore-filtered-desktop.png` | `/search?city=BLR&type=APARTMENT` |
| `search-pune-desktop.png`, `search-pune-signed-in-desktop.png` | `/search?city=PNQ`, anonymous and signed in |
| `property-anonymous-desktop.png`, `property-anonymous-mobile.png` | `/p/:id` anonymous: facts and ratings, rent history and reviews locked (D-56) |
| `property-signed-in-desktop.png`, `property-signed-in-mobile.png` | `/p/:id` signed in as Demo Tenant BLR-1 (dev login) |
| `property-not-found-desktop.png` | `/p/<unknown id>` |

## Run it locally

```sh
# API (Java 21): SQLite file, demo seed, dev login
cd rentrix-server
SPRING_PROFILES_ACTIVE=dev SEED_DEMO=true DEV_LOGIN_ENABLED=true ./mvnw spring-boot:run

# Web (Node 20+), in a second terminal: Vite proxies /api to localhost:8080
cd rentrix-web
npm ci
npm run dev
```

Open http://localhost:5173 (Home), http://localhost:5173/search?city=BLR and
http://localhost:5173/search?city=PNQ, then click a home.

Signed-in view (until the W3 sign-in screen lands): in the browser console on
http://localhost:5173 run

```js
const users = await (await fetch("/api/v1/auth/dev-users")).json()
const tenant = users.find((u) => u.roles.includes("TENANT"))
await fetch("/api/v1/auth/dev-login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ userId: tenant.id }),
})
location.reload()
```

The web restores the session from the refresh cookie; the top bar then shows "Demo data" and
the user's short name. Dev logins are the `@example.test` demo users only (D-77). If the web runs
on another port, start the API with `CORS_ALLOWED_ORIGINS=http://localhost:<port>`.
