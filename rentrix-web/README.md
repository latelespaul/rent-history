# rentrix-web

Static React SPA for Doorway Living (React 19, Vite, TypeScript, TanStack Query, React Router, shadcn/ui).
It talks to the API only through one configurable base URL, and `npm run build` makes plain
files in `dist/` that any static host or CDN can serve (SYSTEM_DESIGN.md D-04).

## Run locally

```bash
npm ci                 # .npmrc sets legacy-peer-deps (openapi-typescript still pins TypeScript 5 as a peer)
npm run dev            # http://localhost:5173, proxies /api to API_PROXY_TARGET (default http://localhost:8080)
```

Start the API first: `SPRING_PROFILES_ACTIVE=dev SEED_DEMO=true ./mvnw spring-boot:run` in `rentrix-server`
(`SEED_DEMO=true` adds the demo users and homes on an empty database).

| Script | Does |
|---|---|
| `npm run lint` | ESLint |
| `npm test` | Vitest (`src/**/*.test.{ts,tsx}`; component tests run in jsdom) |
| `npm run build` | Type-check and build `dist/` |
| `npm run gen:api` | Regenerate `src/types/api.gen.ts` from `openapi/openapi.json` |
| `npm run gen:api:fetch` | Refresh `openapi/openapi.json` from a running dev API, then regenerate (`OPENAPI_URL` overrides the source) |

## Sign-in

`/login` asks the API what it offers (`GET /api/v1/auth/config`), so the web has no Google setting
of its own and nothing to commit:

- **Google** (`googleClientId` set): the page loads Google Identity Services and renders the official
  "Sign in with Google" button. The ID token goes to `POST /auth/google`; the API verifies it and
  returns its own access token. Set `GOOGLE_CLIENT_ID` on the API (root README, "Create a Google OAuth
  client"; authorized JavaScript origin `http://localhost:5173` for dev). Without it the button is hidden.
- **Dev login** (`devLogin: true`, API in the `dev` profile with `DEV_LOGIN_ENABLED=true`): a purple
  "DEV ONLY" picker lists the demo seed users (`GET /auth/dev-users`, seeded by `SEED_DEMO=true` on an empty
  database); pick one and sign in, no Google needed.
- A first sign-in goes to `/welcome` to pick tenant, landlord or both (+ optional home city), which posts
  `/me/onboarding` and swaps in the fresh token. `/settings` edits the profile, adds the other role and
  signs out. `/search`, `/settings`, `/me/*` and `/welcome` need a session (`/` is the logged-out landing; a signed-in visitor goes on to `/search`); `/admin/*` needs `ADMIN`.
- Hosting: pages that show the Google button must be served with
  `Cross-Origin-Opener-Policy: same-origin-allow-popups` (set in `vite.config.ts`, `docker/nginx.conf`
  and `vercel.json`). A Content Security Policy, if you add one, must allow `https://accounts.google.com/gsi/`
  for `script-src`, `frame-src`, `style-src` and `connect-src`.

Code: `src/features/auth` (provider, guards, GIS loader, dev picker), `src/pages/LoginPage.tsx`,
`OnboardingPage.tsx`, `SettingsPage.tsx`.

## Design system

The look comes from `rentrix-docs/design` (DESIGN.md): tokens, icons, mockups.

- `src/styles/tokens.css` is a copy of `rentrix-docs/design/tokens.css`; `src/index.css` maps the
  `--rx-*` tokens onto the shadcn names and exposes them as Tailwind colours (`bg-teal-50`, `text-ink-500`,
  `bg-tenant-100`, …). Components use tokens only, never raw colours.
- Icons: `<Icon name="shield-check" />` or the named components (`ShieldCheckIcon`, …) from
  `src/components/ui/icons.gen.ts`, generated from `rentrix-docs/design/icons/icons.js`. No third-party icon set.
- After changing the design tokens or icons, run `npm run sync:design`; `src/test/design-sync.test.ts` fails
  while the copies differ.
- Shell: `src/components/layout` (top bar with the city picker, footer, `Container`);
  `src/components/ui` has `Badge` (+ `DemoBadge`, `VerifiedBadge`), `Notice`, `EmptyState`, `ErrorState`.

## Pointing a build at an API

The base URL is resolved per request, first match wins:

1. `window.__RENTRIX_CONFIG__.apiBaseUrl` from `public/config.js` (`dist/config.js` after a build).
   Replace that one file on the host to point the same build at another API, no rebuild.
   Serve it with `Cache-Control: no-cache`.
2. `VITE_API_BASE_URL` at build time (see `.env.example`).
3. `/api/v1` on the same origin.

The refresh token is an `HttpOnly` cookie, so the web and the API must be same-site
(production: `doorwayliving.in` and `api.doorwayliving.in`), and the API's `CORS_ALLOWED_ORIGINS` must list the web origin.

## API client

`src/api/client.ts` is the only code that calls `fetch`:

- The access token is kept in memory only (D-62). `api.setAccessToken()` stores it after sign-in;
  `api.restoreSession()` trades the refresh cookie for a new one on page load.
- On a `401` it calls `POST /auth/refresh` once (shared by concurrent calls) and retries once.
  Only a `401`/`403` from refresh ends the session (clears the token, calls `onSessionExpired`);
  a `5xx`, `429` or network failure keeps it, and the caller gets the original error.
- Every error rejects with `ApiError` (`status`, stable `code`, `fieldErrors`, `traceId`) mapped from
  Problem Details (D-60). Network failures are `status 0`, `code "network_error"`.

## Types

`src/types/api.gen.ts` is generated by `openapi-typescript` (D-61); do not edit it.
`src/types/api.ts` re-exports generated types and holds hand-written ones for endpoints the
backend does not serve yet (TODO: replace them after B2+ by running `npm run gen:api:fetch`).
