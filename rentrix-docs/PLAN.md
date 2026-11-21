# Rentrix v1 — Implementation plan (PR sequence)

Status: **current**. Date: 2026-11-21.
Source of truth for behaviour: [`architecture/SYSTEM_DESIGN.md`](architecture/SYSTEM_DESIGN.md) (decisions `D-xx`).
This file only orders the work. If a PR must deviate from the design, it updates SYSTEM_DESIGN.md in the same PR and says why.

## Rules for every PR

- Small and reviewable alone; master stays green after each merge (backend tests, web lint/build/test, migrations).
- Branch from latest `master`; one concern per PR; passes no-mistakes before merge.
- Backend tests run on the **real Flyway migrations** (SQLite by default; MySQL/Postgres via Testcontainers in CI from O2 on).
- Google is never called in tests: `FakeGoogleTokenVerifier` (D-75).
- Every UI PR body has: run command(s), URL, test logins (dev-login seed users, D-77), the pages to shoot, and the screenshots. Images are also committed to `rentrix-docs/screenshots/<pr-id>/` (`git add -f`, the root `.gitignore` ignores `rentrix-docs/`).
- Screenshots come from a local headless browser (Playwright) against local dev servers only.
- No invented numbers or testimonials anywhere (D-03); cities are Bangalore and Pune only (D-20).

## Tracks

- **B** backend (`rentrix-server`), **W** web (`rentrix-web`), **O** ops/CI, **S** screenshots.
- Running in parallel already: **B1 backend core** and **UX design** (`rentrix-docs/design/`). Every W PR from W2 on depends on the UX design PR.

## PR table

