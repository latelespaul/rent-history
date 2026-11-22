# W3 · Sign-in + role pick

| File | Shows |
|---|---|
| `login-dev-picker-desktop.png`, `login-dev-picker-mobile.png` | `/login` with no `GOOGLE_CLIENT_ID`: "Google sign-in is not set up" notice and the purple DEV ONLY seed-user picker (city tabs from `GET /cities`; staff, landlords, tenants, not onboarded) |
| `login-google-desktop.png` | `/login` when `GET /auth/config` returns a client id and no dev login: the official GIS "Sign in with Google" button only |
| `onboarding-empty-desktop.png`, `onboarding-mobile.png` | `/welcome`, first sign-in role pick: tenant / landlord cards, home city picker, display name, terms |
| `onboarding-filled-desktop.png` | Same with Landlord, a home city and the terms ticked |
| `after-onboarding-landlord-dashboard-desktop.png` | Where a new landlord lands (`/me/dashboard`, placeholder until W5); landlord nav in the top bar |
| `settings-landlord-desktop.png` | `/settings`: profile, roles with "Add tenant role", account with Sign out |
| `settings-both-roles-desktop.png` | After "Add tenant role": both roles held, each "Not verified yet" (W8 adds verification) |
| `account-menu-desktop.png` | Top-bar account menu: Settings, Sign out |
| `settings-tenant-mobile.png`, `menu-signed-in-mobile.png` | Guarded `/settings` opened signed out → `/login?next=/settings` → dev login as a tenant → back on settings; mobile menu with Settings and Sign out |
| `admin-no-access-desktop.png` | `/admin` for a tenant: "No access" |
| `home-signed-in-admin-desktop.png` | Home signed in as the demo admin: admin nav and badge |

Taken with headless Chromium (Playwright 1.49.1) against `npm run dev` in `rentrix-web` and the B3 API
(`SPRING_PROFILES_ACTIVE=dev`, SQLite) with hand-inserted demo users (`is_demo`), since the B10 seeder is not
built yet. The Google shot answers `GET /auth/config` with a placeholder client id in the browser.

## Run it locally

```bash
# API (needs Java 21; dev login is on by default in the dev profile)
cd rentrix-server && SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run     # http://localhost:8080/api/v1
# Web
cd rentrix-web && npm ci && npm run dev                                     # http://localhost:5173/login
```

Open http://localhost:5173/login and pick a seed user in the DEV ONLY panel (any `is_demo` user; the B10
seeder adds the 4 + 4 Bangalore/Pune users). For Google, set `GOOGLE_CLIENT_ID` on the API (root README,
"Create a Google OAuth client", authorized JavaScript origin `http://localhost:5173`) and restart it.