| ID | Title | Goal | Main files / modules | Tests added | Depends on | Parallel with | Screenshots |
|---|---|---|---|---|---|---|---|
| B1 | Backend core (in progress) | New server skeleton. Fail-closed profiles, no default profile (D-64). Env config (§17). SQLite default, MySQL/Postgres via `DB_URL` (D-09–D-11). Full portable Flyway `V1` schema with indexes (city search, aggregates, unique review per direction). Entities + repositories for all §5 tables. `/actuator/health` plus liveness and readiness probes (`/actuator/health/liveness`, `/actuator/health/readiness`, DB in the readiness group). Old flats/reviews/auth server code, `dataSeed/`, loose SQL deleted | `rentrix-server/**`, `db/migration/V1__*.sql`, `db/migration/vendor/*`, `application*.yaml` | Context starts per profile; refuses start without profile / with weak `JWT_SECRET` outside dev/test; migrations apply + `ddl-auto=validate` on SQLite; repository smoke tests; health, liveness and readiness 200, readiness 503 when the DB is down | — | UX design | — |
| B2 | Common API layer | Problem Details errors with stable `code` (D-60), pagination envelope + sort allow-list (D-59), validation, `public_id` UUIDv7 (D-12), structured JSON logs with `traceId`, `Idempotency-Key` store, springdoc only in dev/test, `scheduled_job_lock` helper (D-08) | `common/**` | Error shape per status; page/size bounds; sort allow-list rejects unknown field; idempotent replay; OpenAPI hidden in prod | B1 | W1, O1 | — |
| B3 | Auth: JWT + Google + dev login | `GoogleTokenVerifier` + `JwksGoogleTokenVerifier` + `FakeGoogleTokenVerifier` (D-70, D-75). `POST /auth/google`, `/auth/refresh` (rotation + family revoke, D-63), `/auth/logout`, `/auth/config` (D-76). Dev login `GET /auth/dev-users`, `POST /auth/dev-login` dev/test only, refuse start if enabled elsewhere (D-77). Stateless JWT filter (D-62). `ADMIN_EMAILS` (D-78). `/me`, `/me/onboarding`, role add returns new token (D-73). Rate limit on auth (Bucket4j). README: Google OAuth client steps (D-79) | `user/**`, `auth/**`, `security/**`, `README.md` | New / returning user, unverified email, wrong audience, email conflict 409; refresh rotation + reuse revokes family; `onboarding_required` 403; dev-login 404 in prod profile; start refused with `DEV_LOGIN_ENABLED=true` + prod; 429 | B2 | B4 (after B2 merge, coordinate shared `security` config) | — |
| B4 | Cities, properties, search, stats | `GET /cities`, `/stats` (real counts). Property CRUD for `LANDLORD` (DRAFT → PUBLISHED → ARCHIVED, D-21). `GET /properties` search by live city / locality / type / bedrooms / minRating / `q`, sorted with indexed queries (bayes, D-53). Public vs logged-in fields (D-56) | `city/**`, `property/**` | Search filters + pagination; non-live city 422; owner-only edit 403; anonymous sees no `unit_label`; query uses index (EXPLAIN on SQLite) | B3 | — | — |
| B5 | Tenancies + rent records | Tenancy lifecycle §6: create (either side), confirm / decline / cancel / end, 14-day expiry job, guards D-35. Invites via `invite_tokens` + `EmailSender` (`LogEmailSender`, D-65). Rent records + confirm/dispute (D-25), `GET /properties/{id}/rent-history` (D-26), `/me/tenancies` | `tenancy/**`, `notification/**` | Each transition + illegal transition 409; overlap 409; pending cap 5; invite binds only to matching verified email; rent history shows confirmed only; month-only dates for others (D-57) | B4 | — | — |
| B6 | Reviews + aggregates | `POST /tenancies/{id}/reviews` with trust rules D-37–D-45, content checks D-43, double-blind reveal job D-46, property reviews publish now D-47. Edit window, update-once, withdraw. Response (one per review). Stored `rating_aggregates` updated in the same transaction (D-33), threshold n<3 (D-52), bayes nightly (D-53). `/users/{id}`, `/users/{id}/reviews`, `/me/reviews/*`, `/me/review-tasks` with privacy D-54/D-55 | `review/**`, `rating/**`, `user/profile/**` | Wrong direction 403; duplicate 409; window closed 422; reveal on counter-review and after 14 d; repeat-pair `counts_in_aggregate` (D-44); aggregate math worked example (§9); tenant profile 404 to strangers | B5 | B7 | — |
| B7 | Enquiries + share links | `POST /properties/{id}/enquiries` grants landlord view of tenant profile until expiry (D-31, D-32). `/me/enquiries`, `/me/share-links`, `GET /share/{token}` | `enquiry/**`, `sharelink/**` | Grant opens/expires tenant profile; caps 10 open / 20 per 7 d; revoked link 404 | B5 | B6 | — |
| B8 | Reports + moderation | `POST /reports` (D-49, D-50 incl. `PERSONAL_DATA` auto-hide text). Admin endpoints: reports queue + resolve, hide/restore review, void tenancy (D-36), suspend/reinstate user, document accept/reject, audit log (`moderation_actions`, D-51). Account deletion `DELETE /me` (D-58) | `report/**`, `admin/**` | Report never hides (except D-50 case); void removes reviews + rent from aggregates/history; suspend blocks sign-in + refresh; non-admin 403; deletion keeps written reviews as "Former Rentrix user" | B6, B7 | B9 | — |
| B9 | Image + document storage | `ImageStorage` interface, `local` impl + `/media/**` + HMAC signed URLs, `s3` impl (MinIO-compatible) (D-66). Property images upload ≤10, sniff type, WebP re-encode + thumbnail, EXIF strip (D-67). Tenancy documents private (D-30) | `media/**`, `property/image/**`, `tenancy/document/**` | Bad content type 422; EXIF gone; keys random; signed URL expiry; S3 impl against MinIO Testcontainer | B4 (B5 for documents) | B6, B7, B8 | — |
| B10 | Dev seed 4 BLR + 4 PNQ | Seeder per §16 (D-68, D-69): dev profile + `SEED_DEMO=true` + empty DB only; 8 properties, 4 landlords, 12 tenants, demo admin; rent histories; reviews in all 3 directions, spread 1–5, one response, one `AWAITING_REVEAL`, one open report. Not in prod migrations | `seed/**` (dev-only beans) | Refuses under prod / non-empty DB; counts per city = 4; every property has 3-direction published reviews; all emails `@example.test`, no phones; aggregates match recomputation | B6, B7, B8, B9 | — | — |
| W1 | Web reset + API client | Delete mock layer (`VITE_ENABLE_MOCKS`, `src/api/mock`), flats pages, signup/login forms, fake HomePage stats/testimonials (D-03). Configurable `VITE_API_BASE_URL` (D-04), Vite proxy `/api`, in-memory token + refresh flow (D-62), generated types from OpenAPI (D-61, `openapi-typescript`), Vitest setup | `rentrix-web/src/api/**`, `src/types/**`, `src/pages/*` (removed), `vite.config.ts` | Vitest: client attaches token, refreshes once on 401, maps Problem Details; lint + build | B1 (types regenerated after B2+) | B2, UX design | none (placeholder screen) |
| W2 | Design system + shell | Tokens, new icon set, layout, header/footer, city switcher (BLR/PNQ only), "Demo data" badge, error/empty states — per UX design | `src/components/ui/**`, `src/components/layout/**`, `index.css` | Component tests (city switcher only lists API cities) | W1, UX design | B3–B5 | Shell desktop + mobile, empty/error state |
| W3 | Sign-in + onboarding | Login page: GIS button when `googleClientId` set (D-76), dev-login picker in dev (D-77); onboarding role pick (D-73); add role in settings; logout. COOP header + CSP notes | `src/features/auth/**`, `src/pages/LoginPage.tsx`, `OnboardingPage.tsx`, `SettingsPage.tsx` | Google button hidden without client id; onboarding swaps token; guarded routes | W2, B3 | W4 | Login (dev picker), onboarding, settings |
| W4 | Home, search, property page | Home with real `/stats` only, search hint Bangalore/Pune localities; search results with filters + pagination; property page (anonymous vs logged-in, D-56), rent history chart/table, property rating + reviews | `src/pages/HomePage.tsx`, `SearchPage.tsx`, `PropertyPage.tsx`, `src/features/property/**` | Home has no hard-coded numbers; filters → query params; anonymous view hides review text | W2, B4 (B5/B6 for history/reviews data) | W3 | Home, search BLR, search PNQ, property (anon + logged-in) |
| W5 | Landlord: properties + tenancies | My properties, create/edit/publish/archive, image upload; invite tenant; confirm/decline tenancy requests; rent records | `src/features/landlord/**`, `src/features/tenancy/**` | Form validation mirrors API errors; state buttons per status | W3, B5, B9 | W6 | My properties, property form, tenancy request, rent records |
| W6 | Reviews, profiles, enquiries | Review tasks + write/edit/withdraw (per direction, sub-ratings D-29), awaiting-reveal state, response; landlord profile; tenant profile via enquiry/share link; enquiry send + landlord inbox; share links | `src/features/review/**`, `src/features/profile/**`, `src/features/enquiry/**` | Direction options per side; n<3 shows "New" not stars (D-52); 404 profile renders not-found | W3, B6, B7 | W5, W7 | Review form, review tasks, landlord profile, tenant profile (landlord view), enquiries |
| W7 | Reports + admin | Report dialog on review/user/property/tenancy; admin reports queue, resolve, hide/restore, void, suspend, audit; account deletion in settings | `src/features/report/**`, `src/pages/AdminPage.tsx` | Admin routes hidden for non-admin; resolve flows | W3, B8 | W6 | Report dialog, admin queue, audit log |
| O1 | Per-tier Docker + compose | `Dockerfile` for API (JRE 21, non-root, health check on `/actuator/health/readiness`) and web (static build behind nginx, `VITE_API_BASE_URL` at build), `docker-compose.yml` (api + web + SQLite volume; optional `postgres`/`minio` profiles), `.env.example` without secrets | `rentrix-server/Dockerfile`, `rentrix-web/Dockerfile`, `docker-compose.yml` | CI builds both images; compose smoke: readiness 200 | B1, W1 | B2+ | — |
| O2 | CI: both tiers + all DBs | GitHub Actions: backend tests on SQLite + Testcontainers MySQL 8 and Postgres 15 matrix, web lint/test/build, OpenAPI type drift check (D-61) | `.github/workflows/ci.yml` | The matrix itself | B2, W1 | B3+ | — |
| O3 | README + `.no-mistakes.yaml` + doc cleanup | Root README: one command per tier (`SPRING_PROFILES_ACTIVE=dev SEED_DEMO=true ./mvnw spring-boot:run`, `npm run dev`, `docker compose up`), env table pointer, Google setup (from B3). `.no-mistakes.yaml` with backend + web test commands. Delete superseded docs (§20), `rentrix-tests/` Postman, `configuration/*`, `SETUP.md` when replaced; architecture doc "path to scale" kept in SYSTEM_DESIGN §18 | `README.md`, `.no-mistakes.yaml`, `rentrix-docs/**`, `rentrix-tests/` (removed) | `no-mistakes` runs the commands green | O1, O2, B10 | W5–W7 | — |
| S1 | Full screenshot set | Run the dev stack with seed, shoot every screen (anon, tenant, landlord, admin; BLR + PNQ; desktop + mobile) into `rentrix-docs/screenshots/v1/` with an index page; fix any visual gaps found as follow-up issues | `rentrix-docs/screenshots/**`, `scripts/screenshots.ts` (Playwright) | Script runs headless and exits; every listed page captured | W4–W7, B10 | — | All pages above |

## Order and parallelism

```
B1 ─▶ B2 ─▶ B3 ─▶ B4 ─▶ B5 ─┬─▶ B6 ─┐
                  │         └─▶ B7 ─┴─▶ B8 ─┐
                  └──────▶ B9 ──────────────┴─▶ B10 ─┐
UX design ─┐                                         │
B1 ─▶ W1 ─▶ W2 ─▶ W3 ─┬─▶ W4                         ├─▶ O3 ─▶ S1
                      ├─▶ W5 (needs B5, B9)          │
                      ├─▶ W6 (needs B6, B7)          │
                      └─▶ W7 (needs B8)              │
B1+W1 ─▶ O1 ;  B2+W1 ─▶ O2 ──────────────────────────┘
```

- Backend is one critical path B1→B6. B6/B7, then B8/B9, can run in parallel.
- W1 can start as soon as B1 is merged; O1 after B1 + W1; O2 after B2 + W1. W2 starts when the UX design PR is merged.
- W PRs can be built against a dev server while their backend PR is open, but merge only after it (master stays green).
- Seed (B10) lands before the UI PRs need rich data for screenshots; until then UI PRs screenshot with data created through the dev API. If B10 is late, W4–W7 may merge with interim screenshots and S1 re-shoots.

## Notes

- **Google sign-in** is B3 (API, README steps) + W3 (UI). Dev login keeps local runs, tests and screenshots Google-free.
- **Fake claims**: removed in W1 (HomePage stats, testimonials, "18 cities"); W4 rebuilds Home on `/stats` only.
- **Migrations**: B1 owns `V1`. Later backend PRs that need schema changes add `V2+` migrations; none edit `V1` after B1 merges.
- **Seed** is dev-only code (B10), never a Flyway migration.
- **Screenshot run recipe** (every UI PR): `SPRING_PROFILES_ACTIVE=dev SEED_DEMO=true ./mvnw spring-boot:run` in `rentrix-server`, `npm run dev` in `rentrix-web`, open `http://localhost:5173`, sign in via the dev-login picker (e.g. "Demo Landlord BLR-1", "Demo Tenant PNQ-3", `demo-admin@example.test`). Stop both servers and the headless browser afterwards.
