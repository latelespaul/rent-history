# Rentrix — System Design (v1, two-sided trust platform)

Status: **current**. This document is the single source of truth for the v1 rebuild.
It supersedes every older architecture, API and database doc in `rentrix-docs/` (see §20).
Date: 2026-11-21. Revised 2026-11-21 (design v2): data-driven cities (§5.2), admin console (§8.1), onboarding with approvals (§7.3), verification documents (§5.13–§5.14). Revised 2026-11-22 (**design v3**): a profile is ID verified with any one of Aadhaar, PAN or phone OTP, LinkedIn is an optional bonus badge (§7.4, D-99–D-106); location hierarchy City → Area → Society → Tower → Wing → Unit (§5.17–§5.19, D-107–D-110); rating roll-ups per society, area and owner and browsing by them (D-111–D-113); migrations and seed for both (D-114, D-115). Revised 2026-11-22 (**design v4**): phone OTP is required for every tenant and landlord, PAN and masked Aadhaar become optional extra ID badges; an "account created" screen after the first sign-in, a "Signed in as" toast and return to the page the user came from; onboarding continues straight into the verification stepper; verification progress in the account menu (§7.5, D-116–D-122); tenancy verification through the rental agreement, checked in our own system (§5.20, §7.6, D-123–D-131). Revised 2026-11-22 (**design v4, open by default**): a user with a verified phone writes trust data at once, shown as "Not verified yet"; admin approval no longer gates writing and instead gives a green tick "Verified by Doorway Living" on users, properties, tenancies and reviews; headline ratings count only green-tick reviews; a mobile-first admin approvals queue; a preview-only SMS sender (§7.7, D-132–D-137). Text it replaces is marked *Changed in design v4 (open by default)*. Revised 2026-11-23 (**admin separation**): the admin console moves to its own host, `ADMIN_EMAILS` is removed and the main site never shows, grants or uses the admin role (§13.3, D-154, D-155). Text it replaces is marked *Changed in admin separation*.

Each decision is recorded as a numbered line `D-xx`. Later work (UX, implementation plan,
code) cites these numbers. To change a decision, edit its line here in the same PR as the change. Numbers are stable IDs, not reading order: decisions added later get the next free number wherever they sit (e.g. D-70–D-79, Google sign-in).

---

## 1. Product summary

Rentrix is a two-sided trust platform for renting in India. It rates three things:

| Direction | Author | Subject |
|---|---|---|
| `TENANT_TO_LANDLORD` | tenant of a tenancy | the landlord of that tenancy |
| `LANDLORD_TO_TENANT` | landlord of a tenancy | the tenant of that tenancy |
| `TENANT_TO_PROPERTY` | tenant of a tenancy | the property of that tenancy |

- A landlord can check a tenant's rating when the tenant enquires about one of the landlord's properties.
- A tenant can check a landlord's rating, a property's rating, and the property's rent history.
- Every review is tied to a **confirmed tenancy** (landlord + tenant + property + dates + rent).
  A stranger cannot rate anyone.
- Every account that writes trust data (tenancies, reviews, listings) verifies a phone number by OTP first; without it the account stays read-only (D-117). Reading needs no verification. *Changed in design v4 (open by default)*: an admin no longer approves accounts before they write; what they post is visible at once as "Not verified yet", and an admin review gives users, properties, tenancies and reviews a green tick **"Verified by Doorway Living"** (§7.7, D-132, D-134).
- PAN or masked Aadhaar is optional and gives the **"ID verified"** badge; LinkedIn is a second optional badge (§7.4, §7.5). *Changed in design v4*: until then any one of Aadhaar, PAN or phone was enough.
- A tenancy can be backed by its rental agreement: both parties confirm the same uploaded file, Rentrix checks it against the tenancy, and an admin checks its stamp or registration number on the government portal. The result is a tier badge, "Agreement verified" or "Agreement matched" (§7.6).
- Every property sits in a location hierarchy: City → Area (PIN code / locality) → gated society → tower/block → wing → unit. Standalone houses skip the society levels. Ratings can be browsed by area and by society, and roll up per property, society, area and owner (§5.19, §9.1).
- Cities: **starting in Bangalore (Bengaluru) and Pune, more cities coming**. Cities are rows in the `cities` table; adding one is data, not code (D-20, D-83).

- **D-01** The repo is a scratch start. The flat-listing + admin-approved-review model is replaced, not migrated. No feature is kept only because it exists today.
- **D-02** v1 is a trust and reputation product, not a listings marketplace. Properties exist so that tenancies, rent history and property ratings have something to attach to.
- **D-03** No invented numbers anywhere in the product. Every stat shown in the UI comes from the live API (`GET /stats`); if a count is zero or small, the UI shows it as is or hides the block. No testimonials until real, consented ones exist.

---

## 2. Current state and what is replaced

What exists on `master` (v0.1.6) and what happens to it:

| Today | v1 |
|---|---|
| `Flat` entity, `flats` table, `/flats` API | Replaced by `Property` (§5.3), `/properties` |
| `FlatImage`, public S3 bucket | Replaced by `PropertyImage` behind the `ImageStorage` interface (§15) |
| `RentHistory` (flat + landlord + tenant + dates, no rent, no confirmation) | Replaced by `Tenancy` (§5.4) + `RentRecord` (§5.5) |
| `Review` (user × flat, rating 1–10, `PENDING` until admin approves) | Replaced by `Review` with `direction`, tied to a tenancy, rating 1–5, post-moderated (§5.6, §7) |
| `ReviewProof` (private documents per review) | Replaced by `TenancyDocument` (optional evidence per tenancy, §5.8) |
| `UserDetail` (name parts, date of birth, address, phone) | Merged into `User`; date of birth and home address are dropped (data minimisation) |
| `Role` enum, one role per user (`TENANT`/`LANDLORD`/`ADMIN`) | Role **set** per user (§4) |
| Flat `verified` / `visible` flags, tenant-created flats | Removed. Only landlords create properties; trust comes from tenancy confirmation |
| `RatingAggregate` projection computed per query | Stored `RatingAggregate` rows (§5.10) |
| MySQL-only Flyway `V1__initial_schema.sql` and loose `db/migration-*.sql` files | Deleted. New portable baseline `V1` for SQLite/MySQL/Postgres (§3.3) |
| `dataSeed/*.sql`, `rentrix-docs/**/INSERT*.txt`, `database/sample-data.md` | Deleted. Replaced by the dev-only seeder (§16) |
| `rentrix-web` mock layer (`VITE_ENABLE_MOCKS`, `src/api/mock`) | Removed. The web talks only to the real API |
| `HomePage.tsx` fake stats ("Now live in 18 cities", "Cities covered: 18", "2,400+ flats", "9,800+ reviews", "6,200+ tenants") and testimonials (incl. "Tenant · Mumbai") | Removed (D-03). City names and counts come only from `GET /cities` and `GET /stats` (D-85) |
| `rentrix-tests/` Postman collections | Obsolete; replaced by API integration tests in `rentrix-server` |
| Email + password signup/login (`AuthController`, `AuthServiceImpl`, bcrypt, `SignupRequest`/`LoginRequest`, web `LoginForm`/`SignupForm`) | Replaced by "Sign in with Google" (§13.1). No passwords are stored in v1 |
| Hard-coded `JWT_SECRET` default in `application-dev.yaml` | Allowed only when the `dev` or `test` profile is explicitly active; any other profile refuses to start without a real `JWT_SECRET` (D-64) |
| `${SPRING_PROFILES_ACTIVE:dev}` default in `application.yaml` | Removed. The profile must be set explicitly; the API refuses to start without one (D-64) |

Kept as-is in spirit: React + Vite + TypeScript + Tailwind/shadcn web stack, Spring Boot 4 / Java 21 API,
Flyway, JWT, context path `/api/v1`, GitHub Actions CI (backend tests + web lint/build).

---

## 3. Architecture: three tiers

```
 Browser ──HTTPS──▶ Static web (React SPA)        any static host / CDN
    │
    └──HTTPS JSON──▶ API (Spring Boot, stateless)  1..N instances
                        │            │
                        ▼            ▼
                    Database      ImageStorage
                 (SQLite default; (local disk default;
                  MySQL/Postgres   S3-compatible via env)
                  via env)
```

### 3.1 Web tier
- **D-04** `rentrix-web` is a static SPA (React 19, Vite, TypeScript, TanStack Query, React Router, shadcn/ui). Build output is plain files; it can be served by any static host or CDN. The only config is the API base URL: `window.__RENTRIX_CONFIG__.apiBaseUrl` from `public/config.js` (a plain file the host may replace at deploy time, served `no-cache`), else `VITE_API_BASE_URL` baked in at build time, else same-origin `/api/v1`. The web Docker image writes `/config.js` at container start from `API_BASE_URL`. *Changed in W1*: the runtime `config.js` override was added so one static build can be promoted across environments without a rebuild.
- **D-05** The web holds no business rules that the API does not also enforce. Permission checks in the UI are cosmetic; the API is the authority.

### 3.2 API tier
- **D-06** `rentrix-server` is a stateless Spring Boot 4 (Java 21) REST API under `/api/v1`. No HTTP session, no in-memory state that must survive between requests. Any instance can serve any request.
- **D-07** Layering: `controller` → `service` → `repository`, DTOs at the edge, entities never serialised directly. Package by feature (`user`, `city`, `property`, `tenancy`, `review`, `enquiry`, `report`, `admin`, `auth`, `media`, plus `security`, `config` and `common`) instead of by layer.
- **D-08** Scheduled work (review reveal, window close, aggregate repair) runs in-process with a DB row lock (`scheduled_job_lock` table, ShedLock-style), so it is safe with several instances and needs no extra service. Jobs call `common.scheduling.JobLock.runLocked(name, lockAtMostFor, lockAtLeastFor, job)`; `SCHEDULING_ENABLED=false` turns the scheduler off (the `test` profile does, and tests call jobs directly).

### 3.3 Data tier
- **D-09** SQLite is the default database: in the `dev` profile `DB_URL` defaults to `jdbc:sqlite:./data/rentrix.db` (the `test` profile uses a fresh file under `target/`). `prod` has no default and refuses to start without `DB_URL` (fail closed, like D-64); a `jdbc:sqlite:` URL is a valid production value for the single-box stage (§18). Setting `DB_URL` (+ `DB_USERNAME`, `DB_PASSWORD`) to a MySQL 8 or PostgreSQL 15+ JDBC URL switches the database with no code change.
- **D-10** One Flyway migration set, written in a portable SQL subset (no `AUTO_INCREMENT`/`ENGINE`, no vendor JSON functions, `CHECK` constraints only where all three support them). Where a statement must differ, it goes in `db/vendor/{vendor}`, outside `db/migration` because Flyway scans each location recursively, and `spring.flyway.locations` lists `classpath:db/migration,classpath:db/vendor/{vendor}` using Flyway's `{vendor}` placeholder (the folder and the second location are added with the first such statement). A column *type* that differs on every table is a Flyway placeholder instead (D-81). JPA runs with `ddl-auto=validate`. Decimal columns are declared `NUMERIC(p,s)` (not `DECIMAL`), because the SQLite driver reports `DECIMAL` as a float type and schema validation would fail.
- **D-11** SQLite runs with `journal_mode=WAL`, `foreign_keys=ON`, `busy_timeout=5000`. SQLite deployments run **one** API instance (single writer). Multiple instances require MySQL or Postgres.
- **D-12** IDs: internal `BIGINT` primary keys; every externally visible row also has a `public_id` (UUIDv7, stored as `CHAR(36)`). The API only ever exposes `public_id`, so IDs cannot be enumerated. Both are generated by the API, not the database (D-80).
- **D-13** Money is stored as whole rupees in `BIGINT` (`monthly_rent_inr`, `deposit_inr`). Currency is INR only in v1.
- **D-14** Timestamps are UTC (`TIMESTAMP`; `DATETIME(6)` on MySQL, D-81), kept at millisecond precision so they round-trip identically on all three databases; SQLite stores them as UTC text `yyyy-MM-dd HH:mm:ss.SSS`; tenancy and rent dates are calendar `DATE` (no time zone). Rent periods are month-granular in the UI.
- **D-15** Soft delete only where history matters (users, properties, reviews); a `deleted_at` column, not a boolean. Tenancies and rent records are never hard-deleted; they change status.
- **D-80** Internal ids are time-ordered 64-bit values generated in the API (`TimeOrderedIds`: 41 bits of milliseconds since 2026-01-01, 10 bits of node id, 12 bits of sequence starting at a random value each millisecond). This keeps the DDL identical on all three databases (no `AUTO_INCREMENT`, `IDENTITY` or sequences), needs no database round trip, and avoids the second write transaction a table-based id generator would need on SQLite's single writer. Several instances set `ID_NODE` (0–1023) each to rule out collisions; without it the node id is random per process. `public_id` is a UUIDv7 generated in the API.
- **D-81** The only vendor difference in the baseline is the timestamp column type: MySQL's `TIMESTAMP` ends in 2038 and is converted through the session time zone, so migrations write `${timestamp_type}`, which the API sets at startup from the connection's database product (`DATETIME(6)` on MySQL, `TIMESTAMP` elsewhere; `MigrationPlaceholders`). Running the migrations with the Flyway CLI needs `-placeholders.timestamp_type=…`.
- **D-98** `V1` is frozen (merged with B1); later schema changes are new migrations, never edits to a merged one. Design v2 needs three. Each takes the next free version when its PR merges (B2 added `V2__idempotency_keys.sql`; C1 took `V3`), so the unmerged ones are named by purpose here:
  - **Cities migration**, `V3__city_pincode_prefixes.sql` (PLAN C1): `ALTER TABLE cities ADD COLUMN pincode_prefixes VARCHAR(64)`; set `56` for `BLR` and `41` for `PNQ`.
  - **Verification-core migration**, `V<n>__verification_core.sql` (PLAN B11): `verification_applications` + `verification_events` (§5.13); `properties` gains `review_reason`, `reviewed_by`, `reviewed_at` (the new status values need no DDL: enum columns have no `CHECK`, per the V1 header); `moderation_actions` is dropped and recreated with nullable `target_id` and a new `target_ref VARCHAR(36) NOT NULL` plus index `(target_type, target_ref, created_at)`. The drop is safe because no released code writes that table before B8, which lands after B11; SQLite cannot relax `NOT NULL` in place, so recreate is the portable path.
  - **Verification-documents migration**, `V<n>__verification_documents.sql` (PLAN B12): `verification_documents` (§5.14) with indexes on `(owner_id, kind)`, `(identity_verification_id)` and `(status, purge_after)`. *Changed in design v3*: the ID number fields moved to `identity_verifications` (D-114).
- **D-114** Design v3 migrations follow the same rule as D-98: new files only, each takes the next free version when its PR merges, no merged migration is edited. Full column lists, keys and indexes are in [`../database/schema.md`](../database/schema.md).
  - **Identity migration**, `V<n>__identity_verifications.sql` (PLAN B14): `identity_verifications` (§5.15) and `phone_otp_challenges` (§5.16). No change to `users` (`phone` already exists in V1).
  - **Location migration**, `V<n>__location_hierarchy.sql` (PLAN B15): `areas`, `area_pincodes`, `societies`, `society_towers`, `society_wings` (§5.17, §5.18); `properties` gains nullable `area_id`, `society_id`, `tower_id`, `wing_id`, each added with an inline `REFERENCES` (`ALTER TABLE … ADD COLUMN`, default null, which SQLite and Postgres accept and enforce), plus indexes `(area_id, status)` and `(society_id, status, tower_id, wing_id)`. MySQL parses but ignores inline column `REFERENCES`, so `db/vendor/mysql/V<n>_1__location_fks.sql` adds the four foreign keys with `ALTER TABLE … ADD CONSTRAINT`; it is the first vendor file, and the B15 PR sets `spring.flyway.locations` to `classpath:db/migration,classpath:db/vendor/{vendor}` (D-10). No backfill: no deployment has property rows before B4, which lands after this migration. `area_id` stays nullable in the DDL (SQLite cannot add a `NOT NULL` column without a default) and the API requires it on every property (D-110).
  - **Not yet merged, so written in their v3 shape** (no extra migration): the verification-core migration (B11) creates `verification_applications` without the LinkedIn columns of design v2 (LinkedIn is an `identity_verifications` row, D-106); the verification-documents migration (B12) creates `verification_documents` with `identity_verification_id` instead of `number_last4`/`number_hmac`.
  - *Design v4*: **Rental-agreement migration**, `V<n>__rental_agreements.sql` (PLAN B18): `rental_agreements` (§5.20) with its indexes. Phone-required (D-117) needs no DDL.
  - *Design v4 (open by default)*: **Verification-state migration**, `V<n>__verification_state.sql` (PLAN B17): nullable `verification_status` on `properties`, and nullable `verification_status`, `verification_decided_by` (FK `users`), `verification_decided_at`, `verification_reason` on `tenancies` and `reviews`, plus indexes `(verification_status, created_at)` on all three for the queue (D-134, D-136). Null reads as `UNVERIFIED`, so no backfill of the new columns. The same PR recomputes every existing `rating_aggregates` row from source once after the migration (the D-33 nightly repair logic, run once at startup under the D-08 lock), so existing unverified reviews leave the headline average at once (D-135).
  - **No migration needed** for the roll-ups (D-111): `rating_aggregates.subject_type` is `VARCHAR(16)` with no `CHECK`, so the new values `SOCIETY`, `AREA`, `OWNER_HOMES` fit.

---

## 4. Roles

- **D-16** A `User` has a role set: any of `TENANT`, `LANDLORD`, plus `ADMIN` for staff. One person can be both tenant and landlord with one account. A user picks at least one of `TENANT`/`LANDLORD` on first sign-in (onboarding, D-73) and can add the other later from settings. Roles are never removed by the user once they are attached to a tenancy.
- **D-17** The role a user plays **in a tenancy** comes from the tenancy (`landlord_id` / `tenant_id`), not from the account. The account role set only gates actions such as "create a property" (needs `LANDLORD`) or "send an enquiry" (needs `TENANT`).
- **D-18** A user has two separate reputations: **as landlord** and **as tenant**. They are aggregated and shown separately and never mixed.
- **D-19** `ADMIN` is never self-assigned and there is no self-service path to it. *Changed in admin separation*: it exists only in an **admin console session on the admin host** (§13.3, D-154). Who may open one comes from config or the database only: (a) verified Google emails listed in `ADMIN_CONSOLE_EMAILS` (env), checked at every admin sign-in and refresh on the admin host; (b) the `user_roles` `ADMIN` row (the DB flag), which an existing admin grants or revokes from the admin console, **only under the `dev` profile** (and `test`, as for dev login, D-77), for the seed's Demo Admin. In `prod` the row grants nothing: `ADMIN_CONSOLE_EMAILS` is the only way into the console, at sign-in, refresh and every admin decision. Nobody can grant or revoke their own `ADMIN`; an `ADMIN_CONSOLE_EMAILS` admin is removed only by editing the env. Every grant and revoke is audited (D-87). The main site never shows, grants or uses `ADMIN`: no admin links or badge, no admin reads or writes, and an admin is a normal tenant or landlord there. The old path where `ADMIN_EMAILS` granted `ADMIN` at sign-in on the main site (D-78) is removed. In the dev seed, `demo-admin@example.test` has the `ADMIN` row and signs in through the admin host's dev login only (D-77, D-97). Admins cannot write reviews in their admin capacity; an admin who is also a party to a tenancy acts as a normal user there (and D-88 applies).

| Role | Can do |
|---|---|
| Anonymous | Browse cities, areas, societies and properties, see property, society and area cards with aggregate rating and count, see public landlord summary (name, aggregate as landlord, badges). Sign up / log in. |
| Logged in, phone not verified | Read everything a logged-in user can read (all review texts, rent history, landlord profiles; D-55, D-56). Verify the phone, fill in and request verification (§7.3). Report content, withdraw own reviews, delete the account. No trust writes (D-117, D-132). |
| `TENANT` (phone verified) | Everything a logged-in user can, plus: send enquiries, add a tenancy period on a property (or start a tenancy request), confirm tenancies, add rent records, review landlord and property of own tenancies. What they post shows "Not verified yet" until an admin gives it the green tick (D-134). |
| `LANDLORD` (phone verified) | Everything a logged-in user can, plus: add and manage own properties (visible at once, D-92), add tenancy periods and invite tenants, confirm tenancy requests, add rent records, review tenants of own tenancies, view profiles of tenants who enquired. |
| `ADMIN` (admin host only, §13.3) | Admin console (§8.1): view, search and filter all records; review users (per role), properties, tenancies, reviews, identity checks (PAN, masked Aadhaar, LinkedIn) and uploaded agreements, and give or refuse the green tick (D-134, D-136); moderation (reports queue, hide/restore content, void tenancies, suspend users); activate cities; grant admin; view the audit log. |

Verification is per role: a user who is both tenant and landlord has one verification for each (D-89). *Changed in design v4 (open by default)*: the rows above used to read "approved" and gated trust writes on it (D-132).

---

## 5. Entities

```
User 1───N Property (landlord_id)     one owner, many properties (D-113)
User 1───N Tenancy (as landlord)      Property 1───N Tenancy
User 1───N Tenancy (as tenant)        Tenancy  1───N RentRecord
Tenancy 1───0..3 Review (one per direction)
Review 1───0..1 ReviewResponse        Review 1───N ReviewRevision
Property 1───N PropertyImage          Tenancy 1───N TenancyDocument
Tenant  1───N Enquiry N───1 Property
Report N───1 (Review | ReviewResponse | User | Property | Tenancy)
User 1───0..2 VerificationApplication (one per role)
User 1───0..5 IdentityVerification (one per method: AADHAAR, PAN, PHONE_OTP, LINKEDIN, TRUECALLER)
User 1───N VerificationDocument N───0..1 VerificationApplication | IdentityVerification | Property (listing proof)
User 1───N PhoneOtpChallenge
User 1───N RentalAgreement N───0..1 Tenancy | VerificationApplication   (design v4, §5.20)
City 1───N Area N───N pincode (AreaPincode)
Area 1───N Society 1───N SocietyTower 1───N SocietyWing
Property N───1 Area, N───0..1 Society, N───0..1 SocietyTower, N───0..1 SocietyWing   (the property is the unit / flat)
RatingAggregate: one row per (subject_type, subject_id); subjects LANDLORD, TENANT, PROPERTY, SOCIETY, AREA, OWNER_HOMES
City: reference table (rows added as data; BLR and PNQ live at launch)
```

Common columns on every table: `id BIGINT PK`, `created_at`, `updated_at`. Tables with `public_id` noted.

### 5.1 User (`users`, has `public_id`)
| Column | Notes |
|---|---|
| `google_sub` | Google OIDC subject id (`sub` claim); the identity key (D-72). Null for seed users and deleted users (D-58), so no `NOT NULL` constraint; unique where not null |
| `email` | from the Google `email` claim; unique, lower-cased, private; refreshed on each sign-in |
| `email_verified_at` | set from Google's `email_verified=true` claim; sign-in is refused without it |
| `google_name` | `name` claim as last received from Google |
| `display_name` | 2–60 chars, initialised from `google_name`, user-editable. Shown publicly as first word + initial of the last name word, skipping code words with a digit, e.g. "Demo Tenant BLR-1" → "Demo T."; the API computes it (`PublicNames.shortName`) and sends it as `user.shortName` |
| `avatar_url` | `picture` claim (Google-hosted URL, not copied to our storage); shown only where the full profile is visible (D-54, D-55) |
| `phone` | optional, E.164, private; never shown publicly; set only by a confirmed phone OTP (D-101), cleared from verification when changed |
| `roles` | separate `user_roles(user_id, role)` table; empty until onboarding completes |
| `onboarded_at` | null until the first-sign-in role pick (D-73) |
| `last_login_at` | |
| `home_city_code` | FK `cities.code`, optional |
| `status` | `ACTIVE`, `SUSPENDED`, `DELETED` |
| `is_demo` | true only for seed data (D-68) |
| `deleted_at` | soft delete; see D-58 for anonymisation |

There is no password column: v1 has no passwords (D-71).

### 5.2 City (`cities`)
| Column | Notes |
|---|---|
| `code` | PK, short upper-case code, e.g. `BLR`, `PNQ` |
| `name` | display name, e.g. "Bangalore", "Pune" |
| `aliases` | comma list of other names, e.g. "Bengaluru" for `BLR`; search matches aliases |
| `state` | e.g. "Karnataka", "Maharashtra" |
| `live` | boolean; `true` = active (offered in pickers, search and new properties) |
| `pincode_prefixes` | comma list of pincode prefixes, e.g. `56` for `BLR`, `41` for `PNQ` (added by the cities migration, D-84); `400,401` for `MUM`, `500,501,502` for `HYD`, `11` for `DEL`, `122` for `GGN`, `201` for `NOI` (`V8__launch_cities.sql`) |
| `region` | optional group name the copy shows together, e.g. `Delhi NCR` for `DEL`, `GGN`, `NOI`; null otherwise (`V8__launch_cities.sql`, D-82, D-85) |

- **D-20** Cities are data, not an enum. The `cities` table is the **only** list of cities: no city code, name, count or "only these cities" rule is hard-coded anywhere in the API, the web, UI copy or error messages (test fixtures may use the seed codes). Launch rows: `BLR` Bangalore (alias Bengaluru, Karnataka) and `PNQ` Pune (Maharashtra), then `MUM` Mumbai, `HYD` Hyderabad, `DEL` Delhi, `GGN` Gurugram and `NOI` Noida (`V8__launch_cities.sql`; the last three in region `Delhi NCR`), all `live=true`. The API rejects any `city` that is not a live row (`422 city_not_live`, detail "We are not live in this city yet."). City pickers and filters list only `GET /cities` results. The 4 + 4 demo properties of the seed stay as they are (§16).
- **D-82** `GET /cities` returns the active cities as `{code, name, aliases[], state, region}`, ordered by name. `region` is null unless the city belongs to a group the copy shows together (`Delhi NCR` for Delhi, Gurugram and Noida; *added with the launch cities*, `V8__launch_cities.sql`). Optional `?q=` matches code, name, any alias, state or region, case-insensitive, as a prefix starting at any word, so multi-word queries work ("beng" finds Bangalore through "Bengaluru"; "navi mumbai" and "mumbai" both find Navi Mumbai). The web city picker filters the same fields client-side over the loaded list and calls `?q=` only when the list is paged (more than 200 active cities, not expected in v1). `GET /properties?city=` takes a code; free text never reaches the property search.
- **D-83** Adding a city is a database row, not a release: an admin creates it from the admin console (`POST /admin/cities`, created inactive) or a Flyway data migration inserts it, then an admin activates it (`PATCH /admin/cities/{code}` with `live=true`). Activate, deactivate and edits are audited (D-87). Deactivating hides the city from pickers, search and new properties; existing properties, tenancies, rent history and reviews in it stay readable by direct link (like archived, D-21) and their parties can still end tenancies and review. Codes are never reused.
- **D-84** Pincode rule from data: a property's 6-digit pincode must start with one of its city's `pincode_prefixes`. A city with no prefixes accepts any valid 6-digit pincode. This replaces the hard-coded `56xxxx`/`41xxxx` rule.
- **D-85** City UI everywhere is driven by the list: the city control is a searchable picker (combobox) on Search, onboarding (home city), the property form and admin filters, never a fixed two-option toggle. Any copy that names or counts cities is built from the list. *Changed with the launch cities (captain 2026-11-22, R14)*: cities that share a `region` are named once as the region with its cities, at the first city's place ("Delhi NCR (Delhi · Gurugram · Noida)"); with up to 6 such names the copy lists them ("Live in Bangalore, Delhi NCR (Delhi · Gurugram · Noida), Hyderabad, Mumbai and Pune; other cities on request"), with more it counts cities ("Live in 12 cities; other cities on request"). The product never says "only". The launch cities are Bangalore, Pune, Mumbai, Hyderabad and Delhi NCR (Delhi, Gurugram, Noida); any other city is added as data (D-83). *Changed in rename layer 1, redesigned in landing v2*: the logged-out Home (`/`) is an explanatory, mobile-first scrolling landing with no city picker: a sticky header (section links, "Sign in"); hero (headline, subline, the launch copy built from the list, a "Sign in / Sign up with Google" button, a "See how it works" anchor, a sample app screen marked "Sample screen"); the problem (tenants, landlords, no trusted record); how it works in 3 steps; one card for tenants and one for landlords; trust and verification (phone OTP required, the green tick of D-134, optional Aadhaar/PAN and LinkedIn badges); a swipeable row of up to 6 top-rated published homes from `GET /properties` (seed rows show "Demo data"; with none readable, cards marked "Sample"; no rent on a card) between example reviews marked "Demo data", each "Not verified yet" with no green tick, because the seeded reviews are not verified yet; one card per active city with its first localities from `GET /areas`, linking into Search; a short FAQ; the sign-in button again; Terms and Privacy; a signed-in visitor of `/` goes to Search, and Search needs a session (*changed in admin separation*: there is no `/admin` on the main site, §13.3).

### 5.3 Property (`properties`, has `public_id`)
| Column | Notes |
|---|---|
| `landlord_id` | FK users; current owner. One owner has any number of properties, all on one landlord profile (D-113) |
| `city_code` | FK cities, must be live; equals the area's city |
| `area_id` | FK `areas`; required by the API on every property (D-110; nullable in DDL, D-114) |
| `society_id`, `tower_id`, `wing_id` | FK `societies`, `society_towers`, `society_wings`; all null for a standalone house or building (D-110) |
| `locality` | e.g. "Koramangala"; *changed in design v3*: copied from the area's name by the API (not client input), normalised into `locality_key` (lower-case, single spaces; indexed with `city_code`, `status`) so the V1 search index keeps working |
| `building_name`, `street` | public; `building_name` is the property's title on cards ("Demo Residency A"); inside a society the society, tower and wing names are shown with it |
| `unit_label` | flat/house number; **private** (parties + admin only). The property row is the unit / flat level of the hierarchy |
| `pincode` | 6 digits, must start with one of the city's `pincode_prefixes` (D-84) and be one of the area's pincodes (D-108) |
| `property_type` | `APARTMENT`, `INDEPENDENT_HOUSE`, `VILLA`, `STUDIO`, `ROOM`, `PG` |
| `bedrooms`, `bathrooms`, `area_sqft`, `furnishing` (`UNFURNISHED`, `SEMI`, `FULL`), `parking` | optional facts |
| `description` | ≤ 2000 chars |
| `status` | `DRAFT`, `PENDING_REVIEW`, `REJECTED`, `PUBLISHED`, `ARCHIVED` (D-92) |
| `review_reason`, `reviewed_by`, `reviewed_at` | last listing decision (added by the verification-core migration, D-98); *design v4 (open by default)*: the reason, admin and time of the green-tick decision (D-134) |
| `verification_status` | *design v4 (open by default)*: `UNVERIFIED` (null in old rows), `VERIFIED`, `REJECTED` (D-134); added by the verification-state migration |
| `is_demo`, `deleted_at` | |

- **D-21** Only users with `LANDLORD` create properties. A property is visible to others only when `PUBLISHED`; it reaches `PUBLISHED` only through admin listing approval (D-92). *Changed in design v4 (open by default)*: it reaches `PUBLISHED` when its landlord submits it, and admin approval gives the green tick instead (D-92, D-134). Archiving hides it from search but keeps its tenancies, rent history and reviews readable via direct link.
- *Build order (PLAN B4a/B4b)*: the first properties slice ships before the location hierarchy (B15) and the approval gate (B11, B12) are built. Until B4b, `locality` is client text (trimmed, single-spaced), the location lock covers city, locality, building, street and pincode, and `submit` checks only the state; B4b switches to the area-based location of D-110 and adds the gates. No deployment has property rows before then, so nothing is backfilled.
- **D-22** Ownership transfer between landlords is not in v1. The tenancy stores its own `landlord_id`, so history stays correct if transfer is added later.
- After publishing, the location fields (area, society, tower, wing, building, street, pincode) are locked like the design-v2 location fields (D-92).

### 5.4 Tenancy (`tenancies`, has `public_id`)
| Column | Notes |
|---|---|
| `property_id`, `landlord_id`, `tenant_id` | `landlord_id` must equal the property's landlord at creation; `landlord_id ≠ tenant_id` |
| `start_date`, `end_date` | `end_date` null = current; `start_date ≤ today`; `end_date ≥ start_date` |
| `initial_rent_inr`, `deposit_inr` | rent at start (becomes the first RentRecord); deposit optional. Nullable since V12 (vendor files; SQLite rebuilds the table): a tenant's own "Add your home" record may leave the rent out, shown **"Rent not shared"**, and then has no RentRecord; every two-sided request still needs it |
| `deposit_refunded_inr` | optional, set when ending |
| `status` | see §6 |
| `decline_reason` | reason given by the counterparty on decline (D-34) |
| `initiated_by` | `LANDLORD` or `TENANT` |
| `invite_email` | when the counterparty has no account yet |
| `confirmed_at`, `ended_at`, `voided_at`, `void_reason` | |
| `evidence_level` | `MUTUAL` (both confirmed) or `DOCUMENT` (admin checked an uploaded agreement) |
| `verification_status`, `verification_decided_by`, `verification_decided_at`, `verification_reason` | *design v4 (open by default)*: the green tick (D-134); `UNVERIFIED` (null in old rows), `VERIFIED`, `REJECTED`; added by the verification-state migration |

- **D-23** One tenancy row = one tenant on one property for one continuous period. The same tenant cannot have two overlapping tenancies on the same property. A whole home (`APARTMENT`, `INDEPENDENT_HOUSE`, `VILLA`, `STUDIO`) holds one active or confirmed tenancy at a time: a request or confirmation whose dates overlap a confirmed tenancy of any tenant there is refused with `409 tenancy_overlap`, whose `conflict` names that tenancy's period (`from`, `to` months; no `to` while current), its `status` and, for the home's landlord only, its `tenancyId`. Nothing is ended automatically; the landlord ends the current tenancy first. Back-to-back periods (the new start after the old end day) are allowed. `ROOM` and `PG` take flatmates: separate tenancies on the same property that may overlap (D-26 "room/share" rows). Co-tenants on one whole-home lease (several tenants as parties on one tenancy) are future work.
- **D-24** A renewal with the same tenant on the same property is the **same** tenancy (add a RentRecord), not a new one. A break of more than 30 days is a new tenancy.

### 5.5 RentRecord (`rent_records`, has `public_id` for `/rent-records/{id}/…`)
| Column | Notes |
|---|---|
| `tenancy_id` | |
| `effective_from` | first day of the month the rent applies from; within tenancy dates |
| `monthly_rent_inr` | > 0 |
| `maintenance_inr` | optional, separate from rent |
| `added_by` | user |
| `status` | `PROPOSED`, `CONFIRMED`, `DISPUTED` |

- **D-25** *Changed by rule B (D-150): a rent-history row is `verified` when its tenancy has the team tick; the other party's confirmation stays the separate, optional `confirmed` flag, shown as "Owner confirmed" or "Tenant confirmed" by who confirmed.* The first RentRecord is created from `initial_rent_inr` and is `CONFIRMED` when the tenancy is confirmed. Later records (rent increases) are added by either party and become `CONFIRMED` only when the other party confirms. Only `CONFIRMED` records appear in public rent history. *Changed in design v4 (open by default)* (D-132): `PROPOSED` records of `ACTIVE`/`ENDED` tenancies also appear in rent history at once, each row marked "Not verified yet" unless it is `CONFIRMED` by the other party or its tenancy has the green tick (D-134); `DISPUTED` records stay out. Rent-history rows gain `confirmed` and `verified` flags.
- **D-26** A property's rent history is the merged list of confirmed rent records across all its tenancies, shown as `(month from, month to, monthly rent)` rows with **no tenant identity**. Rows from overlapping flatmate tenancies are shown as separate rows labelled "room/share" when `property_type` is `ROOM` or `PG`.

### 5.6 Review (`reviews`, has `public_id`)
| Column | Notes |
|---|---|
| `tenancy_id`, `direction` | unique together (D-38) |
| `author_id` | must be the tenancy party implied by `direction` |
| `subject_user_id` | landlord or tenant for person directions; null for `TENANT_TO_PROPERTY` |
| `property_id` | always set (denormalised for queries) |
| `rating_overall` | integer 1–5, required |
| `rating_{a,b,c}` | optional sub-ratings 1–5, meaning depends on direction (D-29) |
| `body` | 30–2000 chars |
| `status` | see §7.2 |
| `submitted_at`, `published_at`, `edit_until`, `hidden_at`, `hidden_reason` | |
| `counts_in_aggregate` | false for superseded repeat-pair reviews (D-44) and hidden reviews |
| `last_edited_at`, `post_end_update_used`, `text_hidden_at`, `deleted_at` | "edited" date (§5.7); the one post-end update (D-40); text hidden by a `PERSONAL_DATA` report (D-50); withdrawal (D-41, D-15) |
| `verification_status`, `verification_decided_by`, `verification_decided_at`, `verification_reason` | *design v4 (open by default)*: the green tick (D-134, D-135); as on `tenancies` |

- **D-27** Rating scale is integer 1–5 stars (the old 1–10 scale is dropped). Aggregates show one decimal.
- **D-28** Person reviews show the author as display name short form plus the verified period, e.g. "Demo T. · verified tenancy, Mar 2024 – Feb 2025". The tenancy itself is never linkable by outsiders.
- **D-29** Sub-ratings, all optional:
  - `TENANT_TO_LANDLORD`: responsiveness, fairness (deposit and charges), respect for privacy.
  - `LANDLORD_TO_TENANT`: pays on time, care of property, communication.
  - `TENANT_TO_PROPERTY`: condition and maintenance, utilities (water, power), neighbourhood.

### 5.7 ReviewResponse and ReviewRevision
- `review_responses(review_id unique, author_id, body ≤ 1000, status, created_at, edit_until)`: the review's **subject** (or the property's landlord for property reviews) may post **one** public reply.
- `review_revisions(review_id, revision_no, rating_overall, sub-ratings, body, created_at)`: every edit keeps the previous version. Admins can read revisions; the public sees "edited" with the date.

### 5.8 TenancyDocument (`tenancy_documents`)
Optional private evidence (rent agreement, receipt) uploaded by a party: `tenancy_id`, `uploaded_by`, `storage_key` (private storage), `content_type`, `size_bytes`, `sha256`, `status` (`UPLOADED`, `ACCEPTED`, `REJECTED`), `reviewed_by`.
- **D-30** Documents are visible only to the uploader, the other party, and admins, through short-lived signed URLs (5 min). They are never public and never shown on reviews.

### 5.9 Enquiry (`enquiries`, has `public_id`)
`tenant_id`, `property_id`, `message ≤ 1000`, `share_contact` (bool), `status` (`OPEN`, `CLOSED`), `expires_at` (`created_at + 90 days`).
- **D-31** An enquiry is the access grant that lets a landlord see a tenant's profile and tenant reputation (D-54). It is not a chat. If `share_contact` is true the landlord also sees the tenant's email (and phone if set). Replies happen off-platform in v1.
- **D-32** A tenant can have at most 10 open enquiries and send at most 20 per rolling 7 days (limits spam and profile scraping).

### 5.10 RatingAggregate (`rating_aggregates`)
Primary key `(subject_type, subject_id)`, where `subject_type` ∈ `LANDLORD` (user), `TENANT` (user), `PROPERTY`, and from design v3 the roll-ups `SOCIETY` (society id), `AREA` (area id) and `OWNER_HOMES` (landlord user id) (D-111).
Columns: `review_count`, `rating_sum`, `average` (decimal 3,2), `bayes_score` (decimal 4,3), `count_1` … `count_5`, `sub_a_avg`, `sub_b_avg`, `sub_c_avg`, `last_review_at`, `updated_at`.

- **D-33** Aggregates are stored rows, updated in the same DB transaction as any change to a review that counts (publish, edit, hide, restore, supersede). The update recomputes the subject's row from source reviews (not an increment), so it is always self-correcting. A nightly job recomputes all rows as a repair pass.

### 5.11 Report and ModerationAction
- `reports(public_id, reporter_id, target_type, target_id, reason, details ≤ 1000, status, handled_by, resolution_note, created_at, handled_at)`.
  `target_type` ∈ `REVIEW`, `REVIEW_RESPONSE`, `USER`, `PROPERTY`, `TENANCY`.
  `reason` ∈ `FAKE_TENANCY`, `PERSONAL_DATA`, `HARASSMENT_OR_HATE`, `OFF_TOPIC`, `CONFLICT_OF_INTEREST`, `SPAM`, `OTHER`.
  `status` ∈ `OPEN`, `ACTIONED`, `DISMISSED`.
- `moderation_actions(admin_id, action, target_type, target_id?, target_ref, report_id?, reason, created_at)`: append-only audit log of every admin action and decision (D-87). `target_ref` is the target's `public_id` (or the city code for `CITY`), so cities and future non-BIGINT targets fit; `target_id` stays for joins and is null for cities. Rebuilt by the verification-core migration (D-98).
  `action` adds, to the D-51 set: `APPROVE_APPLICATION`, `REJECT_APPLICATION`, `REVOKE_APPROVAL`, `APPROVE_PROPERTY`, `REJECT_PROPERTY`, `VIEW_DOCUMENT`, `CREATE_CITY`, `UPDATE_CITY`, `ACTIVATE_CITY`, `DEACTIVATE_CITY`, `GRANT_ADMIN`, `REVOKE_ADMIN`, and from design v3 `VERIFY_IDENTITY`, `REJECT_IDENTITY`, `REVOKE_IDENTITY` (replacing design v2's `SET_LINKEDIN_CHECK`), `CREATE_AREA`, `UPDATE_AREA`, `APPROVE_LOCATION`, `UPDATE_LOCATION`, and from design v4 `VERIFY_AGREEMENT_ANCHOR`, `REJECT_AGREEMENT_ANCHOR`, and (open by default) `VERIFY_ITEM`, `REJECT_ITEM` (green tick on a property, tenancy or review, D-134). `target_type` adds `APPLICATION`, `DOCUMENT`, `CITY`, `IDENTITY`, `AREA`, `SOCIETY`, `TOWER`, `WING`, and from design v4 `AGREEMENT` (`PROPERTY`, `TENANCY` and `REVIEW` exist already).

### 5.12 Supporting tables
`user_roles`, `refresh_tokens` (D-63), `share_links` (tenant reputation links, D-54; hashed token, 30-day expiry, revocable), `invite_tokens` (tenancy invites to people without an account; hashed, single use, 14-day expiry), `scheduled_job_lock` (D-08), `property_images` (§15), `idempotency_keys` (§12.1; added in migration `V2`: `scope` = caller's user `public_id`, unique `(scope, idempotency_key)`, `request_hash`, `state` IN_PROGRESS/COMPLETED, stored response incl. `Location`, `expires_at`).

### 5.13 VerificationApplication (`verification_applications`, has `public_id`, verification-core migration)
One per user per role; it carries the approval for that role (§7.3).
| Column | Notes |
|---|---|
| `user_id`, `role` | unique together; `role` ∈ `TENANT`, `LANDLORD` |
| `status` | `DRAFT`, `PENDING`, `APPROVED`, `REJECTED` (D-90) |
| `revision` | starts at 1; +1 on each resubmission after a rejection |
| `flags` | comma list set by the API, e.g. `DUPLICATE_ID`, `DUPLICATE_PHONE` (D-102, D-101); shown to admins only |
| `submitted_at`, `decided_at`, `decided_by` | |
| `decision_code`, `decision_note` | on reject: `NO_VERIFIED_ID`, `ID_UNREADABLE`, `ID_NAME_MISMATCH`, `AADHAAR_NOT_MASKED`, `DOCUMENT_MISSING`, `DUPLICATE_ACCOUNT`, `OTHER`, plus a note shown to the user; on approve: optional note (admin only) |
| `version` | optimistic lock (JPA `@Version`); a concurrent approve, reject, revoke or resubmit of the same application loses with `409 invalid_transition` and keeps nothing |

*Changed in design v3*: the design-v2 columns `linkedin_url`, `linkedin_not_provided` and `linkedin_check` are gone. LinkedIn is one optional `identity_verifications` row per user (D-106), shared by both role applications, and `LINKEDIN_MISMATCH` is no longer a reason to reject an application: a mismatch only means no LinkedIn badge (D-100).

Previous revisions are not overwritten. `verification_events(application_id, revision, event, actor_id, code, note, created_at)`, append-only, keeps the history: `event` ∈ `SUBMITTED`, `APPROVED`, `REJECTED`, `REVOKED`, `IDENTITY_CHECKED`, `PHONE_VERIFIED` (phone verified, code `TRUECALLER` or `SMS_OTP`, D-138), and from design v4 `AGREEMENT_CHECKED` (D-130). Admin events are also written to `moderation_actions` (D-87) in the same transaction; the record detail merges both into one timeline.

### 5.14 VerificationDocument (`verification_documents`, has `public_id`, verification-documents migration)
Private files uploaded for verification (D-95).
| Column | Notes |
|---|---|
| `owner_id` | the uploading user |
| `kind` | `ID_PROOF`, `PROPERTY_PROOF`. `RENTAL_AGREEMENT`: *Changed in design v4: replaced by `rental_agreements` (D-123, D-131)*; rental agreements are uploaded only through `/me/agreements` |
| `identity_verification_id` | set for `ID_PROOF`: the PAN or Aadhaar check it proves (§5.15) |
| `application_id` | *Changed in design v4: replaced by `rental_agreements` (D-123, D-131)*: was set for `RENTAL_AGREEMENT`; not created |
| `property_id` | set for `PROPERTY_PROOF` (listing approval, D-92) |
| `id_type` | `ID_PROOF` only: `PAN` or `AADHAAR_MASKED` (D-102, D-103). *Changed in design v3*: passport, driving licence and voter ID are dropped; the masked number fields moved to `identity_verifications` |
| `storage_key`, `content_type`, `size_bytes`, `sha256` | PRIVATE object (D-66) |
| `status` | `UPLOADED`, `ACCEPTED`, `REJECTED`, `PURGED` (file deleted: `ID_PROOF` as soon as its check is decided, other kinds by retention, D-96) |
| `reviewed_by`, `reviewed_at`, `purge_after`, `deleted_at` | `purge_after` is null for `ID_PROOF` (its file never waits for the retention job) |

### 5.15 IdentityVerification (`identity_verifications`, has `public_id`, identity migration)
One row per user per method; the user-level record behind the "ID verified" and "LinkedIn verified" badges (D-99, D-100). It serves both roles: a user who is tenant and landlord verifies once.
| Column | Notes |
|---|---|
| `user_id`, `method` | unique together; `method` ∈ `AADHAAR`, `PAN`, `PHONE_OTP` (ID methods), `LINKEDIN` (bonus, never counts as ID) and `TRUECALLER` (a phone method like `PHONE_OTP`, D-138) |
| `status` | `PENDING` (submitted, waiting for an admin check), `VERIFIED`, `REJECTED`, `REVOKED` |
| `last4` | `PAN`: last 4 characters; `AADHAAR`: the last 4 digits printed on the masked Aadhaar; `PHONE_OTP` and `TRUECALLER`: last 4 digits of the phone; null for `LINKEDIN`. The full value is never stored (D-106) |
| `value_hmac` | HMAC-SHA256 of the normalised PAN or E.164 phone with `ID_HASH_KEY`, for duplicate detection; **null for `AADHAAR`** (Rentrix never receives the full number) and `LINKEDIN` |
| `linkedin_url` | `LINKEDIN` only: normalised `https://www.linkedin.com/in/<slug>`, private (D-93) |
| `checked_by` | `ADMIN` (admin compared the upload or profile), `OTP` (code confirmed), `PROVIDER` (later, D-104) |
| `provider`, `provider_ref` | null in v1; the KYC provider and its reference id once one is used (D-104) |
| `verified_at`, `verified_by` | `verified_by` = admin user for admin checks, null for OTP |
| `reject_code`, `reject_note` | on `REJECTED` / `REVOKED`: `ID_UNREADABLE`, `ID_NAME_MISMATCH`, `AADHAAR_NOT_MASKED`, `LINKEDIN_MISMATCH`, `PHONE_CHANGED`, `OTHER`; note shown to the user |
| `revoked_at` | |
Index `(method, value_hmac)` for duplicates; `(status, method, created_at)` for the admin queue.

### 5.16 PhoneOtpChallenge (`phone_otp_challenges`, identity migration)
`user_id`, `phone_e164` (the number being verified; private, D-57), `code_hmac` (HMAC of the 6-digit code with `ID_HASH_KEY`; the code itself is never stored or logged), `expires_at` (10 min), `attempts` (max 5), `consumed_at`, `created_at`. Rows are deleted 24 h after they expire or are consumed (D-08 job). Index `(user_id, created_at)` for the send limits (D-101).

### 5.17 Area (`areas`, has `public_id`, location migration) and AreaPincode (`area_pincodes`)
| Column | Notes |
|---|---|
| `city_code` | FK `cities` |
| `name`, `name_key` | locality name, e.g. "Koramangala"; `name_key` lower-case, single spaces; unique `(city_code, name_key)` |
| `aliases` | comma list, e.g. "Kormangala" (common spelling); search matches aliases |
| `status` | `ACTIVE`, `PENDING` (proposed by a landlord, approved with the listing, D-109), `HIDDEN` |
| `created_by`, `is_demo` | |

`area_pincodes(area_id, pincode CHAR(6))`, primary key `(area_id, pincode)`, index `(pincode)`: one area can span several PIN codes and one PIN code can cover several areas, so lookup works both ways (D-108).

### 5.18 Society, SocietyTower, SocietyWing (location migration, all have `public_id`)
- `societies`: `area_id` (FK), `city_code` (copy of the area's city, for city-wide browse), `name`, `name_key` (unique per area), `developer` (optional, e.g. "Sobha"), `kind` (`GATED_COMMUNITY`, `APARTMENT_COMPLEX`), `street`, `status` (`ACTIVE`, `PENDING`, `HIDDEN`), `created_by`, `is_demo`. Shown as the name with "by <developer>" under it (e.g. "Dream Acres · by Sobha"); search matches both.
- `society_towers`: `society_id` (FK), `name` (e.g. "Tower 1", "Block C"), `name_key` (unique per society), `kind` (`TOWER`, `BLOCK`), `sort_order`, `status`.
- `society_wings`: `tower_id` (FK), `name` (e.g. "Wing A" or "B"), `name_key` (unique per tower), `sort_order`, `status`. A tower may have no wings.

### 5.19 Location hierarchy

```
City            cities              BLR Bangalore
 └─ Area        areas (+ PIN codes) Koramangala · 560034, 560095
     ├─ Society       societies         Demo Greens (gated community)
     │   └─ Tower/Block   society_towers    Tower 1
     │       ├─ Wing          society_wings     Wing A
     │       │   └─ Unit/Flat     properties        Demo Residency A · unit D-101 (private)
     │       └─ (no wing)     properties        a unit directly in a tower without wings
     └─ Standalone    properties        house, villa or building outside any society
                                         (society_id, tower_id, wing_id all null)
```

- **D-107** Location is a hierarchy of rows, not free text: City → Area → Society → Tower/Block → Wing → Unit. The **property is the unit** (the leaf); its `unit_label` stays private (D-57). Society, tower and wing are optional levels: a standalone house, villa or building has an area but no society; a tower may have no wings. Every level above the unit is public (building-level, not unit-level data).
- **D-108** Areas are found by PIN code or locality name. `GET /areas?city=&q=&pincode=` matches `name`, `aliases` (prefix of any word, like D-82) or an exact 6-digit PIN code through `area_pincodes`. A property's `pincode` must be one of its area's PIN codes (`422 pincode_not_in_area`) and still start with a city prefix (D-84). Area rows come from the seed and from admins; v1 has no external PIN-code lookup (an import of India Post's open PIN-code directory is a later data task).
- **D-109** Creating location rows. Landlords pick existing rows in the property form (area, then society, tower, wing). If one is missing they may propose it inline ("Add a society", "Add a tower", "Add a wing", "Suggest an area"); proposed rows are created `PENDING`, linked to the draft, and visible only to the proposer and admins. A proposal whose name matches a `PENDING` row under the same parent joins that row instead of creating a duplicate (the home goes on it, a joined area gains the joiner's PIN code, and it keeps its first proposer, so its approval or rejection covers every home on it); a matching `ACTIVE` or `HIDDEN` row is a `409 location_exists`. By id (detail pages, property filters, reuse in a draft) a `PENDING` row is also readable by a landlord who has a home on it; lists and pickers still show it only to its proposer and admins, and errors never show details of a `PENDING` row the caller cannot read. Listing approval (D-92) approves them together with the property; the admin can instead point the listing at an existing row when the proposal is a duplicate (`UPDATE_LOCATION`, audited). Admins also create, rename and hide areas, societies, towers and wings directly in the admin console. Rows are never hard-deleted; hiding keeps existing properties readable. A published home shows its place at once, `PENDING` names included (open by default, captain rules R2 / R8): its page and search card tag them "Pending review" (`PropertyCard.locationPending`) until the team reviews them. Tower and wing names are 1–60 characters with a letter or digit ("A" wing, Tower "4"); area and society names 2 or more.
- **D-110** Placement rules, checked by the API on create and edit: `area_id` is required; `wing_id` needs `tower_id`, `tower_id` needs `society_id`; the wing belongs to the tower, the tower to the society, the society to the area, and the area to `city_code` (`422 location_mismatch`). `locality` is copied from the area. Location fields are locked after publish (D-92).

### 5.20 RentalAgreement (`rental_agreements`, has `public_id`, rental-agreement migration, design v4)
One uploaded rental agreement and what Rentrix read and checked from it (§7.6). Created by one party (the uploader); linked to a tenancy, or during onboarding to the uploader's application until the matching tenancy exists (D-123).
| Column | Notes |
|---|---|
| `uploader_id`, `uploader_side` | the uploading user and the side they act on (`TENANT` / `LANDLORD`) |
| `tenancy_id` | nullable; set when linked to a tenancy (one live agreement per tenancy; a replaced one keeps its row with `status=REPLACED`) |
| `application_id` | nullable; the uploader's application when uploaded in the onboarding stepper (D-120) |
| `city_code`, `anchor_kind` | the city of the home; `anchor_kind` ∈ `MH_LEAVE_LICENCE`, `KA_ESTAMP`, `OTHER_STAMP`, `NONE` (unregistered or no stamp), from the city's state (D-124) |
| `anchor_number`, `anchor_number_key` | registration document number (MH) or e-stamp certificate number (KA), as typed; `anchor_number_key` = upper-case without spaces and dashes, indexed for reuse checks (D-129) |
| `anchor_office`, `anchor_year`, `anchor_date` | MH: Sub-Registrar office (e.g. "Haveli 1") and registration year; `anchor_date` = registration date (MH) or e-stamp issue date (KA) |
| `grn`, `stamp_amount_inr`, `registration_fee_inr` | MH GRAS challan GRN and stamp duty paid; KA stamp amount; MH registration fee |
| `stamp_purchaser`, `stamp_article` | KA: purchaser name and article printed on the e-stamp (e.g. "Article 30 Lease of Immovable Property") |
| `landlord_name`, `tenant_name` | party names as printed (masked per D-131) |
| `address_text`, `pincode`, `society_text`, `tower_text`, `unit_text` | the home as printed; `unit_text` is private like `unit_label` (D-57) |
| `monthly_rent_inr`, `deposit_inr`, `escalation_pct` | money terms; `escalation_pct` NUMERIC(5,2), nullable |
| `start_date`, `end_date`, `term_months` | |
| `extractor` | `MANUAL` in v1 (D-127) |
| `sha256`, `content_type`, `size_bytes`, `page_count` | of the uploaded file; `sha256` is what both sides confirm (D-128) |
| `storage_key` | PRIVATE, encrypted object (D-131); null once the file is deleted |
| `pdf_producer`, `pdf_modified_at` | PDF metadata read at upload for the edit signal (D-129); null for images |
| `uploader_confirmed_at`, `counterparty_confirmed_at`, `counterparty_confirmed_by` | each side's confirmation of `sha256` (D-128) |
| `checks` | TEXT holding the JSON list of check results `[{check, status, score, reasons:[{code, message}]}]` (D-125); written by the API, read whole, never queried in SQL (D-10 allows no vendor JSON functions) |
| `tier` | `UNVERIFIED`, `MATCHED`, `VERIFIED` (D-126) |
| `anchor_result`, `anchor_checked_by`, `anchor_checked_at`, `anchor_note` | admin government lookup (D-130): `anchor_result` ∈ `VERIFIED`, `NOT_FOUND`, null |
| `flags` | comma list for admins: `AGREEMENT_REUSED`, `PDF_EDITED`, `AADHAAR_MASKED_ON_INTAKE` |
| `status` | `DRAFT` (uploaded, fields not confirmed by the uploader), `SUBMITTED` (uploader confirmed), `DECIDED` (admin lookup recorded or linked application decided), `REPLACED` |
| `consent_at` | the uploader's consent (D-131) |
| `decided_at`, `file_deleted_at`, `purge_after` | retention (D-131): `purge_after` = 30 days after the later of `decided_at` and `counterparty_confirmed_at`; 90 days after upload when no other party is linked |

Indexes: `(tenancy_id)`, `(application_id)`, `(uploader_id, created_at)`, `(sha256)`, `(anchor_kind, anchor_number_key)`, `(purge_after)`. Full DDL: [`../database/schema.md`](../database/schema.md) §6.

---

## 6. Tenancy verification lifecycle

```
           create (either party)
                  │
                  ▼
        PENDING_CONFIRMATION ──decline──▶ DECLINED
         │        │   └──expire (14 d)──▶ EXPIRED
         │        └──cancel (initiator)─▶ CANCELLED
         │ confirm (counterparty)
         ▼
       ACTIVE ──end (either party, end_date)──▶ ENDED
         │                                        │
         └────────── admin void ──────────────────┴──▶ VOIDED
```

*Changed in design v4 (open by default)* (D-34, D-132): `create` goes straight to `ACTIVE` (or `ENDED` with a past end date); the counterparty's confirm sets `confirmed_at` ("Confirmed by both") and their decline sets `decline_reason` and puts the tenancy in the admin queue, without a state change. `PENDING_CONFIRMATION`, `EXPIRED` and `DECLINED` are no longer used for new rows; the initiator may still `cancel` (→ `CANCELLED`) while the other party has not confirmed and no review exists. A landlord-added period for an invitee without an account is `ACTIVE` with the tenant shown as invited; reviews of the tenant wait until the invite is claimed.

- **D-34** Two-party confirmation is the trust anchor. *Changed by rule B (D-147, D-149): the team verifies tenancies from the rental agreement; the counterparty's confirmation (`confirmed_at`) is only an optional badge, "Owner confirmed" or "Tenant confirmed" by who confirmed, and never a requirement.* A tenancy starts as `PENDING_CONFIRMATION` and becomes `ACTIVE` (or `ENDED`, if created with an end date in the past) only when the **other** party confirms. Unconfirmed tenancies enable nothing: no reviews, no public rent history. *Changed in design v4 (open by default)*: counterparty confirmation is a trust signal shown on the tenancy, not a gate. A tenancy period added by one party is `ACTIVE` (or `ENDED`) at once and visible as "Not verified yet"; its party may add rent records and reviews at once. The other party's confirmation shows as "Confirmed by both", and an admin review gives the green tick (D-132, D-134). A counterparty who declines puts the tenancy in the admin queue as disputed; it stays visible until an admin voids it (D-36).
  - Landlord-initiated: landlord picks own property, enters tenant email, dates, rent. Tenant gets an email invite; they sign in with Google from the link (creating an account if needed), then confirm. The invite binds only to an account whose verified Google email equals `invite_email`.
  - Tenant-initiated: tenant picks a published property, enters dates and rent. The property's landlord confirms or declines.
  - The counterparty sees all fields before confirming and can decline with a reason; corrections are made by declining and re-creating (no counter-offer flow in v1).
- **D-35** Guards at creation: the initiator's verification for their side is `APPROVED` (D-91), and the counterparty's must be `APPROVED` before they can confirm, while declining needs no approval (an invitee without an account signs in, onboards and is approved first; the 14-day expiry still runs, so admins aim to decide within 48 h); *changed in design v4 (open by default)*: read "has a verified phone" (D-132) for "is `APPROVED`", for the initiator and for the confirming counterparty, and the 14-day expiry no longer applies to a tenancy that is already `ACTIVE` (D-34); both accounts email-verified; `landlord_id ≠ tenant_id`; dates valid; no overlapping tenancy for the same tenant + property, and on a whole home none with any confirmed tenancy (D-23); a user may create at most 5 pending tenancies at once (*changed in design v4 (open by default)*: replaced by a rate limit on added tenancy periods, because new tenancies are never pending: at most 5 per user per 24 h and 20 per 30 days, else `429 rate_limited` with `Retry-After`, D-132); an invite email cannot be the initiator's own email or an existing account with a different email in the payload.
- **D-36** Ending: either party sets `end_date` (≤ today). The other party is notified and can dispute within 14 days (→ report `FAKE_TENANCY` / admin review). An admin `VOID` (fake or mistaken tenancy) hides all its reviews, removes them from aggregates and removes its rent records from rent history; it is logged in `moderation_actions`.
- `evidence_level` is `MUTUAL` after confirmation. If a party uploads an agreement and an admin accepts it, it becomes `DOCUMENT`, shown as a stronger badge ("Agreement checked"). Document upload is optional in v1.

---

## 7. Review lifecycle and trust rules

### 7.1 Trust rules
- **D-37** Only a party of a tenancy in status `ACTIVE` or `ENDED` can review (*changed in design v4 (open by default)*: a tenancy is `ACTIVE` or `ENDED` when added, without waiting for the counterparty, D-34; the review shows "Not verified yet" until its green tick, D-134), and only in the directions their side allows: the tenant writes `TENANT_TO_LANDLORD` and `TENANT_TO_PROPERTY`; the landlord writes `LANDLORD_TO_TENANT`.
- **D-38** One review per direction per tenancy (`UNIQUE(tenancy_id, direction)`). So each tenancy yields at most three reviews.
- **D-39** Review window: opens when the tenancy has been `ACTIVE` for 60 days, or when it `ENDED` (whichever is first). *Changed 2026-11-23 (open model, firstmate)*: the window never closes for posting, so a tenant who adds a home they left years ago can still review it (it shows "Not verified yet" until the team verifies the tenancy); the 180-day close after `end_date` is gone. The D-41 edit lock is unchanged.
- **D-40** A review written during an `ACTIVE` tenancy may be **updated once** after the tenancy ends (at any time: there is no closing window, D-39), as long as it is still `AWAITING_REVEAL` (D-41); a published review gets no post-end update. The update creates a revision and resets `edit_until`.
- **D-41** Edit window: the author may edit text and ratings for **7 days** after submission, and only while the review is `AWAITING_REVEAL`. Once it or its counter-review publishes (D-46), or a property review publishes at once (D-47), its text and ratings are frozen, so neither side can react to the other's review. Once frozen, only admins can hide a review; the subject's one response (§5.7) is unaffected. Authors may withdraw (delete) their own review at any time; withdrawal removes it from aggregates and cannot be undone for that tenancy+direction.
- **D-42** The subject can never edit, delete or hide a review about them. Their tools are one public response (§5.7) and reporting (§8).
- **D-43** Content rules checked at submit (rejects with `422`): length 30–2000; no phone numbers, emails, URLs or unit numbers in the body (regex detection); basic profanity list. A unit number is flagged only with an explicit marker (`flat|unit|apt|apartment|house|door|room` followed by `no`, `no.`, `number` or `#`), a block-unit code tied to those words or to `tower|wing|block` (`flat B 402`, `tower C-1204`, `wing #1203`), a bare code of one capital letter, an optional hyphen or space and 3–4 digits (`A-1203`, `B 402`; multi-letter prefixes such as `NH-48` or `SH-17` never match, and neither does `A` or `I` followed by a space, as in `A 1200 sq ft`), or the home's own unit label. Numbers followed by years, months, weeks, days, km or min, and ranks like `#1`, are never flagged. Edge cases lean towards allowing the text; abuse reports (§8) catch what the regex misses. These keep personal data out of public text.
- **D-44** Repeat-pair rule: if the same landlord and tenant have more than one tenancy together, only the **most recent** tenancy's person reviews count in their aggregates (older ones stay visible with `counts_in_aggregate=false`). Property reviews from the same tenant: only the latest counts for that property. This stops a colluding pair from inflating ratings by cycling tenancies.
- **D-45** Account-age and volume guards: a user's first review needs an email-verified account (*changed by D-141*: the 7-day account age is dropped); at most 10 reviews submitted per user per 24 h. Admin dashboards flag landlords whose tenants are all created through landlord invites to brand-new accounts (signal for fake tenants).

### 7.2 Review states and reveal

```
 submit ──▶ checks pass ──▶ AWAITING_REVEAL ──(other side submits | 14 d | window close)──▶ PUBLISHED
                    │                                                                  │
                    └──(property review)──────────────────────────────▶ PUBLISHED      │
                                                                                       ▼
                                       WITHDRAWN ◀── author        HIDDEN ◀── admin ──▶ (restore) PUBLISHED
```

- **D-46** Double-blind reveal for the landlord↔tenant pair: a person review stays `AWAITING_REVEAL` (visible only to its author) until the counter-review for the same tenancy is submitted, or 14 days pass since the first of the two was submitted (the review window never closes, D-39). Then both publish together. This prevents retaliation reviews.
- **D-47** `TENANT_TO_PROPERTY` reviews publish immediately after checks (there is no counter-review).
- **D-48** Post-moderation: published reviews are live without admin approval (the tenancy gate replaces the old `PENDING` admin approval). Admin action happens on reports and automated flags. Admin approval applies to **people and listings** (§7.3), not to each review: an approved party of a confirmed tenancy publishes without a further check. *Changed in design v4 (open by default)*: reviews still publish without admin approval; an admin review gives each review its own green tick, and only green-tick reviews count in the headline average (D-134, D-135).

### 7.3 Onboarding with approvals

*Changed in design v4* (D-117, D-120): the steps are now 1 Profile, 2 Phone (OTP, required), 3 ID (optional: PAN, masked Aadhaar, LinkedIn), 4 Rental details (optional, asked of both roles), then Submit. The diagram below is the design-v3 shape; §7.5 has the current flow. *Changed in design v4 (open by default)* (D-132, D-133): "trust writes open" now happens at the phone step, not at `APPROVED`; Submit reads "Request verification", and `APPROVED` gives the user the green tick for that role (D-134). `REJECTED` leaves the user writing as before, with no tick.

```
 Google sign-in ─▶ role pick (D-73) ─▶ verification, per role
                                         1 Profile   2 Verify ID (≥1 of Aadhaar · PAN · phone OTP)   3 LinkedIn (optional)   4 Rental agreement (tenant, optional; v4: /me/agreements)
                                         └──────────────────────────── DRAFT ──────────────────────────────────────┘
                                                              │ submit
                                                              ▼
                                  ┌──────────────────────── PENDING ──── admin ──▶ APPROVED ─▶ trust writes open
                                  │ resubmit (revision+1)        │                     │
                                  └──── REJECTED (code + note) ◀─┘ admin               └─ admin revoke ─▶ REJECTED
```

- **D-89** Onboarding has two layers. (1) Account onboarding: the first-sign-in role pick (D-73), unchanged. (2) Verification: for each role the user holds, one `verification_application` (§5.13) that an admin approves. Steps, in order, each saved as a draft: **Profile** (display name, home city from the city picker); **Verify ID**: at least one of Aadhaar, PAN or phone OTP (D-99, D-101–D-103); **LinkedIn**, optional, can be skipped (D-93, D-100); **Rental agreement**, tenant only and optional ("if you rent now or rented before"; it helps the admin, and tenancy-level agreements stay in §5.8; *changed in design v4*: uploaded as a `rental_agreements` row through `/me/agreements`, not as a verification document, D-123, D-131). Landlords prove each property at listing time instead (D-92). Then **Submit** → `PENDING`. Adding the other role later (D-16) starts a second application; identity checks are per user (§5.15), so a `VERIFIED` method counts for the new role at once and nothing is uploaded again, but an admin still approves the new role. *Changed in design v3*: the ID step comes second and offers three methods; LinkedIn moved after it and is optional. *Changed in design v4*: the steps are Profile → **Phone** (OTP, required for both roles, D-117) → **ID** (optional extra badges: PAN, masked Aadhaar, LinkedIn) → **Rental details** (optional for both roles, the rental agreement of §7.6) → Submit; the web goes from the role pick through the "account created" screen straight into these steps (D-118, D-120).
- **D-90** States: `DRAFT` → (user submits) `PENDING` → (admin) `APPROVED` or `REJECTED`. A rejection needs a reason code and a note, both shown to the user; the user edits any step and resubmits (`PENDING`, `revision + 1`), with no limit except the D-45-style rate limit (3 submissions per 24 h). An admin can revoke an approval (→ `REJECTED`, with reason) when new facts appear; suspension (D-51) stays the tool for abuse. Every submit and decision is audited (D-87). Users get an email on each decision (D-65). Target: decisions within 48 h; the admin dashboard shows the oldest waiting item.
- **D-91** *Changed in design v4 (open by default)*: the approval gate is now an approval badge. Writing needs only a verified phone (D-117, D-132); `APPROVED` gives the green tick (D-134); `403 approval_required` is no longer returned. Design-v3 text, kept for the list of gated writes and safety exits, which still apply with "verified phone" read for "approval": The approval gate. **Reading never needs approval**: browsing, search, property pages, rent history, landlord profiles and every review text follow only the visibility rules of D-54–D-56. An `APPROVED` application for the matching role **and** at least one `VERIFIED` ID method (D-99, checked together, D-105; *changed in design v4*: a `VERIFIED` phone, D-117) are required to: create a tenancy request or confirm a tenancy; add, confirm or dispute a rent record; write or edit a review; post a review response; send an enquiry (`TENANT`); submit a property for listing (`LANDLORD`). Otherwise the API returns `403 approval_required` with `{role, status}`. The check reads the application from the database on each gated write (not from the JWT), so a decision takes effect at once. Safety exits are never gated: report content, withdraw an own review, decline a tenancy request, cancel an own pending request, end a tenancy already confirmed, delete the account. Admins are not gated in their admin actions.
- **D-92** *Changed in design v4 (open by default)*: submitting a property (`DRAFT` → `PUBLISHED`) needs only a landlord with a verified phone (D-132); the property is visible at once, marked "Not verified yet". `PROPERTY_PROOF` is optional and helps the admin; admin approval sets `verification_status=VERIFIED` (the green tick), reject sets `REJECTED` with a reason and the property stays published without a tick (D-134). `PENDING_REVIEW` and `REJECTED` are no longer used for new rows. The location lock and the rest of this decision stay. Design-v3 text: Property listing approval. Statuses: `DRAFT` → (landlord submits) `PENDING_REVIEW` → (admin) `PUBLISHED`, or `REJECTED` with reason → (landlord edits) → `PENDING_REVIEW`; `PUBLISHED` → `ARCHIVED` as before. Submitting needs an approved landlord and one `PROPERTY_PROOF` document for that property (a rental agreement for it, or proof of ownership such as a property-tax receipt or electricity bill in the landlord's name). After publishing, location fields (city, locality, building, street, pincode) are locked; to change them the landlord asks an admin. Facts, description and images stay editable without re-review. The 4 + 4 seeded demo properties are `PUBLISHED`.
- **D-93** LinkedIn check (*changed in design v3*: optional bonus, stored as the `LINKEDIN` row of `identity_verifications`, D-100, D-106). Automatic verification is not feasible in v1: "Sign in with LinkedIn" (OpenID Connect) returns name, email and photo but not the profile URL, and fetching profile pages breaks LinkedIn's terms. So the API only validates and normalises the URL (`https://www.linkedin.com/in/<slug>`, no query string, ≤ 200 chars) and never fetches it. An admin opens it from the console and marks the `LINKEDIN` row `VERIFIED` (profile matches the name and photo) or `REJECTED` with `LINKEDIN_MISMATCH`. Skipping the step stores nothing; the admin decides on the ID methods alone, and a missing or mismatched LinkedIn never blocks approval. The URL is private: owner and admins only; the public sees only the "LinkedIn verified" badge.
- **D-94** *Superseded in design v3 by D-99–D-106* (Aadhaar is now accepted as a masked copy, D-103; passport, driving licence and voter ID are dropped). Design-v2 text, kept for history: Identity document. Accepted types: PAN card, passport, driving licence, voter ID. Aadhaar is **not** accepted in v1, because the Aadhaar Act restricts storing Aadhaar numbers and copies. The user types the document number and uploads the file (PDF, JPEG or PNG, ≤ 10 MB; one file, or front + back). The API validates the format per type (PAN: `^[A-Z]{5}[0-9]{4}[A-Z]$`) and stores only `number_last4` and `number_hmac` (HMAC-SHA256 with `ID_HASH_KEY`). The full number is never stored, logged or returned by any API. When the same `number_hmac` appears on two accounts, both applications get the flag `DUPLICATE_ID` for the admin; nothing is rejected automatically. The UI masks every ID number to its last 4 characters, e.g. PAN `XXXXXX234F`.

### 7.4 Identity verification (design v3)

```
                      ┌──────────── Verify ID step (pick one or more) ─────────────┐
                      │                                                            │
  PHONE_OTP:  enter phone ─▶ SMS code ─▶ code matches ─▶ VERIFIED (no admin)       │
  PAN:        type PAN + upload card ─▶ PENDING ─▶ admin compares ─▶ VERIFIED / REJECTED
  AADHAAR:    type last 4 + upload MASKED Aadhaar ─▶ PENDING ─▶ admin compares ─▶ VERIFIED / REJECTED
  LINKEDIN:   (optional) paste URL ─▶ PENDING ─▶ admin opens ─▶ VERIFIED / REJECTED  (bonus only)
                      │                                                            │
                      └─▶ ≥1 ID method VERIFIED ⇒ "ID verified" badge (tenant and landlord profiles)
                          LINKEDIN VERIFIED     ⇒ "LinkedIn verified" badge (extra, never required)

  Application submit needs ≥1 ID method VERIFIED or PENDING; admin approval needs ≥1 VERIFIED (D-105).
```

*Changed in design v4*: phone OTP is required for every tenant and landlord, and PAN and masked Aadhaar are optional (D-117). D-99, D-100 and D-105 below carry the change in their own text; the method flows (D-101–D-104) and the masking rules (D-106) are unchanged.

- **D-99** *Superseded in design v4 by D-117 for the gate, which D-144 replaced with any one of phone, masked Aadhaar or LinkedIn; the "ID verified" badge rule moved to D-100.* **Verified = any one ID method.** A user is *ID verified* when at least one of `AADHAAR`, `PAN` or `PHONE_OTP` in `identity_verifications` is `VERIFIED`. LinkedIn is an optional bonus: it never counts towards ID verified and is never required. The rule is the same for tenants and landlords, and it is per user, so one check serves both roles. This replaces every design-v2 rule that made LinkedIn a step every applicant had to answer.
- **D-100** **Badges.** Two public badges, shown on tenant and landlord profiles, on property-page landlord cards and on review author lines: **"ID verified"** (shield-check, teal) when D-99 holds, and **"LinkedIn verified"** (link icon, info blue) when the `LINKEDIN` row is `VERIFIED`. Badges are a yes/no signal only: the public never sees which ID method was used, the masked value, the LinkedIn URL or any reject reason (D-57, D-95). Search and browse cards show the landlord's "ID verified" badge; a "LinkedIn verified" landlord also gets the second badge, which gives them the edge without being required. Tenant badges are visible only where the tenant profile is visible (D-54). *Changed in design v4*: "ID verified" means a `VERIFIED` PAN or masked Aadhaar (the optional extra ID); a verified phone alone gives no public badge, because every user who can write has one (D-117). `badges.idVerified` on `GET /me` and `/users/{id}` follows this rule.
- **D-101** **Phone OTP** (self-service, no admin). `POST /me/identity/phone/start {phone}` validates an Indian mobile number (E.164 `+91` and 10 digits starting 6–9), creates a `phone_otp_challenges` row and sends a 6-digit code by SMS through the `SmsSender` interface (`LogSmsSender` in dev/test writes it to the log; `HttpSmsSender` for a provider in prod, `SMS_*` env). The code is stored only as an HMAC, expires after 10 minutes and allows 5 attempts; a user can request 3 codes per hour and 10 per day, and one phone number gets at most 5 codes per day across all accounts (`429` with `Retry-After`). `POST /me/identity/phone/confirm {challengeId, code}` marks `PHONE_OTP` `VERIFIED`, stores `last4` + `value_hmac` and sets `users.phone`. If the same phone is verified on another active account, both applications get the flag `DUPLICATE_PHONE` for the admin (shared family phones exist, so nothing is blocked). Changing the phone (`PATCH /me`) sets the old row to `REVOKED` with `PHONE_CHANGED`. In `prod` with no SMS provider configured, the phone method is hidden (`GET /me/identity` lists it as unavailable) and its endpoints return `503 phone_otp_unavailable`; the API still starts. *Design v4*: a third, preview-only sender `AdminRelaySmsSender` (`SMS_BACKEND=admin-relay`, D-137) holds the code for admins instead of sending it. This is verification of a phone, not a login method: sign-in stays Google only (D-70).
- **D-102** **PAN** (admin-checked upload now). The user types the PAN and uploads the card (PDF, JPEG or PNG, ≤ 10 MB) through `POST /me/documents` (`kind=ID_PROOF`, `idType=PAN`). The API checks the format `^[A-Z]{5}[0-9]{4}[A-Z]$`, stores only `last4` and `value_hmac` on the `PAN` row (`PENDING`), and drops the typed value. An admin compares the name and number on the card with the profile and the masked last 4, then marks it `VERIFIED` or `REJECTED` (`ID_UNREADABLE`, `ID_NAME_MISMATCH`). The card file shows the full PAN, so it is kept only until that decision and deleted as soon as it is recorded (D-96); a resubmission uploads a new file. The same `value_hmac` on two accounts flags both applications `DUPLICATE_ID`; nothing is rejected automatically.
- **D-103** **Aadhaar, masked copy only.** To stay within the Aadhaar Act's limits on collecting and storing Aadhaar numbers, Rentrix never asks for, receives, stores or logs a full 12-digit Aadhaar number. The user downloads the **masked Aadhaar** from UIDAI (it prints only the last 4 digits, `XXXX XXXX 1234`), uploads it (`idType=AADHAAR_MASKED`) and types only those last 4 digits; the user also ticks a consent line ("I agree that Rentrix checks my masked Aadhaar to verify my identity"). The API accepts exactly 4 digits and refuses any 12-digit input with `422 aadhaar_full_number_refused`. An admin checks that the copy is masked and that the name and last 4 match, then marks it `VERIFIED` or `REJECTED`. An upload that shows the full number is rejected with `AADHAAR_NOT_MASKED`. Like every ID file, the masked copy is deleted as soon as the decision (approve or reject) is recorded (D-96). There is no `value_hmac` for Aadhaar, so Aadhaar cannot be used for duplicate detection; PAN and phone cover that.
- **D-104** **Provider later.** Checks sit behind `IdentityCheckProvider` (`start(method, input)`, `result(ref)`). v1 has only `AdminReviewProvider` (the admin queue) and the OTP flow. A later PR may add a licensed KYC provider (PAN verification against the Income Tax database, DigiLocker or Aadhaar offline e-KYC through a licensed agency) that returns only name match, last 4 and a reference id; it sets `checked_by=PROVIDER`, `provider`, `provider_ref`, and the stored data stays the same masked shape. No provider is called in tests.
- **D-105** *Changed in design v4 (D-117)*: in (1), (2) and (3) below read "a `VERIFIED` phone" for "an ID method"; PAN and Aadhaar decisions inside an approve call stay as written, but they no longer decide whether the approval is allowed. In (4) read "a user with a `VERIFIED` PAN or masked Aadhaar" for "a user with a verified phone": a phone alone gives no public badge (D-100). *Changed in design v4 (open by default)*: in (3) the gate needs only the `VERIFIED` phone, not the `APPROVED` application (D-132). **How identity feeds the approval flow (§7.3).** Identity checks are per user; approvals stay per role (D-89). (1) Submitting an application needs at least one ID method `VERIFIED` or `PENDING` (`422` with `missing: ["identity"]` otherwise). (2) An admin can approve only when at least one ID method is `VERIFIED`; deciding the application in the console also decides its `PENDING` PAN or Aadhaar check in the same transaction (both audited, D-87), and that check's ID file is deleted once the decision commits (D-96). An admin can also verify or reject a method on its own. (3) The gate D-91 requires the `APPROVED` application and a current `VERIFIED` ID method; if a method is revoked and none remains, gated writes return `403 id_verification_required` until the user verifies another method, without an admin re-approval. (4) Badges follow D-100 and do not wait for approval: a user with a verified phone shows "ID verified" while their application is still pending, but still cannot write until approved.
- **D-106** **Stored data, masked only.** Rentrix keeps per method: status, method, timestamps, who decided it and why, `last4` and (PAN and phone only) an HMAC for duplicate detection. The uploaded PAN card or masked Aadhaar file is held only until the admin decides, then deleted (D-96). Never stored, logged, emailed or returned: a full PAN, any Aadhaar number beyond its last 4 digits, an OTP code. The phone number itself is stored once, in `users.phone`, private (D-57). Masks in the UI: PAN `XXXXXX234F`, Aadhaar `XXXX XXXX 1234`, phone `+91 XXXXXX3210`. Masked values are visible only to the owner and admins; nobody else sees them, not even the other party of a tenancy. Request logs exclude `idNumber`, `panNumber`, `phone`, `code` and multipart bodies.

### 7.5 Onboarding and phone verification (design v4)

```
 Sign in (Google or dev login)
   │ POST /auth/google → needsOnboarding?
   ├── no ──▶ toast "Signed in as Asha Rao" ──▶ back to the page the user came from (?next=)   (D-119)
   │
   └── yes ─▶ [Role pick] /welcome  POST /me/onboarding
                 ▼
              [Account created] /welcome/created                                               (D-118)
              "Welcome to Doorway Living, your account is created" · name · email · role(s)
                 │ "Verify your phone number"            │ "Not now" ─▶ ?next= or role home
                 ▼
              Verification stepper /verify                                                     (D-120)
              1 Profile ─▶ 2 Phone (OTP) ─▶ 3 ID (optional) ─▶ 4 Rental details (optional) ─▶ Submit
                 required     required      PAN · Aadhaar        rental agreement       POST /me/verification/{role}/submit
                                            · LinkedIn           (§7.6)                         ▼
                                                                                    PENDING ─ admin ─▶ APPROVED (green tick)
   Trust writes open after step 2 (any one verified: phone, Aadhaar or LinkedIn, D-144); steps 3, 4 and Submit ("Request verification") are optional (D-133)
   Account menu: "Verify to post" · "Get verified: 2 of 4 steps" · "Waiting for review" · "Changes needed"   (D-121)
```

- **D-116** **Product name.** The name users see is **Doorway Living** (the production domain is `doorwayliving.in`): the web brand, page titles, the sign-in, welcome and verification copy. Code, packages, the repository, the API path and env names stay `rentrix`. The web keeps the name in `src/lib/brand.ts` (`BRAND`, which also fills `index.html` through `vite.config.ts`) and the server in `app.brand.name` (`BrandProperties`: emails, SMS code text, API messages, API docs title), so a later rename is one edit on each side.
- **D-117** *Superseded by D-144 (any one of phone, masked Aadhaar or LinkedIn; codes `missing: ["method"]` and `no_verified_method`).* **Phone OTP is required for every tenant and landlord.** It replaces "any one of Aadhaar, PAN or phone" (D-99): (1) submitting an application needs `PHONE_OTP` `VERIFIED` (`422 verification_incomplete` with `phone` in `missing`; a phone code is verified at once, so phone is never `PENDING`); (2) an admin can approve only when the applicant's phone is `VERIFIED` (`422 phone_not_verified`, replacing `no_verified_id`); (3) `ApprovalGuard` (D-91) needs a `VERIFIED` phone, else `403 id_verification_required` with `missing: ["phone"]` and the detail "Verify your phone number to continue."; a revoked phone (`PHONE_CHANGED`, D-101) closes trust writes until a new number is verified, with no new admin approval. *Changed in design v4 (open by default)*: the guard no longer also needs the `APPROVED` application and never returns `approval_required` (D-132). PAN and masked Aadhaar stay as they are (D-102, D-103) but are optional: they gate nothing and their only effect is the "ID verified" badge (D-100); an admin may still decide them inside an approve call (D-105). Users without a verified phone stay in the read-only state of D-91. When the API has no SMS sender (`prod` without `SMS_BACKEND`, D-101), the phone step says "Phone verification is not available yet" and nobody can post or submit. A preview deployment uses `SMS_BACKEND=admin-relay` (D-137) until a real provider is signed up; live mode (`PREVIEW_MODE` off, `SMS_BACKEND=http`) is a launch item. `IdentityService` names the two rules apart: `isPhoneVerified(user)` for the gate, `isIdVerified(user)` (PAN or Aadhaar `VERIFIED`) for the badge. *Built (B17)*: a new number is stored only by a confirmed code, so an unverified phone can never sit on an account: `PATCH /me` may clear `phone` (which revokes the check) or resend the same number, but answers `422` with the field error `phone_needs_otp` for a new one, and Settings links to the phone step instead of a phone field.
- **D-118** **Account created screen.** After `POST /me/onboarding` succeeds, the web opens `/welcome/created`: "Welcome to Doorway Living, your account is created", the Google photo, display name, email, the picked role badge(s), "Signed in with Google" (or "dev login"), and one next step: **"Verify your phone number"** → `/verify`, with "Not now" → `?next=` (or the role home). It reads only the onboarding response; no API change. The screen is shown once, from the onboarding navigation; opening `/welcome/created` later goes to `/verify` while verification is open, else to the role home.
- **D-119** **Returning sign-in.** When a sign-in (`/auth/google`, `/auth/dev-login`) returns `needsOnboarding: false`, the web shows a toast **"Signed in as <display name>"** (4 s, polite live region) and returns to the page the user came from. Every "Sign in" link and every guarded-route redirect carries `?next=<path and query>` of the current page (only same-app paths are kept, the existing `safeNext` rule against open redirects); with no `next` the user goes to the role home. A session restored on page load from the refresh cookie shows no toast. A user whose role pick is not done goes to `/welcome?next=…` as before (D-73). *Built (W8)*: a refused trust write (`403 id_verification_required`) links to `/verify/phone?next=<the page>`, and the tenancy form keeps what was typed in the tab (`sessionStorage`), so the user comes back to the form as they left it. The dev panel also offers "Sign in as a new user" (D-77).
- **D-120** **Onboarding continues into the verification stepper.** *Changed by D-144: step 2 is "Verify (any one)" (phone here, or Aadhaar / LinkedIn on step 3, which is now "Aadhaar, LinkedIn, PAN"), and the steps report `method`.* *Changed after the #44 re-test (captain 2026-11-23): the home city is optional on step 1, as the welcome screen says; `profile` is `DONE` once the display name is set.* The stepper at `/verify` has the same four steps for both roles; profile, phone and ID are per user, rental details and submit are per held role. Routes: `/verify` (status and the first open step), `/verify/profile`, `/verify/phone`, `/verify/id`, `/verify/rental`, `/verify/submit`. Steps: **1 Profile** (display name, home city; `PATCH /me`); **2 Phone** (`POST /me/identity/phone/start`, `/confirm`; required); **3 ID**, optional (PAN, masked Aadhaar from B12, LinkedIn from B14; "Skip"); **4 Rental details**, optional but always offered (tenant: the home you rent now or last rented; landlord: a home you rent out now; the rental agreement upload and fields of §7.6; "Skip"); **Submit**, labelled **"Request verification"** (D-133) (`POST /me/verification/{role}/submit` for each held role in `DRAFT` or `REJECTED`). Each step saves through its own endpoint, so leaving and coming back resumes at the first step that is still open. `GET /me/verification` reports the steps as `{profile, phone, identity, linkedin, rentalAgreement}`: `phone` is `DONE` or `MISSING`; `identity` (PAN or Aadhaar) is `DONE`, `PENDING`, `REJECTED` or `SKIPPED`; `rentalAgreement` is `DONE` (an agreement is `SUBMITTED` or `DECIDED`), `PENDING` (a `DRAFT` agreement waits for the uploader's confirmation) or `SKIPPED`, for both roles. Status pages (`PENDING`, `REJECTED` with "Fix and resubmit", `APPROVED`) and the not-approved banner stay as in design v2. *Changed in design v4 (open by default)*: the banner shows only while the phone is not verified ("Verify your phone to post"); the status pages say "Waiting for review" and that the user can already post (D-132, D-133).
- **D-121** **Progress in the account menu.** *Changed by D-144: "no verified phone" below means no verified posting method, and that row reads "Verify to post" with the hint "Any one: phone, Aadhaar or LinkedIn"; step 2 counts when `method` is `DONE`.* While any held role is not `APPROVED`, or the phone is not verified, the account chip shows a small status dot and the account menu (and the mobile menu sheet) shows one status row linking to `/verify`: `DRAFT` → **"Get verified: n of 4 steps"**, where a step counts when it is `DONE` (Profile, Phone, Rental details) or `DONE`/`PENDING` (ID: PAN, Aadhaar or LinkedIn); `PENDING` → **"Waiting for review"**; `REJECTED` → **"Changes needed"**; no verified phone (any status) → **"Verify your phone to post"**, which goes first. *Changed in design v4 (open by default)*: the row is optional in tone, because verification no longer gates writing (D-133); it read "Verification: n of 4 steps" and "Waiting for approval" before. With two roles in different states the row shows the one that needs the user first (no phone, then `REJECTED`, then `DRAFT`, then `PENDING`). The row disappears when every held role is `APPROVED` with a verified phone. Data: `GET /me/verification`, refetched after every step write and when the window regains focus.
- **D-122** **W8 moves forward.** The web stepper (PLAN W8) is built right after the verification core (B11, merged) and before the verification documents (B12): the PAN and masked-Aadhaar cards show "Coming soon" until B12 ships, LinkedIn works (B14), and the rental details step arrives with the rental-agreement PR (PLAN B18).

### 7.6 Rental agreement verification (design v4)

```
 upload (tenant or landlord)                        Rentrix checks (D-125)                     tier (D-126)
 ┌──────────────────────────┐   sha256, encrypt,   ┌──────────────────────────────────┐
 │ PDF / photos + consent   │──  mask Aadhaar  ──▶ │ 1 parties   2 property  3 money   │
 │ key fields (manual form) │   AgreementExtractor │ 4 dates     5 anchor    7 fraud   │──▶ Unverified
 └──────────────────────────┘     (MANUAL)         └──────────────────────────────────┘      │ both confirm
              │ uploader confirms fields                     ▲                               ▼   same sha256 (6)
              ▼                                              │ re-run on any change     "Agreement matched"
   other party opens the tenancy ─▶ sees fields + file ─▶ confirms sha256 (6)                │ admin ticks
                                                                                             ▼   anchor (8)
   admin console: anchor fields + link to SHCIL / IGR lookup ─▶ "Anchor verified" ──▶  "Agreement verified"
   file deleted 30 days after the later of the decision and the other party's confirm; fields, hash, anchor number and tier are kept (D-131)
```

- **D-123** **What it is for.** The rental agreement backs a tenancy (§6) with a document, checked in Rentrix's own system: the parties upload it and type its key fields, rules compare them with the tenancy, both parties confirm the same file, and an admin looks up its stamp or registration number on the government portal. It is optional; a tenancy without one keeps working as before (D-34). In the onboarding stepper (D-120, step 4) a user may upload the agreement of a home they rent now; it is linked to their application (`application_id`) so the admin sees it next to the approval, and checks that need a tenancy wait. When that tenancy is later recorded on Rentrix, either party can link the same agreement to it ("Use my uploaded agreement"); checks then run in full. It replaces the "Agreement checked" document badge of §6: `tenancies.evidence_level` becomes `DOCUMENT` when the tier is `VERIFIED`.
- **D-124** **City anchors.** The government-checkable fact on the document depends on the home's state (from `cities.state`, so a new city in a known state needs no code; any other state gets `OTHER_STAMP`):
  - **Maharashtra (Pune)** `MH_LEAVE_LICENCE`: Leave and Licence registration is compulsory (Maharashtra Rent Control Act s.55) and most are e-registered through IGR Maharashtra. Fields: registration document number, Sub-Registrar office, registration year and date, GRAS challan GRN, stamp duty paid (about 0.25 % of total rent plus deposit; the base varies), registration fee (₹1,000 urban). An agreement without registration is `NONE`: it can reach "Agreement matched" but never "Agreement verified".
  - **Karnataka (Bangalore)** `KA_ESTAMP`: usually an 11-month agreement on an SHCIL e-stamp certificate (number like `IN-KA…`, checkable at shcilestamp.com). Fields: certificate number, issue date, stamp amount, purchaser name, article. Registration is optional under 12 months.
  - `OTHER_STAMP`: stamp-paper number and amount, no format rule; `NONE`: no anchor fields.
- **D-125** **The checks.** Each check gives `PASS`, `WARN`, `FAIL` or `NOT_APPLICABLE`, a score 0–100 and reasons (a code and one sentence each), stored in `checks` and shown to the uploader (own side only), the other party and admins. They re-run whenever a field, a confirmation, the tenancy's rent records or the admin lookup changes. Names are compared after normalising (lower case; honorifics such as Mr, Mrs, Ms, Shri, Smt, Kum, Dr removed; punctuation removed; common transliteration pairs folded: `sh`/`s`, `v`/`w`, doubled vowels, `th`/`t`, `ph`/`f`); a single letter matches a word with that initial; score = matched words ÷ words of the longer name.
  1. **Party match**: landlord and tenant names against both accounts' display name and Google name (best of the two). ≥ 80 `PASS`, 50–79 `WARN`, < 50 `FAIL`. In onboarding (no tenancy yet) only the uploader's own side is compared.
  2. **Property match**: PIN code equal to the property's (40 points), area or locality name in the address (20), society (20) and tower or unit (20) against the location hierarchy (D-107) and `unit_label`. ≥ 70 `PASS`, 40–69 `WARN`, < 40 `FAIL`. In onboarding: the PIN code must belong to a live city's area, else `WARN`.
  3. **Money match**: monthly rent against the tenancy's confirmed rent record in force at the agreement's start month (the latest `CONFIRMED` record with `effective_from` on or before it; a renewal is compared with the rent of its own period, not the tenancy's first month): equal `PASS`, within 5 % `WARN`, more `FAIL`; with no confirmed record on or before that month, `WARN` (`NO_RENT_RECORD`); deposit against `deposit_inr` (mismatch `WARN`); escalation against the confirmed rent records inside the agreement's own window (start to end or start + term; off by more than 2 % `WARN`).
  4. **Dates**: start inside the tenancy period (from 31 days before the tenancy start to the tenancy end, or to today while current) `PASS`, else `FAIL`, so a renewal agreement of the same tenancy (D-24) passes; the agreement's end or term is checked against its own window, not the tenancy end: an agreement window that runs past an `ENDED` tenancy's end by more than 31 days `WARN`; Karnataka agreement without registration and a term over 12 months `WARN` (`KA_TERM_NEEDS_REGISTRATION`); stamp or registration date after the start date `WARN` (`ANCHOR_AFTER_START`).
  5. **Anchor sanity**: number format per kind (KA `IN-KA` + 14 digits + 1 letter; MH document number `<1–6 digits>/<4-digit year>` with the year equal to the registration year); a malformed registration document number or e-stamp certificate number is `FAIL` (`ANCHOR_FORMAT`), because it cannot be looked up. The MH GRAS GRN (`MH` + digits + an optional trailing letter, e.g. the `E` of e-payments) only informs the admin: a GRN that does not match the format is `WARN` (`GRN_FORMAT`), never `FAIL`. Stamp amount against an expected value (MH 0.25 % of rent × months + deposit; KA 0.5 % of average annual rent + deposit), outside half to double of it `WARN` (`STAMP_AMOUNT_UNUSUAL`; the formulas are approximate and only ever warn). KA purchaser not matching either party (check 1 rules) `WARN`. `NONE` is `NOT_APPLICABLE`.
  6. **Both sides**: `PASS` when the uploader and the other party of the tenancy have both confirmed the stored `sha256` (D-128) while the file was still stored; `WARN` ("waiting for the other party") until then; `NOT_APPLICABLE` without a tenancy. A file deleted before the other party confirmed can never reach `PASS`: the confirm is refused (D-128) and the parties re-upload.
  7. **Fraud signals** (D-129): the same `sha256` or anchor number on an agreement of another tenancy `FAIL` (`AGREEMENT_REUSED`); PDF edited by an image editor after the anchor date `WARN` (`PDF_EDITED`).
  8. **Government lookup** (D-130): `PASS` when an admin recorded `VERIFIED`, `FAIL` on `NOT_FOUND`, otherwise `NOT_APPLICABLE`.
- **D-126** **Tiers and badges.** *Changed by rule B (D-149): check 6 ("both sides") is optional; it no longer gates a tier and no longer feeds any badge; it stays an informational check the admin sees on the tenancy card (D-151). The "Owner confirmed" / "Tenant confirmed" badge comes only from the tenancy's `confirmed_at`.* **"Agreement verified"** = no check `FAIL`s, check 6 `PASS` and check 8 `PASS` (anchor kind other than `NONE`). **"Agreement matched"** = no check `FAIL`s and check 6 `PASS`. Otherwise **Unverified**. `WARN`s never lower the tier; the admin sees them. The tier is shown as a badge on the tenancy, on each rent-history row from that tenancy (`agreementTier` on `GET /properties/{id}/rent-history` rows) and on review author lines ("verified tenancy · Agreement verified"). "Agreement verified" uses the shield-check icon in teal, "Agreement matched" the document icon in info blue; Unverified shows no public badge, and "Agreement not checked" only to the two parties. Badges are yes/no: the public never sees the agreement, its fields or its check reasons (D-57). *Design v4 (open by default)*: the agreement tier is an extra signal shown next to the tenancy's green tick (D-134); neither replaces the other.
- **D-127** **Extraction behind an interface.** `AgreementExtractor.extract(file, typedFields) → ExtractedAgreement` (fields plus the extractor name). v1 has one implementation, `ManualAgreementExtractor`: the uploader types the key fields in a form next to the file preview, the API stores them as the extracted fields, and an admin compares them with the file. A later `VisionAgreementExtractor` (OCR plus LLM vision) may pre-fill the form; it is off by default and is not built until the captain approves its cost. `AGREEMENT_EXTRACTOR=manual` is the only value accepted now. Whatever extracts, the uploader sees the fields and confirms or corrects them before the agreement is `SUBMITTED`.
- **D-128** **Both sides confirm the same document.** The upload records the file's SHA-256. The uploader confirms the fields (`POST /me/agreements/{id}/confirm`). The other party of the tenancy opens it from the tenancy, sees the fields and the file, and confirms with the hash they were shown (`POST /tenancies/{id}/agreement/confirm {sha256}`); a different hash is `409 agreement_changed`. It reuses the tenancy's two-party rule (D-34): only the two parties of an `ACTIVE` or `ENDED` tenancy confirm, and confirming is a trust write behind the gate (D-91; *changed in design v4 (open by default)*: a verified phone, D-132). Every write on a tenancy-linked agreement (upload with `tenancyId`, link, uploader confirm, counterparty confirm or dispute) needs the caller to be a party of that `ACTIVE` or `ENDED` tenancy, else `404` (no existence leak), and a verified phone, else `403 id_verification_required`. Uploading and confirming in the onboarding stepper (no tenancy) is not gated: the agreement only informs the admin. Confirming a hash whose file is already deleted (D-131) is refused with `409 file_deleted`; the parties re-upload. The other party may instead dispute it (`/dispute {reason}`), which keeps the tier Unverified and puts it in the admin queue. Replacing the file (a new upload) clears both confirmations and the admin lookup; the old row stays `REPLACED`.
- **D-129** **Fraud signals.** On every check run: the same `sha256` or the same `anchor_number_key` on an agreement linked to another tenancy (another property or another landlord and tenant pair) fails check 7 and flags both agreements `AGREEMENT_REUSED` for admins; a renewal of the same pair and home is not reuse. For PDFs, the API reads the document information at upload (`/Producer`, `/Creator`, `/ModDate`, and the XMP packet when it is uncompressed): a producer naming an image editor (Photoshop, GIMP, Canva, PicsArt, Pixlr, Snapseed, Illustrator) or a modification date more than one day after the anchor date sets `PDF_EDITED`. Name and money mismatches show in checks 1 and 3. Nothing is rejected automatically; signals inform the admin.
- **D-130** **Government lookup is a manual admin step.** The admin console shows the anchor fields with a link to the official lookup page (SHCIL e-stamp verification for Karnataka, the IGR Maharashtra document search for Maharashtra). The admin fills in the portal's captcha in their own browser and records `VERIFIED` ("Anchor verified") or `NOT_FOUND` with a note (`POST /admin/agreements/{id}/anchor`), audited as `VERIFY_AGREEMENT_ANCHOR` / `REJECT_AGREEMENT_ANCHOR` (D-87), and as an `AGREEMENT_CHECKED` event in `verification_events` when the agreement is linked to an application. Rentrix never scrapes, automates or calls these portals.
- **D-131** **Privacy, consent and storage.** Maharashtra agreements can carry Aadhaar numbers, photos and thumbprints, so: (1) every typed field is scanned at intake and any 12-digit Aadhaar-like number (`\d{4}[ -]?\d{4}[ -]?\d{4}`) is replaced with `XXXX XXXX <last 4>` before it is stored or logged (flag `AADHAAR_MASKED_ON_INTAKE`); (2) the manual extractor cannot redact the file itself, so the upload screen asks the user to cover Aadhaar numbers before scanning, and the file is stored **encrypted** (AES-256-GCM, a random IV per file, key `AGREEMENT_FILE_KEY` from env, required outside `dev`/`test`) in PRIVATE storage through `ImageStorage` (D-66); (3) only the uploader, the other party of the linked tenancy and admins can open it, through 5-minute signed URLs (admin views audited `VIEW_DOCUMENT`, D-95); (4) the file is deleted `AGREEMENT_FILE_RETENTION_DAYS` (**30**) days after the **later** of (a) the admin decision (the lookup result, or the decision on the linked application) and (b) the other party's confirmation of that document (D-128); if no other party is linked within 90 days of upload, the file is deleted 90 days after upload anyway. `purge_after` is recomputed whenever (a) or (b) is recorded. A deleted file cannot be confirmed any more (`409 file_deleted`, D-128), so check 6 never passes on it. Kept after deletion: the masked fields, `sha256`, anchor number, check results and tier. Consent is an explicit checkbox before upload: "I agree that Doorway Living stores this agreement encrypted, shows it only to the other party of this tenancy and to admins, uses it to verify this tenancy, and deletes the file 30 days after the check and the other party's confirmation." The uploader can delete the agreement until it is decided (the row and file go); after that only the file can be deleted early, and the tier stays. Account deletion (D-58) deletes every file at once. Files: PDF, JPEG or PNG, ≤ 15 MB, one file per agreement (several photos are combined into one PDF by the user's phone); the full Aadhaar or PAN number is never typed into any field.

### 7.7 Open by default, approval as a green tick (design v4)

Captain's decision of 2026-11-22 16:45. It replaces the approval gate of D-91 with an approval badge. Text it changes elsewhere is marked *Changed in design v4 (open by default)*.

```
 Google sign-in ─▶ role pick ─▶ phone OTP (required) ─▶ can post at once: property, tenancy period, rent records, reviews, enquiries
                                                          │  everything is visible at once, marked "Not verified yet"
                                                          ▼
                              admin approvals queue (W9, mobile, 1-2 taps)  ── approve ─▶ green tick "Verified by Doorway Living"
                              users · properties · tenancies · reviews · agreements  ── reject ─▶ stays visible, no tick
 optional: "Request verification" (ID, rental agreement) gives the admin more to go on (D-133)
```

- **D-132** **Open by default.** *Changed by D-144: "a `VERIFIED` phone" below now reads "any one verified method" (phone, masked Aadhaar or LinkedIn), and the guard answers `missing: ["method"]`.* A user signed in with Google who has a `VERIFIED` phone (D-117) can add a property, add a tenancy period as landlord or tenant, add and confirm rent records, write reviews and responses, and send enquiries. Nothing waits for an admin: every item is visible at once, marked **"Not verified yet"** until an admin gives it the green tick (D-134). `ApprovalGuard.require(user, role)` checks only the role and the verified phone; without the phone it returns `403 id_verification_required` with `missing: ["phone"]`, and `403 approval_required` is no longer returned. Counterparty confirmation of a tenancy or a rent record stays a trust signal shown on the item ("Confirmed by both"), not a gate (D-34, D-25). Abuse controls stay: phone OTP to post, the rate limits (D-32, D-45, and D-35's limit of 5 added tenancy periods per user per 24 h and 20 per 30 days, `429` with `Retry-After`), reports (D-49), admin hide and void (D-36, D-51) and suspension.
- **D-133** **Verification is optional for users.** *Changed by D-144: the required step is "Verify (any one)" and the row reads "Verify to post".* A user may request verification by uploading a rental agreement or an ID, or skip it. The W8 stepper stays: the phone step is required to post, the other steps are optional, and Submit reads **"Request verification"**. The account-menu row (D-121) stays but reads as optional: "Get verified: 2 of 4 steps", "Waiting for review", "Changes needed", and "Verify your phone to post" while the phone is not verified.
- **D-134** **Green tick.** *Changed after the #44 re-test (captain 2026-11-23): a person's tick is public on property pages. `landlord.verified` on the property detail is the landlord's `LANDLORD` application `APPROVED` (false for an unclaimed home); `author.verified` on a review is the author's application for the role they wrote as. The web shows "Verified landlord" / "Verified tenant", else "Landlord not verified yet" / "Tenant not verified yet", plus the public "ID verified" and "LinkedIn verified" badges.* An admin reviews items in the admin console (D-136) and approves or rejects them. Approve gives the badge **"Verified by Doorway Living"** (green tick) on that user (per role application), property, tenancy or review. Reject leaves the item visible as unverified with no tick and a reason the owner sees; items are hidden only for abuse or reports (D-51). Each item carries a verification state `UNVERIFIED`, `VERIFIED` or `REJECTED` with `decided_by`, `decided_at` and a reason: users through `verification_applications` (`PENDING`/`DRAFT` = unverified, `APPROVED` = verified, `REJECTED`); properties through a new `verification_status` plus the V6 columns `review_reason`, `reviewed_by`, `reviewed_at`; tenancies and reviews through new nullable columns `verification_status`, `verification_decided_by`, `verification_decided_at`, `verification_reason` (verification-state migration, [`../database/schema.md`](../database/schema.md) §7). A null state reads as `UNVERIFIED`. An edit of a review's text or ratings, or a change of a tenancy's dates or rent, resets its state to `UNVERIFIED`. The rental-agreement tier (D-126) stays an extra signal next to the tick. Every decision is audited (D-87).
- **D-135** **Ratings count green-tick reviews.** *Changed by rule B (D-150): a review of a team-verified tenancy can get its tick without the owner; see the open R8 point in §7.8.* The headline average and its Bayesian rank (D-52, D-53, D-111) use only reviews with `verification_status = VERIFIED`. Unverified reviews are shown separately, with their own count and average, labelled **"Not verified yet"** (e.g. "4.2 ★ from 3 verified reviews · 2 not verified yet, 3.5 ★"). `rating_aggregates` keeps one row per subject for verified reviews; the unverified count and average are computed at read time over the same subject's counted reviews (bounded per subject). A review's tick change recomputes its subject rows in the same transaction (D-33). `REJECTED` reviews count in neither, but stay visible as unverified. *Built (B17)*: the headline mode is the setting `app.ratings.headline` (`RATINGS_HEADLINE`): `all` (the default until W9 ships the admin tick) counts every published, counted review as before; `verified` counts only `VERIFIED` reviews as above. `V7__verification_state.sql` adds the columns and indexes only and leaves existing `rating_aggregates` rows as they are. Switching the setting needs the aggregates rebuilt, which the nightly repair (D-33, `RatingRecomputer.recomputeAll`) does; until it runs, rows rebuilt by review changes follow the new mode and the others the old one. The separate unverified count and average are not built yet.
- **D-136** **Mobile-first approvals queue.** *Changed by rule B (D-151): the queue covers user, tenancy and property / ownership verification as separate cards.* The admin console approvals queue (PLAN B13 API, W9 web) is on the critical path. It lists new users, properties, tenancies, reviews and uploaded agreements, oldest first, as cards with a "Not verified yet" badge, the key facts, flags and the item's agreement tier. It works on a 390 px phone: approve is one tap, reject is two (reason code, then confirm). `GET /admin/queue` feeds it (§12.2). *Changed in admin separation (captain 2026-11-23, built in W9 PR 2)*: the queue is the admin host's `/approvals` and lists **newest first**. Besides users, homes, tenancies and reviews it lists each `PENDING` PAN, masked Aadhaar or LinkedIn check on its own, so a user's sole check can be verified or rejected with no application (before, such a check stayed `PENDING` forever), and each city someone typed that is not live yet (D-143; approve sets it live). Open reports have their own page `/reports` (Dismiss, or Hide for a reported review). Reject is two taps: Reject, then a reason, which sends it; the note is optional (a user's rejection sends the reason's words when the note is empty, because B11 needs one). Hide (abuse only, D-51) is offered on reviews. The same answer twice is `409 invalid_transition`. Agreement cards join when B18 builds agreements. One read returns at most 50 cards per type; the counts are full.
- **D-137** **Preview-only SMS sender.** No SMS provider is signed up yet, so a preview deployment uses `SMS_BACKEND=admin-relay`: `AdminRelaySmsSender` sends nothing and keeps the pending code for admins. The admin console shows it next to the user with the masked phone (`relayOtp` on the queue card, `+91 XXXXXX3210`), so an admin can pass it to a trusted first user; in `dev` it is also logged. The code is held only in memory for the challenge's 10 minutes (the database keeps only its HMAC, D-101) and is never in an API response other than the admin queue. The API refuses to start with `admin-relay` under the `prod` profile unless `PREVIEW_MODE=true`, and every start with it logs one `WARN` naming `admin-relay`. Live mode is `PREVIEW_MODE` off with `SMS_BACKEND=http` configured; it is a launch item (PLAN). *Built (B17)*: until the B13 queue ships, admins read the pending codes at `GET /admin/sms-relay` (admins only: user, display name, email, masked phone, code, sent and expiry times; `404` when the sender is not `admin-relay`); B13 moves them to `relayOtp` on the queue card and drops the endpoint. With `ADMIN_RELAY_TELEGRAM_BOT_TOKEN` and `ADMIN_RELAY_TELEGRAM_CHAT_IDS` set, each held code is also pushed to those Telegram chats in the background; a push failure logs one warning and never breaks the OTP. Codes are lost on restart; the user asks for a new one.
- **D-138** **Verify with Truecaller** (Android mobile web, the first phone method). The phone step offers "Verify with Truecaller" before the SMS code, on Android browsers only (Truecaller's mobile-web flow does not run on iOS or desktop). `POST /me/identity/truecaller/start` makes a request nonce (32 URL-safe characters, bound to the user, single use, 10 minutes) and returns the `truecallersdk://truesdk/web_verify` deep link with `partnerKey` = `TRUECALLER_APP_KEY` and `partnerName` = `TRUECALLER_APP_NAME`. Truecaller posts to `POST /auth/truecaller/callback` (anonymous, answered `200` at once, as Truecaller needs a 2xx within 3 s): `{requestId, status: "flow_invoked"}` when its sheet opened, `{requestId, status: "user_rejected"}` when the user declined, `{requestId, accessToken, endpoint}` when they agreed. The API then fetches the profile in the background from `endpoint` with `Authorization: Bearer <accessToken>`, only when it is `https://profile…truecaller.com` on the default port, reads `phoneNumbers[0]`, and accepts only an Indian mobile number. It stores method `TRUECALLER` `VERIFIED` (`checked_by=PROVIDER`, `provider=TRUECALLER`) with `last4`, `value_hmac` and `verified_at` only: no token, no name, no other profile field. `TRUECALLER` counts as the verified phone exactly like `PHONE_OTP` (`IdentityService.PHONE_METHODS`, D-117), sets `users.phone`, revokes a verified phone of the other method with a different number, is revoked by `PATCH /me` like `PHONE_OTP` (D-101), and joins duplicate-phone detection. Each of the user's applications gets a `PHONE_VERIFIED` event with code `TRUECALLER` (a confirmed SMS code writes the same event with code `SMS_OTP`). The web polls `GET /me/identity/truecaller/{requestId}` every 2 s; declined, no Truecaller app (the page kept focus and no `flow_invoked` came), failed or no answer in about 2.5 minutes falls back to the SMS code. Requests live in API memory (one instance; a restart drops requests in flight and the user taps again). `TRUECALLER_APP_KEY` and `TRUECALLER_APP_NAME` are env only and never logged; without a key (or without `ID_HASH_KEY`) the button is hidden. Dev and test use `FakeTruecallerVerifier` (`TRUECALLER_FAKE=true`, refused in `prod`): an access token `fake:<number>` reads as that verified number. **What the captain registers** in the Truecaller developer portal, one *Web* app per deployment: app name "Doorway Living"; domains `doorwayliving.in` (LIVE) and `doorway.129-225-77-1.sslip.io` (DEV); callback URLs `https://doorwayliving.in/api/v1/auth/truecaller/callback` (LIVE) and `https://doorway.129-225-77-1.sslip.io/api/v1/auth/truecaller/callback` (DEV). Each app's key goes in that deployment's `TRUECALLER_APP_KEY`.

#### 7.7 Add your home (captain 2026-11-22 21:38 and 22:22; built in the journey PR)

```
 new user ─▶ Google sign-in ─▶ role pick ─▶ "Account created" ─▶ verify (gate check, D-117 / successor)
                                                                     │
           header "+ Add your home" · empty states "Can't find your home? Add it"
                                                                     ▼
 /homes/new  1 Where is it? (city > area > society > tower > wing > flat no. + PIN; new names are proposals, D-109)
             │   GET /me/homes/matches (PIN + society + flat) ─▶ "Is it this one?" ─▶ yes: that home
             2 I rent(ed) it / I own it
             3 Tenancy: moved in, moved out or "I still live here", rent, deposit (renters)
             4 Owner name + email / phone, private (renters)
             ▼
 POST /me/homes ─▶ property PUBLISHED (unclaimed account as landlord, "Added by a tenant · not verified yet")
                 + tenancy ACTIVE / ENDED at once (one-sided) + first rent record PROPOSED
             ▼
 "Your home is added" ─▶ /tenancies/{id}/review ─▶ "You submitted this review" ─▶ /p/{id}#reviews (tick state)
```

- **D-139** **Unclaimed homes.** A signed-in user who passes the posting gate (D-132) adds the home they rent or rented with `POST /me/homes`. A home whose landlord is not on the platform is held by one system account, `unclaimed-homes@doorwayliving.invalid` (created at startup, never signs in, shown as **"Not on Doorway Living yet"** with no profile link), because `properties.landlord_id` and `tenancies.landlord_id` stay `NOT NULL` (SQLite cannot drop it without rebuilding the tables). The property records `added_by` (the tenant) and is `PUBLISHED` at once, `UNVERIFIED`, with the badge **"Added by a tenant · not verified yet"**; the owner claims it later through an admin (not built yet). On an unclaimed home: no landlord review (`422 landlord_not_on_platform`) and no landlord review task, no landlord emails, the pending-request cap does not apply. The owner's name, email and phone as the tenant typed them are stored on the property (`owner_name`, `owner_email`, `owner_phone`) for the claim and a later invite; nothing is sent yet. Before a new home is created, `GET /me/homes/matches?city&pincode&society|societyName&unit` lists published homes with the same PIN code and society and the form asks **"Is it this one?"**. A home whose flat number is `OPEN` must also have the same flat number (letters and digits only, "flat" ignored); a home whose flat number is `MASKED` matches on PIN code and society alone, any flat, so its flat number can never be probed, and the card never carries a flat number. "I own it" creates the home with the caller as landlord and gives them the `LANDLORD` role (fresh `accessToken` in the answer); an owner cannot pick an existing home (`422 claim_not_available`). *Changed 2026-11-23 (captain 09:55, 11:35)*: every optional field may stay blank (owner name, email, phone; the rent); a home not in a society (standalone) carries its `building_name`, required for an apartment (`422` on `home.buildingName`) and optional for other types; the form shows each API field error by its field, on its step, never the generic "The request has invalid fields." Each step is checked on its own Next. Step 1 calls `GET /me/homes/address-check?city&pincode&area` (*firstmate 2026-11-23 14:50*): a PIN code outside the city's prefixes (D-84) is a **hard block** (`pincodeError`, inline under the PIN field); a PIN code the picked area does not list is only a **warning** (`pincodeWarning`, "560008 is usually not in HSR Layout; check the PIN") with **Keep this PIN**, because PIN codes cross area borders and the area PIN data is incomplete, so `POST /me/homes` accepts it (landlord listings keep D-108's `422 pincode_not_in_area`). Picking an area fills in its first PIN code and lists the others to pick, unless the user typed a PIN. A field error clears as soon as its field changes; if the final save still rejects an earlier step's field, the form returns to that step and focuses the field. *Changed 2026-11-23 (captain 14:53)*: after "Save my home" the flow goes on to **"Home added. N optional steps left"** with the skippable step **Rate and review this home and landlord** (the review form; done once `TenancyView.reviewed`, set only for the tenant side). Below it, **Upload your rental agreement** is a "Coming soon" card, one swappable web component, until the agreement upload (PR #62) replaces it; it is not counted, so a tenancy whose only open item is this card shows no "steps left" prompt. When it is counted again it is done at `evidence_level` `DOCUMENT` or when the team verified the tenancy (`verification_status = VERIFIED`). The steps stay on My tenancies and the home's page for the tenant until done.
- **D-140** **Per-field visibility switch.** Sensitive fields carry a `FieldVisibility` (`OPEN`, `MASKED`, `PRIVATE`) stored on the row, so masking can be switched on later by data and code, never a migration: `properties.unit_label_visibility` (default `MASKED`, i.e. D-57 for listings; `OPEN` for homes a tenant added through D-139, as the captain asked "open for everyone for now"; a home its owner adds with "I own it" stays `MASKED` like any listing; `PropertyDetail.unitLabelPublic` says whether the flat number is `OPEN`), `properties.owner_contact_visibility` (always `PRIVATE`: only the tenant who entered it and admins see `ownerContact`) and `tenancies.rent_visibility` (default `OPEN`; a `MASKED` rent shows its months in the rent history without the amount). Migration `V9__home_records.sql` (+ `V9_1` MySQL FK for `added_by`).
- **D-141** **A verified posting method replaces the first-review wait.** The D-45 rule "first review needs an account at least 7 days old" is dropped: every writer already passes the posting gate (D-132), so a brand-new user can review their home right after adding it. The email check and the 10-per-24 h volume guard stay.
- **D-142** **A tenant's own record never waits for the owner.** *Changed by rule B (D-149): a team-`VERIFIED` tenancy holds its dates like a confirmed one; the whole-home overlap check (D-23) and the closing of rent-history rows count tenancies with `confirmedAt` set, landlord-started, or `verification_status = VERIFIED`; on a whole home the admin tick runs the same overlap check and is refused with `409 tenancy_overlap` on a clash (D-149); the "Owner confirmed" badge comes only from `confirmedAt`.* A tenancy the tenant records through "Add your home" (`TenancyService.recordOwn`) is `ACTIVE`, or `ENDED` with an end date, at once, on any home, without `confirmedAt` (one-sided); its first rent record is `PROPOSED` and shows in the rent history with `confirmed: false` ("added by the tenant · not verified yet"). The team verifies tenancies from the rental agreement (B18); the owner's confirmation becomes an optional "Owner confirmed" badge (not built yet). A tenancy both sides confirmed keeps its proposals hidden until confirmed (D-25). On a home with a real landlord a one-sided record holds no dates against others: the whole-home overlap check (D-23) at create and confirm counts only tenancies both sides confirmed (`confirmedAt` set) or the landlord started, so the landlord can still record and confirm their tenants for the same dates; the tenant's own overlap check stays. In the rent history of a whole home only the rows of those tenancies are closed against each other; one-sided rows are listed as they are, next to them, and never shorten them. The landlord gets one email, "A tenant recorded a tenancy on your home". On an unclaimed home (D-139) one-sided records still hold dates against each other, and no email is sent to the unclaimed account.
- **D-143** **Any city.** "Add your home" offers "Another city…": the user types the city, its state and the locality. `POST /me/homes` with `newCity: {name, state}` finds a city row with that name or alias (case and spacing ignored), else creates one **pending**: `live = false`, code from the first three letters of the name (made unique with a number), no PIN prefixes. A pending city is not in `GET /cities` and has no areas, so its homes are placed by free-text locality only (a `location` is `422`); the home and tenancy are saved at once. An admin approves the city by setting `live` (admin screen later, W9); it then shows in the pickers and its homes in search.
- **D-144** **Verify with any one method to post** (captain 2026-11-22 23:25; supersedes the phone-only rule of D-117, D-132 and D-133). To post (add a home, a tenancy, rent, reviews and responses, enquiries) a user needs **any one** `VERIFIED` posting method: the phone (`PHONE_OTP` or `TRUECALLER`), masked `AADHAAR` or `LINKEDIN` (`IdentityService.POSTING_METHODS`, `hasPostingMethod`). PAN is not a posting method; it only gives the "ID verified" badge (D-100). The methods a user did not need stay optional extra badges. (1) `ApprovalGuard` needs a posting method, else `403 id_verification_required` with `missing: ["method"]` and the detail "Verify with any one: phone, Aadhaar or LinkedIn to continue."; a revoked phone (D-101) closes writes only when no other method is verified. (2) Submitting an application needs one verified or waiting for an admin check (`422 verification_incomplete` with `method` in `missing` only when none of them is added), so a user with only a `PENDING` Aadhaar or LinkedIn can ask the team to check it. (3) An admin approves only an applicant with one `VERIFIED` after the identity results of the call apply (`422 no_verified_method`, replacing `phone_not_verified`); the approve call decides `PENDING` Aadhaar, PAN and LinkedIn checks (`IdentityService.ADMIN_CHECKED_ID_METHODS`, D-105), so approving with that Aadhaar or LinkedIn `VERIFIED` opens posting, and rejecting the only method (e.g. code `OTHER`) is refused and keeps nothing. (4) `GET /me/verification` steps gain `method`: `DONE` when a posting method is verified, `PENDING` while only an admin-checked one (Aadhaar, LinkedIn) waits, else `MISSING`; `phone` keeps the phone alone. (5) The web stepper's step 2 is **"Verify (any one)"** at `/verify/phone`: "Pick any one: phone, Aadhaar or LinkedIn", the phone form, and a link to step 3, now **"Aadhaar, LinkedIn, PAN"** (optional extra badges, or the one method when the phone is skipped). The banner, the gate prompt and the account-menu row read **"Verify to post"** (any one of phone, Aadhaar or LinkedIn). (6) The **green tick** stays the team's approval (D-134): "Verified by Doorway Living" after an ID check; one verified method lets a user post, the tick is the extra trust layer. Masked Aadhaar waits for the document upload (PLAN B12) and the admin decisions wait for the admin console, so until those ship the phone is the method most users can finish alone.


### 7.8 Team-verified tenancies (rule B, design only)

Captain's decision of 2026-11-22 23:30. It supersedes "a tenancy counts only when both sides confirm" (D-34) and the "Confirmed by both" signal of design v4. Nothing here is built yet; PLAN rows B20 and W12 build it. Text it changes elsewhere is marked *Changed by rule B (D-147)*.

```
 tenant adds the tenancy (D-142)          landlord adds it (D-34)            either party
   ACTIVE / ENDED at once, "Not verified yet" ◀───────────────────────────────┐
        │ uploads the rental agreement (D-123), types its fields                │
        ▼                                                                       │
 checks D-125 run (parties, property, money, dates, anchor, fraud)              │ other party confirms (optional)
        │                                                                       ▼
        ▼                                                  "Owner confirmed" / "Tenant confirmed" badge
 admin queue: tenancy + agreement card (D-151) ── approve ─▶ tenancy tick "Verified by Doorway Living"
                                               ── reject ──▶ stays visible, no tick, reason to the uploader
 rent history rows and reviews of a ticked tenancy count as verified, even if the owner never joined (D-150)

 three separate ticks, decided separately:   user (ID method + checks, D-134)
                                              tenancy (rental agreement, D-149)
                                              property / ownership (ownership papers, D-152)
```

- **D-147** **The team verifies tenancies from the rental agreement.** A tenancy no longer needs the landlord's approval to become verified. The Doorway Living team verifies it from the rental agreement (§7.6) and the agreement checks (D-125): party, property, rent and date match, and the stamp or registration anchor (D-130). The other party's confirmation becomes an optional extra signal, a badge that names who confirmed: **"Owner confirmed"** when the landlord confirmed a tenant-added tenancy or record, **"Tenant confirmed"** when the tenant confirmed a landlord-added one. It is never a requirement for anything: not for the tick, not for reviews, not for rent history. A tenant's tenancy on a home whose owner is not on the platform (D-139) can be fully verified.
- **D-148** **Three separate green ticks.** The team verifies, each on its own and each with its own `verification_status` (`UNVERIFIED`, `VERIFIED`, `REJECTED`), decider, time and reason: (1) a **user**, per role application (tenant or owner), from their ID method and checks (D-134, unchanged); (2) a **tenancy**, from its rental agreement (D-149); (3) a **property / ownership**, from the landlord's ownership papers or an agreement that names them as owner (D-152). No tick implies another: a verified tenancy does not verify the landlord as a person or the home's ownership, and a verified user does not verify their tenancies. Each shows **"Verified by Doorway Living"** on its own item.
- **D-149** **Tenancy states without a blocking confirmation.** The lifecycle has no state that waits for the other side. A new tenancy is `ACTIVE` (or `ENDED` with a past end date) at once, whoever adds it (already true for new rows, D-34 v4 note, D-142). `PENDING_CONFIRMATION`, `EXPIRED` and `DECLINED` are kept in the enum only for old rows; the build migration (B20) moves old `PENDING_CONFIRMATION` rows to `ACTIVE`/`ENDED` by their dates. The verification axis is separate from the state: `UNVERIFIED` → `VERIFIED` or `REJECTED` by an admin only. The other party's confirmation stays today's optional `confirmed_at` flag; it no longer marks a state change and is shown as "Owner confirmed" when the landlord confirmed a tenant-added tenancy, "Tenant confirmed" when the tenant confirmed a landlord-added one. The other party's decline stays a dispute that puts the tenancy in the admin queue without a state change (D-34 v4 note). The initiator may still cancel (`CANCELLED`) while no review exists; admin void (`VOIDED`, D-36) is unchanged. **Tick criteria (admin guidance, not automatic):** the agreement has no `FAIL` check, the party and property checks pass, and the anchor is `VERIFIED` where the state has one (D-124); check 6 ("both sides", D-125) becomes optional and informational only: it gates no tier and does not feed the "Owner confirmed" / "Tenant confirmed" badge, which comes only from `confirmed_at`; the admin sees it on the tenancy card. A team-`VERIFIED` tenancy holds its dates like a confirmed one: the whole-home overlap check (D-23) and the closing of rent-history rows count tenancies with `confirmed_at` set, landlord-started, or `verification_status = VERIFIED` (D-142 note). On a whole home the admin tick runs the D-23 overlap check against those date-holding tenancies: if ticking would make the tenancy overlap one of them, approve is refused with `409 tenancy_overlap` naming the clash (its period and status); the admin first rejects or voids one of the two, or the dates are corrected. `ROOM` and `PG` tenancies are unaffected. An agreement with anchor kind `NONE` can still be ticked when the other checks pass and the admin is satisfied; the card shows that the anchor is missing.
- **D-150** **What counts as verified.** (1) **Rent history**: a row is `verified` when its tenancy has the tick, whether or not the other party confirmed the record; `confirmed` stays a separate, optional flag, shown as "Owner confirmed" or "Tenant confirmed" by who confirmed the record (D-25 v4 note). Rent figures (D-26) keep today's rules; only the per-row `verified` flag follows the tenancy tick. (2) **Review eligibility**: unchanged: a party of an `ACTIVE` or `ENDED` tenancy can review at once (D-37). A review's own tick (D-134) is given when its tenancy is team-verified and the review passes moderation; approving a tenancy offers "Also verify its reviews" in the same card (one tap), so a verified tenancy's reviews count in the verified headline (D-135) even when the owner never joined. (3) The author line "verified tenancy" (D-28) means a team-verified tenancy; an unverified one reads "tenancy not verified yet".
- **D-151** **The admin queue covers users, tenancies and properties.** `GET /admin/queue` (D-136) lists three verification kinds next to reviews: **user** cards (role, ID methods and their states, flags such as `DUPLICATE_PHONE`), **tenancy** cards (home, period, rent, the agreement's tier and its check results, check 6 shown as information only, on a whole home any overlap with a date-holding tenancy shown up front (its period and status; approve is refused with `409 tenancy_overlap` until it is resolved, D-149), whether the other party confirmed ("Owner confirmed" / "Tenant confirmed"), "owner not on Doorway Living yet") and **property / ownership** cards (home, the landlord, the ownership papers or the agreement naming the owner). Each card approves in one tap and rejects in two (reason code, then confirm), on a 390 px phone. Each decision is audited in `moderation_actions` with its own action type (`VERIFY_TENANCY`, `REJECT_TENANCY`, `VERIFY_PROPERTY`, `REJECT_PROPERTY`; users keep `APPROVE_APPLICATION` / `REJECT_APPLICATION`).
- **D-152** **Property / ownership tick.** A landlord proves ownership by uploading one ownership paper (sale deed or allotment letter, property-tax receipt, or an electricity bill in their name) or by linking a rental agreement that names them as owner. The file follows the agreement rules: PRIVATE, encrypted, signed URLs, deleted 30 days after the decision (D-131). An admin compares the owner's name (D-125 check 1 rules) and the address (check 2 rules) and gives the property tick. An unclaimed home (D-139) cannot get the ownership tick until its owner claims it, but its tenancies can still be verified (D-147).
- **D-153** **Copy.** Every "both sides confirmed" and "Confirmed by both" wording changes: the tenancy, rent-history rows, the property page footer and review author lines say **"Verified by Doorway Living"** for a ticked item and **"Owner confirmed"** or **"Tenant confirmed"** for the other party's optional confirmation, by who confirmed; "Nothing is public until both sides confirm" (tenancy request page) and "The landlord confirms within 14 days, or the request expires" go, because nothing waits for the other side. The landing keeps "Green tick = approved by the Doorway Living team after an ID check" for users and adds "Tenancies are verified from the rental agreement".

#### Open points for the captain (recorded, not decided)

- **R7 vs R11.** R7 (22 Nov 16:00) says rent amounts show only when **both** parties opt in, private by default. R11 (22 Nov 22:22, ⤴ newer) says records stay visible for now and masking comes later. The design follows R11 today: `tenancies.rent_visibility` defaults to `OPEN` and can be switched to `MASKED` by data (D-140). R7's both-opt-in rule also conflicts with rule B, because an owner who never joins can never opt in. Proposed: rent stays `OPEN` until the captain picks a date for R7; then a tenancy's rent is shown when its **tenant** opts in (owner opt-in optional), so rule B tenancies are not locked out. Needs the captain's yes.
- **R8 vs the interim headline.** R8 (22 Nov 15:55) says the headline rating counts verified reviews only. #44 shipped `RATINGS_HEADLINE=all` as the interim default, counting every published review until admins can give ticks (D-135). Proposed: switch to `verified` once the queue of D-151 is live and the existing reviews have been worked through, so headlines do not drop to "no rating yet" overnight. Needs the captain's yes and a date.

---

## 8. Abuse reporting and moderation

- **D-49** Any logged-in user can report a review, response, user, property or tenancy once per target. Parties of the tenancy behind a review may also report it.
- **D-50** Reports never auto-hide content, so a subject cannot hide a fair review by reporting it. Exception: a report with reason `PERSONAL_DATA` from a party of the tenancy auto-hides the **text** (rating still counts) until an admin decides, because exposure of personal data is the higher harm.
- **D-51** Admin outcomes: dismiss; hide review/response (removed from public and aggregates; author is notified with the reason); void tenancy (D-36); suspend user (their pending tenancies cancel, their authored published reviews are hidden pending review). Every action writes `moderation_actions`. Target: reports handled within 72 h.
- Hidden is reversible (restore). Admins never edit review text.

### 8.1 Admin console

- **D-86** *Design v4 (open by default)*: its approvals queue becomes the mobile-first queue of D-136 over users, properties, tenancies, reviews and agreements. The admin console (*changed in admin separation*: its own host, `admin.doorwayliving.in`, §13.3; API `/admin/**` from that host only, `ADMIN` only) gives read access to every record: landlords and tenants (users by role), properties in every status, tenancies, reviews (including `AWAITING_REVEAL` and `HIDDEN`), reports, verification applications, cities and the audit log. Each list has search (name, email, `public_id`, locality, building) and filters (role, city, status, verification status, demo data, created date range), with D-59 pagination. Screens: dashboard (queue counts, oldest waiting item, recent decisions), approvals queue (landlord, tenant, property listing, and from design v3 identity checks: PAN, masked Aadhaar, LinkedIn), areas and societies (D-109), records lists, record detail with its audit trail, reports queue (D-51) and cities (D-83). Admins see private fields that D-57 allows them (email, phone, `unit_label`), but ID numbers only masked, like everyone else; document files only through logged signed URLs (D-95). No bulk export in v1.
- **D-87** Every admin decision writes one append-only `moderation_actions` row: **who** (`admin_id`), **when** (`created_at`), **what** (`action`, `target_type`, `target_ref`) and **why** (`reason`). A reason is required for every reject, revoke, hide, void, suspend, city deactivation and admin revoke; approvals take an optional note. The user's own steps (submit, resubmit) are recorded in `verification_events` (§5.13), so `moderation_actions` stays an admin-only log. Rows of both are never updated or deleted; on account deletion (D-58) the rows stay and refer to the anonymised user. Each record detail page shows the trail for that record.
- **D-88** Conflict of interest: an admin cannot decide their own verification, approve their own property, or moderate a review, tenancy or report they are a party to (`403 admin_conflict`). Another admin must act. The dev seed has one admin and no admin-party records, so this never blocks the demo.

---

## 9. Rating math

For each subject (landlord user, tenant user, property), over reviews with `status = PUBLISHED` and `counts_in_aggregate = true` (*design v4 (open by default)*: and, with `app.ratings.headline=verified`, `verification_status = VERIFIED`; unverified reviews get their own count and average beside it, D-135):

- `n` = review count, `average` = `rating_sum / n`, shown with one decimal.
- **D-52** Public display threshold: with `n < 3` the UI shows "New · n reviews" and the individual reviews, but no star average, so one review does not define a person. *Changed in design v4 (open by default)*: `n` counts green-tick reviews only (D-135); the "Not verified yet" group shows its own count, and its average only from 3 reviews.
- **D-53** Ranking/sorting uses a Bayesian average: `bayes = (C·m + rating_sum) / (C + n)` with `C = 5` and `m` = the mean rating of all counted reviews of that subject type (recomputed nightly; `3.5` until 50 reviews exist). Sort "Top rated" uses `bayes_score`; ties break on `n` then `last_review_at`.
- Distribution `count_1..count_5` and sub-rating averages (over reviews that filled them) are shown with the aggregate.
- Each tenancy contributes at most one review per direction (D-38), and repeat pairs count once (D-44), so long or renewed relationships cannot outvote others.
- No recency decay in v1; reviews show their period so readers can judge age.

Worked example: landlord with reviews 5, 4, 4 → `n=3`, `average=4.3`, with `m=3.5`: `bayes=(5·3.5+13)/(5+3)=30.5/8=3.81`.

### 9.1 Roll-ups, browsing and owners (design v3)

- **D-111** **Roll-ups.** Besides the three person and property subjects, `rating_aggregates` holds three roll-ups over the same counted `TENANT_TO_PROPERTY` reviews (`status = PUBLISHED`, `counts_in_aggregate = true`, published property):
  - `SOCIETY`: every property with that `society_id`;
  - `AREA`: every property with that `area_id`;
  - `OWNER_HOMES`: every property whose current `landlord_id` is that user ("Homes rating" on the owner profile). The owner's person rating stays the separate `LANDLORD` aggregate (`TENANT_TO_LANDLORD`, D-18); the two are shown side by side and never mixed.
  When a property review's counting changes, the same transaction (D-33) recomputes `PROPERTY` and then the property's `SOCIETY`, `AREA` and `OWNER_HOMES` rows from source reviews. Each review counts once in each roll-up it belongs to. D-52 (no average under 3 reviews) and D-53 (Bayesian rank, `m` per subject type) apply to roll-ups too. Tower and wing figures on a society page are computed at read time (bounded by one society) and are not stored. The nightly repair job (D-33) also rebuilds roll-ups.
- **D-112** **Browse, filter and sort.** Public endpoints (D-56 visibility applies to review text):
  - `GET /areas?city=&q=&pincode=&minRating=&sort=` — area cards: name, PIN codes, published property count, society count, `AREA` aggregate.
  - `GET /areas/{id}` — area page: its societies (with `SOCIETY` aggregates) and its standalone published properties.
  - `GET /societies?city=&area=&q=&kind=&minRating=&sort=` — society cards: name, developer, area, tower count, published homes, `SOCIETY` aggregate.
  - `GET /societies/{id}` — society page: towers → wings → published homes (no unit labels), per-tower counts and average, the `SOCIETY` aggregate; `GET /societies/{id}/reviews?tower=&wing=` gives the property reviews of its homes (text for signed-in users only).
  - `GET /properties` gains `area=`, `society=`, `tower=` and `standalone=true` filters.
  - Sort allow-list (D-59) for areas and societies: `top_rated` (Bayesian, D-53), `most_reviewed` (`review_count`), `recent` (`last_review_at`), `name`. Properties keep their sorts. `minRating` filters on `average` and therefore excludes subjects with fewer than 3 reviews (stated in the filter hint).
  - Only `ACTIVE` areas and societies with at least one published property appear in browse lists; a society page opened by link shows its towers even when they have no published homes ("No homes listed yet").
- **D-113** **One owner, many properties.** A landlord can own any number of properties across cities, areas and societies; every property links to one owner (`properties.landlord_id`) and so to one landlord profile. The owner profile lists all published properties grouped by city, then area, then society (standalone ones in their area), each with its `PROPERTY` aggregate, and shows the owner's `LANDLORD` rating and `OWNER_HOMES` "Homes rating" (D-111). `GET /users/{id}/properties?city=&sort=` returns the portfolio with the same visibility as the landlord profile (D-55); anonymous visitors still see only the short name, aggregates and badges on property pages. Co-owners and transfers stay out of v1 (D-22).

---

## 10. Privacy: public vs logged-in

- **D-54** Tenants are not searchable. There is no tenant directory. A tenant's profile and tenant reputation are visible only to: the tenant; landlords of their tenancies; a landlord who received an open enquiry from them (until `expires_at`); anyone holding a share link the tenant created (`/me/share-links`, revocable, 30-day expiry); admins.
- **D-55** Landlord profiles (short name, landlord aggregate, published properties, reviews received as landlord) are visible to any **logged-in** user. Anonymous visitors see the landlord's short name and aggregate only on property pages.
- **D-56** Property pages: anonymous visitors see city, locality, building/street, facts, images, aggregate rating and count. Logged-in users also see review texts, responses and the full rent history.
- **D-57** Never shown to anyone except the user, the other party of a shared tenancy (where relevant) and admins: email, phone (unless shared via enquiry), `unit_label`, tenancy documents, exact tenancy dates of others (public views show month/year only), deposit amounts. Never shown to anyone except the user and admins (not to the other party either): verification documents, the LinkedIn URL, the masked ID number, Aadhaar last 4 and phone last 4, which ID method was used, verification status details and reject reasons (D-95, D-106).
- **D-95** Verification documents are private. ID proofs and property proofs (*changed in design v4*: onboarding rental agreements moved to `rental_agreements`, D-123, D-131) are stored with `Visibility.PRIVATE` through the `ImageStorage` interface (§15) under random keys (`verification/{userPublicId}/{uuid}`), encrypted at rest; JPEG and PNG files are re-encoded to strip EXIF (resolution kept so they stay readable), PDFs are stored as uploaded; `sha256` is recorded. Only the owner and admins can open them, through short-lived signed URLs (5 min, D-30): `GET /me/documents/{id}/url` and `GET /admin/documents/{id}/url`; each admin URL writes a `VIEW_DOCUMENT` audit row (D-87). Public and party-facing APIs never return document data, ID numbers or their last 4, LinkedIn URLs or reject reasons. The only public signals are the **"ID verified"** and **"LinkedIn verified"** badges on profiles, landlord cards and review author lines (*changed in design v3*: per user, from D-99 and D-100, no longer per approved role). Request and error logs exclude multipart bodies and the `idNumber` field.
- **D-96** Retention (DPDP data minimisation): an **ID proof** file (PAN card, masked Aadhaar) is deleted as soon as the admin records the decision (approve or reject) on its identity check; a resubmission uploads a new file. Property proofs are deleted 90 days after their listing is decided (`VERIFICATION_DOC_RETENTION_DAYS`). Rental agreements: *Changed in design v4: replaced by `rental_agreements` (D-123, D-131)*; their files follow only the D-131 rules. Any document file is deleted at once when the user deletes the account (D-58) or replaces the file before a decision. The `identity_verifications` rows (`last4`, `value_hmac`, D-106), statuses and audit rows stay, so duplicates are still caught and decisions stay explained. ID files are deleted by the decision itself; a nightly job with the D-08 lock purges due property proofs. Both set `status = PURGED`.
- **D-58** Account deletion (DPDP Act 2023 aligned): personal data is erased (email, phone, `google_sub`, `google_name`, `avatar_url` cleared, display name replaced). Signing in again with the same Google account creates a new, empty user. Reviews the user **wrote** stay, attributed to "Former member", and keep counting. Reviews **about** the user are hidden with their profile. Property reviews and confirmed rent records stay (they describe the property, not the person).

---

## 11. Permission matrix

Legend: ✅ allowed · 🔒 own only / condition noted · Ⓟ needs a verified phone (D-117, D-132) · — not allowed.
"Party" = landlord or tenant of the tenancy in question. "Logged-in" includes users whose verification is not yet approved. *Changed in design v4 (open by default)*: Ⓟ replaces Ⓟ ("needs an `APPROVED` verification for that role", D-91); approval now gives the green tick and gates nothing (D-134).

| Action | Anonymous | Logged-in | Tenant party | Landlord party | Admin |
|---|---|---|---|---|---|
| List cities, search properties | ✅ | ✅ | ✅ | ✅ | ✅ |
| Browse areas and societies, view society and area pages + aggregates (D-112) | ✅ | ✅ | ✅ | ✅ | ✅ |
| View owner portfolio (all published properties + roll-ups, D-113) | aggregate only | ✅ | ✅ | ✅ | ✅ |
| View property card + aggregate | ✅ | ✅ | ✅ | ✅ | ✅ |
| View property reviews text + rent history (no approval needed) | — | ✅ | ✅ | ✅ | ✅ |
| View property `unit_label` | — | — | ✅ | ✅ | ✅ |
| Create / edit draft property | — | 🔒 `LANDLORD`, own | — | 🔒 own | hide only |
| Publish property (submit; visible at once, D-92) | — | 🔒 `LANDLORD`, own, Ⓟ | — | 🔒 own, Ⓟ | — |
| Archive own property | — | 🔒 `LANDLORD`, own | — | 🔒 own | hide only |
| View landlord profile + reviews (no approval needed) | aggregate only | ✅ | ✅ | ✅ | ✅ |
| View tenant profile + reviews | — | 🔒 self, enquiry grant, share link (D-54) | ✅ self | ✅ | ✅ |
| Send enquiry | — | 🔒 `TENANT`, Ⓟ | — | — | — |
| Add a tenancy period / tenancy request (D-34) | — | 🔒 `TENANT` on a published property, or `LANDLORD` on own property; Ⓟ | — | — | — |
| Confirm tenancy | — | — | 🔒 if counterparty, Ⓟ | 🔒 if counterparty, Ⓟ | — |
| Decline tenancy request (counterparty) / cancel own pending request (initiator), no approval needed | — | — | 🔒 if counterparty or initiator | 🔒 if counterparty or initiator | — |
| End tenancy | — | — | ✅ | ✅ | ✅ |
| Void tenancy | — | — | — | — | ✅ |
| Add rent record | — | — | ✅ Ⓟ | ✅ Ⓟ | — |
| Confirm / dispute rent record | — | — | 🔒 other party, Ⓟ | 🔒 other party, Ⓟ | ✅ |
| Upload / view tenancy document | — | — | ✅ | ✅ | ✅ |
| Write `TENANT_TO_LANDLORD`, `TENANT_TO_PROPERTY` | — | — | ✅ Ⓟ (window open) | — | — |
| Write `LANDLORD_TO_TENANT` | — | — | — | ✅ Ⓟ (window open) | — |
| Edit review | — | — | 🔒 author, D-41, Ⓟ | 🔒 author, D-41, Ⓟ | — |
| Withdraw review | — | — | 🔒 author | 🔒 author | — |
| Respond to review | — | — | 🔒 subject, Ⓟ | 🔒 subject / property landlord, Ⓟ | — |
| Report content | — | ✅ | ✅ | ✅ | — |
| Fill in / submit / resubmit own verification | — | 🔒 own, per held role | ✅ | ✅ | — |
| Verify own phone (OTP), submit PAN or masked Aadhaar, add LinkedIn (D-101–D-103) | — | 🔒 own | 🔒 own | 🔒 own | — |
| Propose a new area / society / tower / wing in a property draft (D-109) | — | 🔒 `LANDLORD`, own draft | — | 🔒 own draft | ✅ create directly |
| Verify / reject / revoke an identity check | — | — | — | — | ✅ not own (D-88) |
| Upload / view own verification documents | — | 🔒 owner (D-95) | 🔒 owner | 🔒 owner | ✅ logged |
| View all records, search and filter (admin console) | — | — | — | — | ✅ (D-86) |
| Approve / reject verification and property listings | — | — | — | — | ✅ not own (D-88) |
| Give or refuse the green tick on a user, property, tenancy or review (D-134) | — | — | — | — | ✅ not own (D-88) |
| Create / activate / deactivate city | — | — | — | — | ✅ |
| Grant / revoke `ADMIN` | — | — | — | — | ✅ not self (D-19) |
| Handle reports, hide/restore, suspend | — | — | — | — | ✅ |

---

## 12. API outline

Base path `/api/v1`. JSON only (`application/json`), except uploads (`multipart/form-data`) and errors (`application/problem+json`). All IDs in paths are `public_id`s.

### 12.1 Conventions
- **D-59** Pagination: offset style, `?page=0&size=20` (0-based, `size` max 50, default 20), `?sort=field,asc|desc` from a per-endpoint allow-list. Response envelope:
  ```json
  { "items": [ ... ], "page": { "number": 0, "size": 20, "totalItems": 37, "totalPages": 2 } }
  ```
- **D-60** Errors use RFC 9457 Problem Details, always with a stable machine `code`:
  ```json
  {
    "type": "https://doorwayliving.in/problems/review-window-closed",
    "title": "Review window closed",
    "status": 422,
    "code": "review_window_closed",
    "detail": "Reviews for this tenancy closed on 2026-08-31.",
    "instance": "/api/v1/tenancies/0193.../reviews",
    "errors": [ { "field": "body", "code": "too_short", "message": "At least 30 characters." } ],
    "traceId": "4f1c..."
  }
  ```
  Status use: `400` malformed, `401` no/invalid token, `403` authenticated but not allowed (including `id_verification_required` with `missing` in the body, D-117, and `admin_conflict`, D-88; *changed in design v4 (open by default)*: `approval_required` is no longer returned, D-132), `404` not found **or not visible to the caller** (no existence leak), `409` conflict (duplicate review, overlapping tenancy, state transition not allowed, `concurrent_update` when the database refused a lock to a simultaneous write; nothing was saved, so retry), `422` validation/business rule, `429` rate limited (with `Retry-After`).
- Dates `YYYY-MM-DD`, timestamps ISO-8601 UTC, money as integer rupees with field suffix `Inr`.
- Writes that may be retried by clients accept an optional `Idempotency-Key` header (stored 24 h) on `POST /tenancies`, `POST .../reviews`, `POST /reports`.
  Endpoints opt in with `@Idempotent`. Keys are 1–64 chars from `A-Z a-z 0-9 . _ : -` (else `400 idempotency_key_invalid`) and are scoped to the signed-in caller. The first request reserves the key; a `2xx` response (status, `Content-Type`, `Location`, body) is stored and replayed for the same key and same request (method, path, query, body) with `Idempotent-Replayed: true`. A non-`2xx` response releases the key so the client can fix and retry with it. Same key with a different request: `422 idempotency_key_reused`; same key while the first request still runs: `409 idempotency_request_in_progress`. A reservation still `IN_PROGRESS` after a 5-minute lease is treated as abandoned (its instance died), so a retry with the key runs. Expired keys are deleted hourly by a locked job (D-08).
- Problem `title` is derived from `code` (`review_window_closed` → "Review window closed"). `ApiException` factories: `notFound`, `conflict` (409), `rule` (422 business rule), `validation` (422 with `errors`), `rateLimited` (429 with `Retry-After` in seconds).

### 12.2 Endpoints

**Auth**
| Method | Path | Notes |
|---|---|---|
| GET | `/auth/config` | public: `{googleClientId, devLogin: bool}` so the web needs no build-time Google config (D-76) |
| POST | `/auth/google` | `{idToken}` → verifies the Google ID token (D-70), finds or creates the user, returns `{accessToken, user, needsOnboarding}` + refresh cookie |
| POST | `/auth/refresh` | uses refresh cookie, rotates it |
| POST | `/auth/logout` | revokes current refresh token |
| GET | `/auth/dev-users` | **`dev`/`test` profile only** (D-77): seed users for the login picker (no seed admins, D-155) |
| POST | `/auth/dev-login` | **`dev`/`test` profile only** (D-77): `{userId}` of a seed user → same response as `/auth/google` |
| POST | `/auth/dev-login/new` | **`dev`/`test` profile only** (D-77): no body; signs in as a brand-new, non-demo user with no role (a made-up `@example.test` email) → same response as `/auth/google` with `needsOnboarding: true` |
| POST | `/auth/admin/google`, `/auth/admin/refresh`, `/auth/admin/logout` | *admin separation*: the admin console's session on the admin host only (`404` elsewhere); same shapes as the main site's, with the admin token and the `rentrix_admin_refresh` cookie; `403 not_an_admin` for non-members (§13.3, D-155) |
| GET / POST | `/auth/admin/dev-users`, `/auth/admin/dev-login` | **`dev`/`test` profile only**, admin host only: the seed admins and an admin session for one of them (D-155) |

**Me**
| Method | Path | Notes |
|---|---|---|
| GET / PATCH / DELETE | `/me` | profile (display name, phone, home city), add role, account deletion (D-58). A `PATCH` returns `{user, accessToken}`; the token carries the current roles, so an added role takes effect at once (D-73). The phone is returned masked only (`+91 XXXXXX3210`, D-106); a `PATCH` that changes or clears it revokes a verified `PHONE_OTP` check (`PHONE_CHANGED`, D-101) |
| POST | `/me/onboarding` | once, first sign-in: `{roles:[TENANT|LANDLORD], homeCity?, acceptTerms:true}` → `{user, accessToken}` with the new roles (D-73) |
| GET | `/me/tenancies?role=&status=` | the caller's tenancies, either side or `role=LANDLORD|TENANT`; `status` is a comma list; D-59 paging, `sort` = `newest` (default, desc), `start`, `updated`. Each item is the tenancy view of `GET /tenancies/{id}` |
| GET | `/me/reviews/written`, `/me/reviews/received?as=LANDLORD|TENANT` | received includes own `AWAITING_REVEAL` state only as "pending" |
| GET | `/me/review-tasks` | tenancies with an open review window and missing reviews |
| GET | `/me/enquiries?as=TENANT|LANDLORD` | |
| GET / POST / DELETE | `/me/share-links` | tenant reputation share links |
| GET | `/me/properties` | landlord's own, all statuses |
| GET | `/me/verification` | one entry per held role: `{id?, role, status, revision, steps: {profile, phone, method, identity, linkedin, rentalAgreement}, submittedAt?, decision: {code, note, decidedAt}?}` (`method` is D-144's any-one posting method); `id` is the application's `public_id` (absent until the first submit); each step is `DONE`, `PENDING`, `MISSING`, `REJECTED`, `SKIPPED` or `NOT_APPLICABLE`; `decision` carries code and note for `REJECTED`, only `decidedAt` for `APPROVED` (the approve note is admin-only); `GET /me` carries a short `verification: {TENANT: status, LANDLORD: status}` (a held role without an application reports `DRAFT`) and `badges: {idVerified, linkedinVerified}`. *Design v4*: `phone` added and step meanings per D-120 |
| POST | `/me/verification/{role}/submit` | `DRAFT` or `REJECTED` → `PENDING`; `422 verification_incomplete` with the missing steps in `missing` (`profile` without a display name, the home city being optional (D-120); *design v4*: `phone` when neither `PHONE_OTP` nor `TRUECALLER` is `VERIFIED`, D-117, D-138, replacing design v3's `identity` rule; *D-144*: `method` when none of phone, masked Aadhaar or LinkedIn is `VERIFIED` or `PENDING`; a `PENDING` Aadhaar or LinkedIn may submit, as approve decides it first); `409 invalid_transition` when already `PENDING`/`APPROVED`; `429` with `Retry-After` after 3 submits in 24 h; a role the caller does not hold is `404`. *Design v3*: the design-v2 `PUT /me/verification/{role}` (LinkedIn fields) is replaced by `/me/identity/linkedin` |
| GET | `/me/identity` | `{idVerified, linkedinVerified, methods: [{method, status, masked, checkedBy, verifiedAt?, reject: {code, note}?, available}]}`; masked values only (D-106) |
| POST | `/me/identity/phone/start` | `{phone, channel?}` (Indian mobile; `+91`, `91` or bare 10 digits, spaces allowed; `channel` `TELEGRAM` or `SMS`, default Telegram when configured, D-146) → `{challengeId, expiresAt, delivery}` (`delivery`: `TELEGRAM` (D-146), `SMS`, or `TEAM` under `admin-relay`, D-137, where the web says the team will message the code); sends the code by Telegram or SMS; limits and `429` per D-101; `409 phone_already_verified` for the number already verified on this account; `503 phone_otp_unavailable` without a provider in prod; `503 sms_send_failed` when the provider refuses (that code is used up and still counts toward the limits); `503 telegram_send_failed` when Telegram cannot reach the number (same, D-146); `422` for an unknown `channel`; `429 rate_limited` with `Retry-After` also when parallel starts for the same number queue up for more than a few seconds |
| POST | `/me/identity/phone/confirm` | `{challengeId, code}` → `PHONE_OTP` `VERIFIED` (`TELEGRAM` for a Telegram code, D-146), returns the `GET /me/identity` body; wrong code `422 otp_invalid` (attempts left), after 5 wrong codes `422 otp_attempts_exceeded`, expired or already used `422 otp_expired`; another user's challenge `404` |
| POST | `/me/identity/truecaller/start` | D-138: `{requestId, deepLink, expiresAt}`; `503 truecaller_unavailable` without a Truecaller key |
| GET | `/me/identity/truecaller/{requestId}` | D-138: `{requestId, status, masked?, reason?}`, status `PENDING`, `INVOKED`, `VERIFYING`, `VERIFIED`, `REJECTED`, `FAILED` or `EXPIRED`; another user's request `404` |
| POST | `/auth/truecaller/callback` | D-138: anonymous, Truecaller's server only; always `200` for a JSON object (unknown, expired or used nonces are ignored), `400` otherwise |
| POST | `/me/identity/pan` | `{panNumber, documentId}` → `PAN` `PENDING`; format check, keeps last 4 + HMAC only (D-102) |
| POST | `/me/identity/aadhaar` | `{last4, documentId, consent: true}` → `AADHAAR` `PENDING`; anything but 4 digits `422`, 12 digits `422 aadhaar_full_number_refused` (D-103) |
| PUT / DELETE | `/me/identity/linkedin` | `{url}` → `LINKEDIN` `PENDING` (normalised, never fetched, D-93); delete removes it |
| POST | `/me/documents` | multipart `{kind, role?, propertyId?, idType?, file}` → `{id, kind, idType, status}`; `kind` ∈ `ID_PROOF`, `PROPERTY_PROOF` (*changed in design v4*: `RENTAL_AGREEMENT` replaced by `/me/agreements`, D-123, D-131); `idType` ∈ `PAN`, `AADHAAR_MASKED` for `ID_PROOF`; ID numbers go through `/me/identity/*`, never this endpoint |
| GET | `/me/documents` | own documents, metadata only, masked numbers |
| GET | `/me/documents/{id}/url` | owner only: 5-min signed URL (D-95) |
| DELETE | `/me/documents/{id}` | owner, only while the application or listing is not `PENDING`/decided |
| POST | `/me/agreements` | *design v4* (D-123–D-131): multipart `{file, data}`, `data` = JSON `{side, tenancyId?, cityCode, anchorKind?, fields: {…§5.20}, consent: true}` → `DRAFT` agreement with `sha256`, masked fields, checks and tier; without `tenancyId` it links to the caller's application for `side`. With `tenancyId` the caller must be a party of that `ACTIVE` or `ENDED` tenancy on the given `side` (else `404`, as for `/link`) and have a verified phone (`403 id_verification_required`, D-128); the new upload replaces the tenancy's live agreement. `consent` missing `422 consent_required`; type or size `422`; an Aadhaar-like number in a field is masked, not refused (D-131) |
| GET | `/me/agreements`, `/me/agreements/{id}` | own uploads plus agreements of the caller's tenancies; fields (masked), checks (own side only before the other party confirms), tier, status, `fileAvailable` |
| PATCH | `/me/agreements/{id}` | uploader, while `DRAFT` or `SUBMITTED` and not decided: correct fields; checks re-run, confirmations are kept (the file and its hash did not change) |
| POST | `/me/agreements/{id}/confirm` | uploader confirms the fields → `SUBMITTED`; on a tenancy-linked agreement it needs a verified phone like the counterparty confirm (D-128); an onboarding agreement is not gated |
| POST | `/me/agreements/{id}/link` | `{tenancyId}`: link an onboarding agreement to the uploader's tenancy (a party, `ACTIVE`/`ENDED`, else `404`; verified phone, D-128) |
| GET | `/me/agreements/{id}/url` | uploader, the linked tenancy's other party: 5-min signed URL to the decrypted file; `410 file_deleted` after retention |
| DELETE | `/me/agreements/{id}` | uploader; before the decision deletes row and file, after it only the file (D-131) |

**Reference & stats**
| GET | `/cities?q=` | active cities only, `{code, name, aliases[], state}`, by name; `q` matches code/name/alias/state (D-82) |
| GET | `/areas?city=&q=&pincode=&minRating=&sort=` | area cards with `AREA` aggregate and counts (D-108, D-112); D-59 pages; `city` optional (live cities only), `q` a word prefix of name or alias, `pincode` exactly 6 digits. Lists show `ACTIVE` rows plus the caller's own `PENDING` proposals (admins: every `PENDING`), never `HIDDEN`. Until B16: `sort=name` only and no aggregate |
| GET | `/areas/{id}` | area page: societies + standalone published properties (from B15: the societies and `publishedHomes` / `standaloneHomes` counts; the homes themselves are `GET /properties?area=&standalone=true`). A `HIDDEN` area still opens by link; a `PENDING` one opens by id only for its proposer, admins and landlords with one of their own homes on it (D-109), else `404` |
| GET | `/societies?city=&area=&q=&kind=&minRating=&sort=` | society cards with `SOCIETY` aggregate (D-112) |
| GET | `/societies/{id}` | society page: towers → wings → published homes, per-tower counts, aggregate (from B15: towers and wings with `publishedHomes` counts; the homes are `GET /properties?society=&tower=`) |
| GET | `/societies/{id}/reviews?tower=&wing=` | property reviews of homes in the society; text for signed-in users (D-56) |
| GET | `/societies/{id}/towers` | towers and wings, for the property form pickers (landlord sees own `PENDING` rows too) |
| GET | `/stats` | real counts: published properties, confirmed tenancies, published reviews, per active city: `{publishedProperties, confirmedTenancies, publishedReviews, cities: [{city, publishedProperties, confirmedTenancies, publishedReviews}]}` (`city` is the code; every active city has a row, zeros included). Until B4 serves it the web shows no counts (D-03) |

**Properties**
| Method | Path | Notes |
|---|---|---|
| GET | `/properties?city=BLR&area=&society=&tower=&standalone=&locality=&type=&bedrooms=&minRating=&q=&sort=` | published only; `city`, when given, must be an active city code (without it every active city is searched); `locality` is a prefix, `q` matches locality, building and street; `minRating` (1–5) needs an average shown (3+ reviews, D-52) and compares the shown one-decimal value; sorts `top_rated` (default, D-53), `most_reviewed`, `newest`, and a sort name without a direction sorts descending; `area`, `society`, `tower` take `public_id`s (D-112, built in PLAN B15): unknown ids and other users' `PENDING` rows that hold none of the caller's homes (D-109) are `422` on the parameter, a tower outside the society, a society outside the area or a place outside `city` is `422 location_mismatch`; `standalone=true` keeps homes outside any society (not combinable with `society` / `tower`), `standalone=false` those inside one |
| GET | `/properties/{id}` | fields per D-56 |
| POST | `/properties` | `LANDLORD`; creates `DRAFT`; location `{areaId | newArea{name, pincode}, societyId? | newSociety{name, kind, developer?}, towerId? | newTower{name, kind}, wingId? | newWing{name}}`; proposed rows are `PENDING` (D-109, D-110) |
| PATCH | `/properties/{id}` | owner; edits (location fields locked once published, D-92) and `ARCHIVED` |
| POST | `/properties/{id}/submit` | owner, Ⓟ landlord: `DRAFT` → `PUBLISHED` with `verification_status=UNVERIFIED` (*changed in design v4 (open by default)*: was `PENDING_REVIEW` with a required `PROPERTY_PROOF`, D-92) |
| DELETE | `/properties/{id}` | owner; archives a `PUBLISHED` listing (D-21); one never published (`DRAFT`, `PENDING_REVIEW`, `REJECTED`) is soft-deleted, since it has no history to keep |
| GET / POST | `/properties/{id}/images` | upload ≤ 10 images |
| PATCH / DELETE | `/properties/{id}/images/{imageId}` | order, primary, delete |
| GET | `/properties/{id}/rent-history` | logged-in (D-56); D-26: `{propertyId, rows: [{fromMonth, toMonth?, monthlyRentInr, maintenanceInr?, share}]}`, months as `YYYY-MM` only (D-57), no tenant identity, confirmed records of `ACTIVE`/`ENDED` tenancies only, oldest first (*design v4 (open by default)*: also `PROPOSED` records, and rows gain `confirmed`, `verified` and `agreementTier`, D-25, D-134). A row runs to the month before the next confirmed record of its tenancy, or to the tenancy's end month; `toMonth` is absent while the rent is current. `share` is true on `ROOM`/`PG` properties |
| GET | `/properties/{id}/reviews` | logged-in for text; `TENANT_TO_PROPERTY` only |
| GET | `/properties/{id}/rating` | aggregate; public; *design v4 (open by default)*: `{verified: {aggregate}, unverified: {count, average}}` (D-135) |
| POST | `/properties/{id}/enquiries` | `TENANT` |

**Users (public profiles)**
| GET | `/users/{id}` | shape depends on D-54/D-55; `404` when not visible; carries `badges: {idVerified, linkedinVerified}` (D-100) and, for landlords, `landlordRating` + `homesRating` (`OWNER_HOMES`) |
| GET | `/users/{id}/properties?city=&sort=` | owner portfolio grouped by city, area, society, with `PROPERTY` aggregates (D-113) |
| GET | `/users/{id}/reviews?as=LANDLORD|TENANT` | same visibility |
| GET | `/share/{token}` | tenant profile via share link |

**Tenancies**
| Method | Path | Notes |
|---|---|---|
| GET | `/me/homes/matches` | "Is it this one?" (D-139): `?city&pincode&unit` and `society` (id) or `societyName`; published homes with that PIN code and society; homes with an `OPEN` flat number also need that flat number, `MASKED` ones match on the society alone. At most 10 cards, never a flat number. Signed in. |
| POST | `/me/homes` | "Add your home" (D-139, D-142): `{propertyId? | home?, relation: RENT|OWN, startDate?, endDate?, monthlyRentInr?, depositInr?, owner?: {name?, email?, phone?}, newCity?: {name, state}}` (D-143: a city not in the list, created pending). `201` `{property, tenancy?, accessToken?}`; the tenancy is one-sided and `ACTIVE`/`ENDED` at once; `OWN` adds the `LANDLORD` role. Gate: posting gate (D-132); `422 claim_not_available`. |
| POST | `/tenancies` | `{propertyId, counterpartyEmail?, startDate, endDate?, initialRentInr, depositInr?}`. The caller's side follows from the property: its landlord must give `counterpartyEmail`. The answer is the same whether or not that email has an account (privacy: no account lookup by email): the landlord gets `tenant` = null and a masked `inviteEmail` (`p***@g***.com`). Internally, an existing email-verified account with the TENANT role is linked so it sees the request in `/me/tenancies` and gets a confirm-request email; otherwise a single-use invite is emailed. For such a request `409 tenancy_overlap` is checked when the invite is claimed and on confirm, against confirmed tenancies only (a sibling pending request never blocks), not at creation; anyone else is the tenant, on a `PUBLISHED` property, and sends no email. `startDate ≤ today`; `endDate` only for a tenancy that already ended (`≤ today`). The landlord's property must be `PUBLISHED` or `ARCHIVED` (`422 property_not_listed`). Guards D-35: `403 id_verification_required` (no verified phone, D-132; *changed in design v4*: was `approval_required`), `403 role_required`, `422 email_not_verified`, `422 invite_self`, `409 tenancy_overlap` (tenant-started requests; on a whole home any request whose dates overlap a confirmed tenancy, D-23), `429 rate_limited` with `Retry-After` (5 tenancy periods per 24 h, 20 per 30 days, D-35; *changed in design v4*: was `422 too_many_pending`) |
| GET | `/tenancies/{id}` | parties + admin, else `404`. `{id, property{id, buildingName, locality, city, unitLabel, propertyType}, myRole, landlord{id, shortName}, tenant? (hidden from the landlord until a landlord-started request is confirmed), inviteEmail? (masked for the landlord, full for admin), startDate, endDate?, initialRentInr, currentRentInr?, currentRentFrom? (date the current rent applies from), depositInr?, depositRefundedInr?, status, initiatedBy, declineReason?, evidenceLevel?, createdAt, expiresAt? (pending), confirmedAt?, endedAt?, actions{confirm, decline, cancel, end, addRentRecord}}` |
| POST | `/tenancies/{id}/confirm`, `/decline`, `/cancel`, `/end` | state transitions (§6); a transition the state does not allow is `409 invalid_transition`. `confirm` (counterparty, Ⓟ) → `ACTIVE`, or `ENDED` when the request has an end date, and writes the first `CONFIRMED` rent record (*design v4 (open by default)*: the tenancy is already `ACTIVE`/`ENDED`; confirm sets `confirmedAt` and confirms the first rent record, D-34); `decline` (counterparty, optional `{reason}`) and `cancel` (initiator) are not gated; `end` `{endDate, depositRefundedInr?}` (either party or admin, not gated) on `ACTIVE` only |
| POST | `/tenancy-invites/claim` | `{token}` from the invite link (`APP_BASE_URL/tenancy-invite?token=…`; in the body so it stays out of URLs and logs). Binds a landlord's invite to the caller when their verified email equals the invited one (`403 invite_email_mismatch` otherwise), the caller holds `TENANT` and no overlap exists; single use, expires with the request (14 d). The invitee then confirms or declines as the counterparty |
| GET / POST | `/tenancies/{id}/rent-records` | parties + admin; `POST {effectiveFrom, monthlyRentInr, maintenanceInr?}` by a party (Ⓟ) on an `ACTIVE`/`ENDED` tenancy (`409 tenancy_not_confirmed` otherwise) → `PROPOSED`. `effectiveFrom` is stored as the first of its month and must lie from the start month to the end date, or to next month while current (`422 outside_tenancy`); one live record per month (`409 rent_record_exists`) |
| POST | `/rent-records/{id}/confirm`, `/rent-records/{id}/dispute` | the party who did not add it (Ⓟ) or an admin; `PROPOSED` only (`409 invalid_transition`) |
| GET / POST | `/tenancies/{id}/documents` | private; GET returns signed URLs |
| POST | `/tenancies/{id}/reviews` | `{direction, ratingOverall, ratingA?, ratingB?, ratingC?, body}` |
| GET | `/tenancies/{id}/agreement` | *design v4*: parties and admin; the live agreement of the tenancy, else `404`. The tenancy view gains `agreementTier` |
| POST | `/tenancies/{id}/agreement/confirm`, `/dispute` | the party who did not upload it (Ⓟ): confirm `{sha256}` (`409 agreement_changed` on a different hash, `409 file_deleted` once the file is gone) or dispute `{reason}` (D-128) |

**Reviews**
| GET | `/reviews/{id}` | visibility follows its subject |
| PATCH | `/reviews/{id}` | author, within edit window |
| DELETE | `/reviews/{id}` | author withdraws |
| POST / PATCH | `/reviews/{id}/response` | subject, one per review |

**Reports**
| POST | `/reports` | `{targetType, targetId, reason, details?}` |

**Admin** (`ADMIN` only; every write audited, D-87; `reason` required where D-87 says so)
| Method | Path | Notes |
|---|---|---|
| GET | `/admin/overview` | queue counts (pending landlord / tenant applications, pending listings, open reports, documents to check), oldest waiting item per queue, last 10 decisions |
| GET | `/admin/queue?type=USER|PROPERTY|TENANCY|REVIEW|AGREEMENT&city=` | *design v4 (open by default)*: the approvals queue of D-136, oldest first, D-59 pages; one card shape per item `{type, id, title, subtitle, city, createdAt, flags[], verificationStatus, relayOtp?}` (`relayOtp` only with `SMS_BACKEND=admin-relay`, D-137); counts per type. *Built (W9 PR 2)*: `type=USER|IDENTITY|PROPERTY|TENANCY|REVIEW|CITY|REPORT`, newest first, at most 50 cards per type (no pages yet); no `type` lists every type but `REPORT`; `city` is a city code. The card also carries `details[]` (masked key facts), `link` (a main-site path or null), `actions[]` (`APPROVE`, `REJECT`, `HIDE`) and `rejectCodes[]`; `relayOtp` is not on cards (the OTP relay page shows codes). Reason codes for homes, tenancies and reviews: `CANNOT_CONFIRM`, `DETAILS_WRONG`, `DUPLICATE`, `OTHER`; for a hide: `ABUSE`, `PERSONAL_DATA`, `OTHER` |
| POST | `/admin/tenancies/{id}/approve`, `/reject`, `/admin/reviews/{id}/approve`, `/reject` | *design v4 (open by default)*: give the green tick (`{note?}`) or refuse it (`{code, note}`) (D-134); audited `VERIFY_ITEM` / `REJECT_ITEM`; a party or author admin `403 admin_conflict` (D-88). *Built (W9 PR 2)*: the reject `note` is optional; the same answer twice `409 invalid_transition`; a review's tick change recomputes its ratings in the same transaction (D-135); `POST /admin/reviews/{id}/hide {code, note?}` hides a review for abuse (`HIDE_REVIEW`, D-51). Users use `/admin/applications/{id}/approve` / `reject` and properties `/admin/properties/{id}/approve` / `reject`, which now set the tick instead of opening writes or publishing |
| GET | `/admin/applications?role=&status=PENDING&flag=&city=&q=&sort=submittedAt,asc` | approvals queue |
| GET | `/admin/applications/{id}` | profile, identity methods (status, masked value, documents), LinkedIn URL + status, flags, previous revisions and decisions |
| POST | `/admin/applications/{id}/approve` | `{note?, identity?: [{id, result: VERIFIED|REJECTED, code?, note?}]}` → `APPROVED`; needs ≥1 ID method `VERIFIED` after the identity results apply (`422 no_verified_id` otherwise, and none of the call is kept; *design v4*: needs the `VERIFIED` phone, `422 phone_not_verified`, D-117; *D-144*: needs any one verified posting method (phone, masked Aadhaar or LinkedIn), `422 no_verified_method`; `APPROVED` is the user's green tick for that role, D-134); `identity` decides only the applicant's `PENDING` PAN or Aadhaar checks (*D-144*: or LinkedIn check); own application `403 admin_conflict` (D-88), not `PENDING` `409 invalid_transition` (D-105) |
| POST | `/admin/applications/{id}/reject` | `{code, note}` → `REJECTED` |
| POST | `/admin/applications/{id}/revoke` | `{code, note}` → `REJECTED` from `APPROVED` |
| GET | `/admin/identity?method=&status=PENDING` | identity checks queue (PAN, masked Aadhaar, LinkedIn) |
| POST | `/admin/identity/{id}/verify`, `/reject`, `/revoke` | one method; reject and revoke `{code, note}` (*built in W9 PR 2*: verify and reject, `note` optional, codes `ID_UNREADABLE`, `ID_NAME_MISMATCH`, `AADHAAR_NOT_MASKED`, `LINKEDIN_MISMATCH`, `OTHER`; the user's own check `403 admin_conflict`; revoke follows); audited `VERIFY_IDENTITY` / `REJECT_IDENTITY` / `REVOKE_IDENTITY`; verify and reject delete the method's ID file at once (D-96) |
| GET / POST / PATCH | `/admin/areas`, `/admin/areas/{id}` | areas with PIN codes; create, rename, aliases, PIN codes, hide (D-108, D-109) |
| GET / POST / PATCH | `/admin/societies`, `/admin/societies/{id}`, `/admin/societies/{id}/towers`, `/admin/towers/{id}/wings` | create, rename, hide; `PENDING` rows are listed with their listing |
| GET | `/admin/properties?status=PENDING_REVIEW&city=&q=` | all statuses available |
| POST | `/admin/properties/{id}/approve`, `/reject` | listing decision (D-92; *design v4 (open by default)*: the green tick on an already published property, D-134); reject `{code, note}`; approve also approves the listing's `PENDING` location rows, or takes `{useAreaId?, useSocietyId?, useTowerId?, useWingId?}` to point the listing at existing rows instead (D-109) |
| GET | `/admin/documents/{id}/url` | 5-min signed URL; writes `VIEW_DOCUMENT` (D-95) |
| GET | `/admin/agreements?status=&tier=&flag=&city=`, `/admin/agreements/{id}` | *design v4*: agreements queue and detail with every field, check reasons, flags, both confirmations and the lookup link (D-130); `GET /admin/applications/{id}` carries the applicant's agreements in the same shape |
| POST | `/admin/agreements/{id}/anchor` | `{result: VERIFIED|NOT_FOUND, note}` → tier re-computed, `decided_at` set; audited (D-130) |
| GET | `/admin/agreements/{id}/url` | 5-min signed URL; writes `VIEW_DOCUMENT` |
| GET | `/admin/users?role=LANDLORD|TENANT&verification=&status=&city=&demo=&q=` | landlord and tenant records |
| GET | `/admin/users/{id}` | record detail: profile, roles, verification per role, documents (masked), tenancies, reviews written/received, reports, audit trail |
| GET | `/admin/tenancies?status=&city=&q=`, `/admin/reviews?status=&direction=&city=&q=` | records lists |
| GET | `/admin/tenancies/{id}`, `/admin/reviews/{id}`, `/admin/properties/{id}` | record detail with audit trail |
| GET | `/admin/reports?status=` | reports queue |
| POST | `/admin/reports/{id}/resolve` | `{outcome: DISMISS|HIDE|VOID_TENANCY|SUSPEND_USER, note}`. *Built (W9 PR 2)*: `DISMISS` (`DISMISS_REPORT`) and `HIDE` of a reported review (`{code, note?}`, the review is hidden as above and the report `ACTIONED`); the reporter, or a party to the reported item (D-88), as admin `403 admin_conflict` on either outcome; `VOID_TENANCY` and `SUSPEND_USER` follow |
| POST | `/admin/reviews/{id}/hide`, `/restore` | |
| POST | `/admin/tenancies/{id}/void` | |
| POST | `/admin/tenancy-documents/{id}/accept`, `/reject` | |
| POST | `/admin/users/{id}/suspend`, `/reinstate` | |
| POST / DELETE | `/admin/users/{id}/admin-role` | grant / revoke `ADMIN` (D-19); not self |
| GET | `/admin/cities` | all cities, active and inactive |
| POST | `/admin/cities` | `{code, name, aliases[], state, pincodePrefixes[]}` → created inactive (D-83) |
| PATCH | `/admin/cities/{code}` | `{live?, name?, aliases?, state?, pincodePrefixes?}`; deactivation needs `reason`. *Built (W9 PR 2)*: only `{live: true}`, to approve a city someone typed (D-143), audited `ACTIVATE_CITY`; anything else `422 action_not_allowed` |
| GET | `/admin/audit?targetType=&targetRef=&adminId=&action=` | audit log |

**Ops**: `GET /actuator/health` (public, no details), probes `GET /actuator/health/liveness` and `GET /actuator/health/readiness` (readiness group includes the DB; the API container health check uses readiness), `GET /v3/api-docs` + Swagger UI only when the `dev` or `test` profile is explicitly active (D-64).

- **D-61** The OpenAPI document generated by springdoc is the contract the web builds against; types in `rentrix-web/src/types` are generated from it (`openapi-typescript`) instead of hand-written. The web commits a snapshot of the document (`rentrix-web/openapi/openapi.json`, refreshed with `npm run gen:api:fetch` against a dev API) and generates `src/types/api.gen.ts` from it, so web builds need no running API; O2's drift check compares the snapshot with the API's.

---

## 13. Authentication

- **D-62** Access token: JWT, HS256, 15 min, claims `sub` (user public_id), `roles`, `iat`, `exp`, `jti`. Sent as `Authorization: Bearer`. The web keeps it in memory only (never `localStorage`). Role and onboarding checks read `roles` from the JWT; every call that changes the caller's roles (`POST /me/onboarding`, role-adding `PATCH /me`) returns a fresh access token (D-73), so the claim is never stale for the caller. The refresh cookie is not rotated there: its `Path=/api/v1/auth` keeps the browser from sending it to `/me`, and `/auth/refresh` re-reads the roles from the database, so an old refresh token never brings back stale roles.
- **D-63** Refresh token: opaque random 256-bit value, 30-day lifetime, **rotated on every refresh**, stored only as a SHA-256 hash in `refresh_tokens(user_id, token_hash, family_id, expires_at, revoked_at)`. Rotation is a conditional update (`revoked_at IS NULL`), so two simultaneous rotations of one token cannot both succeed. **Reuse grace** (30 s, `app.auth.refresh-reuse-grace`): presenting the family's most recently rotated token again within 30 s of its rotation (two tabs, or a lost race, refreshing at once) returns a fresh access token for the user and **no** new cookie; the current successor stays valid and is not rotated again. Any other reuse of a rotated or revoked token (outside the window, or an older token in the chain) revokes the whole family (theft detection). The web also serialises refreshes across tabs (W3). Delivered as an `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` cookie. This keeps API instances stateless while allowing logout and revocation.
- **D-64** Fail closed: `SPRING_PROFILES_ACTIVE` has no default (the `${SPRING_PROFILES_ACTIVE:dev}` default in `application.yaml` is removed), and the API refuses to start unless it is set explicitly to `dev`, `test` or `prod`. The hard-coded dev `JWT_SECRET` exists only when `dev` or `test` is explicitly active; any other profile refuses to start without a real `JWT_SECRET` of at least 32 bytes. Secrets come from env only, never from tracked files. Local run: `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run`.
- Web and API must be same-site for the cookie (production: `doorwayliving.in` and `api.doorwayliving.in`); in dev the Vite dev server proxies `/api` to the API. CORS allow-list from `CORS_ALLOWED_ORIGINS`; when it is empty in `prod` the API starts with a warning and allows no cross-origin origin (an old Render deployment without the variable keeps starting).
- Rate limit on `/auth/google`, `/auth/dev-login` and `/auth/dev-login/new`: 20 requests per 15 min per IP (`429`).
- Suspended or deleted users: sign-in and refresh fail (`403 account_suspended`); access tokens expire within 15 min (accepted window).

### 13.1 Sign in with Google

```
 Browser (React)                     API                                Google
   │ GET /auth/config ─────────────▶ │ {googleClientId, devLogin}
   │ GIS button (client id) ──────────────────────────────────────────▶ │ user signs in
   │ ◀─────────────────────────────────────────────── ID token (JWT) ── │
   │ POST /auth/google {idToken} ──▶ │ verify signature (JWKS, cached) ─▶│ certs
   │                                 │ check iss, aud, exp, email_verified
   │                                 │ find user by google_sub, or create
   │ ◀── accessToken + refresh cookie, needsOnboarding
   │ (if needsOnboarding) POST /me/onboarding {roles}
```

- **D-70** "Sign in with Google" is the only production login, for tenants and landlords alike. The web uses Google Identity Services (GIS) to get an **ID token** in the browser and posts it to `POST /auth/google`. The API verifies it server-side: RS256 signature against Google's JWKS (`https://www.googleapis.com/oauth2/v3/certs`, fetched on first use and cached in memory, re-fetched when a token names an unknown key id), `iss` ∈ {`accounts.google.com`, `https://accounts.google.com`}, `aud` = `GOOGLE_CLIENT_ID`, `exp` not passed (60 s clock skew), `email_verified` = true. Any failure → `401 google_token_invalid`. The API then issues its **own** access + refresh tokens (D-62, D-63); the Google token is never stored or forwarded, so the API stays stateless.
- **D-71** No passwords in v1: no password column, no signup form, no password reset, no email-verification flow (Google already verified the email). This removes a whole class of credential storage risk.
- **D-72** One Google account = one Rentrix user, keyed by `google_sub`, never by email (Google emails can change; the `sub` does not). On every sign-in the API refreshes `email`, `email_verified_at`, `google_name` and `avatar_url` from the token; `display_name` is changed only by the user. If the token's email already belongs to a **different** user (rare: account recreated at Google, or a seed user email), sign-in fails with `409 account_email_conflict` and an admin resolves it; accounts are never merged automatically.
- **D-73** First sign-in creates the user with no roles and `onboarded_at = null`; the response has `needsOnboarding: true`. The web then shows a one-step role pick (followed by the verification steps of §7.3, D-89) (tenant, landlord, or both; optional home city; accept terms) that calls `POST /me/onboarding` once; the response carries a fresh access token with the new roles, and the web replaces its in-memory token. Until onboarding completes, only `GET /me`, `POST /me/onboarding`, `DELETE /me`, `/auth/*` and public reads are allowed (`403 onboarding_required` otherwise); admin sessions live on the admin host and are not held here (§13.3). "Not onboarded" is read from the token: its `roles` hold neither `TENANT` nor `LANDLORD`. The other role can be added later via `PATCH /me` (D-16), which also returns a fresh access token (D-62).
- **D-74** The GIS ID-token flow needs only the OAuth **client ID** (no client secret, no authorization-code exchange, no redirect URI on the API). So `GOOGLE_CLIENT_ID` is the only Google setting. It comes from env only and is never committed, not even as a default (it is not a secret, but it identifies the captain's Google Cloud project). `GOOGLE_CLIENT_SECRET` is not used; if a later server-side flow (e.g. Google APIs on behalf of the user) needs it, it is added as env only.
- **D-75** Token verification sits behind an interface so tests never call Google:
  ```java
  public interface GoogleTokenVerifier {
      GoogleIdentity verify(String idToken);   // throws InvalidGoogleTokenException
  }
  record GoogleIdentity(String sub, String email, boolean emailVerified, String name, String pictureUrl) {}
  ```
  Production bean: `JwksGoogleTokenVerifier` (Spring Security `NimbusJwtDecoder` with the JWKS URL and the issuer/audience validators above). Test bean (`test` profile): `FakeGoogleTokenVerifier`, which accepts tokens shaped `fake:<sub>:<email>[:<name>]` and rejects anything else, so integration tests cover new-user, returning-user, unverified-email, wrong-audience (simulated) and email-conflict cases.
- **D-76** The web has no Google build-time config: it reads `googleClientId` from `GET /auth/config` and loads the GIS script only when it is set. Pages that host the GIS popup are served with `Cross-Origin-Opener-Policy: same-origin-allow-popups` (GIS requirement); the CSP allows `https://accounts.google.com/gsi/`.
- **D-77** Dev-only login: when the `dev` or `test` profile is explicitly active (D-64) and `DEV_LOGIN_ENABLED=true` (default `true` in `dev`), the API exposes `GET /auth/dev-users` and `POST /auth/dev-login`, and the login page shows a "Dev login" picker listing the seed users (D-68), so the 4 Bangalore + 4 Pune demo data can be clicked through and screenshotted with no Google account and no network. The beans (and Swagger UI) are not created in any other profile (the endpoints return `404`), and the app **refuses to start** if `DEV_LOGIN_ENABLED=true` with any profile other than `dev`/`test`. There is no default profile, so a deployment that forgets to set one does not start (D-64). Dev login works only for `is_demo` users. When `GOOGLE_CLIENT_ID` is unset in dev, the Google button is hidden and dev login is the only way in. In `prod` without `GOOGLE_CLIENT_ID` the API starts with one warning and `POST /auth/google` answers `503 google_sign_in_disabled` (same fail-closed-but-start rule as an empty CORS list, so the existing Render service and the compose smoke test keep starting); set it before opening sign-in to users. *Changed in design v4 (B17)*: `POST /auth/dev-login/new` (same profiles and flag) signs in as a brand-new, non-demo user with no role (`needsOnboarding: true`, a made-up `@example.test` email), so the first sign-in, the role pick, the account-created screen and the stepper can be tested without Google.
- **D-78** *Removed in admin separation*: `ADMIN_EMAILS` no longer exists and is not read. Admin membership is `ADMIN_CONSOLE_EMAILS` (or the `user_roles` row under the `dev` profile only), checked on the admin host only (D-19, D-154); main-site tokens and `GET /me` never carry `ADMIN`.

### 13.2 Creating the Google OAuth client (setup steps)

- **D-79** These steps are in the root `README.md` (added with the backend auth PR, B3):
  1. Open Google Cloud Console → select or create a project (e.g. "rentrix-dev").
  2. APIs & Services → OAuth consent screen: user type **External**, app name "Doorway Living (dev)", support email, scopes `openid`, `email`, `profile` only. While in "Testing", add your own Google account as a test user.
  3. APIs & Services → Credentials → **Create credentials → OAuth client ID** → application type **Web application**.
  4. Authorized JavaScript origins: `http://localhost:5173` and `http://localhost` (GIS needs both for local dev). Add the production web origins `https://doorwayliving.in` and `https://www.doorwayliving.in` for production. Add the admin console's origins too: `https://admin.doorwayliving.in` (LIVE), `https://admin.doorway.129-225-77-1.sslip.io` (DEV) and `http://admin.localhost:5173` (local, §13.3). No redirect URIs are needed.
  5. Copy the client ID (`…apps.googleusercontent.com`). Do **not** download or commit the JSON.
  6. Export it for the API only: `export GOOGLE_CLIENT_ID=…apps.googleusercontent.com` (shell, or an untracked `.env` file; `.env` is git-ignored). The web picks it up from `/auth/config`.
  7. Start the API and web; "Sign in with Google" appears on the login page.

### 13.3 Admin console host (admin separation)

Captain's decision of 2026-11-23 00:05. LIVE `https://admin.doorwayliving.in`, DEV `https://admin.doorway.129-225-77-1.sslip.io`.

```
 admin.doorwayliving.in ──Caddy──▶ /api/*  → API (Host kept)        everything else → the same web build
   browser (admin shell)            │ Host ∈ ADMIN_HOSTS → admin request
   POST /auth/admin/google ───────▶ │ verify Google token, email ∈ ADMIN_CONSOLE_EMAILS (or user_roles ADMIN, dev only)
   ◀── admin access token (ctx: "admin", roles [ADMIN]) + rentrix_admin_refresh cookie (Path=/api/v1/auth/admin)
   GET /admin/sms-relay ──────────▶ │ admin token + admin host → 200
 doorwayliving.in ──────────────────▶ /admin/** and /auth/admin/** → 404; admin tokens → 401 token_invalid
```

- **D-154** **Admin host.** `ADMIN_HOSTS` (env, comma list of host names) names the admin console's hosts. A request is an admin request when its host (the `Host` header, or `X-Forwarded-Host` from a `TRUSTED_PROXIES` peer, §17) is listed. `AdminHostFilter` enforces: `/admin/**` and `/auth/admin/**` answer `404` off an admin host; the main site's `/auth/google`, `/auth/refresh`, `/auth/logout`, `/auth/dev-users` and `/auth/dev-login[/new]` answer `404` on an admin host, so a session there is always an admin session; an admin session reads any endpoint (with admin visibility) but writes only through `/admin/**` and `/auth/admin/**` (`403 admin_console_only`). `JwtAuthenticationFilter` accepts an admin token only on an admin host and a main-site token only elsewhere; the other one reads as `401 token_invalid`. Empty `ADMIN_HOSTS` (the `prod` default) means no admin console. The `dev` and `test` profiles default to `admin.localhost` (Chromium and Vite treat `*.localhost` as loopback), so the console runs at `http://admin.localhost:5173` against `npm run dev`; the Vite proxy sends `X-Forwarded-Host` (`xfwd`).
- **D-155** **Admin session.** `POST /auth/admin/google {idToken}` verifies the Google token like `/auth/google` (D-70) and starts a session only for a member: a verified email in `ADMIN_CONSOLE_EMAILS` or, under the `dev`/`test` profile only, a user with the `user_roles` `ADMIN` row (`403 not_an_admin` otherwise; in `prod` the row alone is refused, checked before any account is created). A listed email with no account gets a normal account with no role, so the same person signs in on the main site as a new tenant or landlord. The access token has `ctx: "admin"` and `roles: ["ADMIN"]` only; main-site tokens never hold `ADMIN`, even when `user_roles` has it. The refresh token is a separate family whose raw value starts with `adm.` (covered by the hash, so a main token cannot be turned into one), in the cookie `rentrix_admin_refresh` (`HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/admin`, host-only like the main cookie). `POST /auth/admin/refresh` rotates it (D-63) and checks membership again, so removing an email ends that console session at its next refresh (`403 not_an_admin`, family revoked); `/auth/refresh` refuses admin tokens and `/auth/admin/refresh` refuses main ones (`401`). `POST /auth/admin/logout` revokes it. Dev login on the admin host: `GET /auth/admin/dev-users` and `POST /auth/admin/dev-login {userId}` list and sign in demo users who are members (the seed's Demo Admin), under the same profile and flag as D-77; the main site's dev login no longer lists or accepts seed admins. Admin decisions check membership again in the database (D-19), so a token outliving its grant is refused. The web is one build: on a host starting with `admin.` it renders the admin shell (sign-in, OTP relay `/sms-relay`, the approvals queue from W9; mobile-first with a bottom page bar), and it calls the API at `window.__RENTRIX_CONFIG__.adminApiBaseUrl` (`ADMIN_API_BASE_URL` in the web image), else same-origin `/api/v1`. The main web never shows an admin link, badge or route.
- **D-146** **Phone code on Telegram** (captain 2026-11-23). The phone code can go through the Telegram Gateway API (`gatewayapi.telegram.org`): Telegram shows it in the Telegram app of the account registered to the number. We still generate the 6-digit code and keep only its HMAC, with the same challenge, limits, attempts and expiry as SMS (D-101); `sendVerificationMessage` gets `phone_number`, our `code` and `ttl`, and its `request_id` is stored on the challenge (`phone_otp_challenges.channel` `SMS`/`TELEGRAM`, `gateway_request_id`, V10). A correct code marks **`TELEGRAM`** `VERIFIED` (a phone method like `PHONE_OTP` and `TRUECALLER`, so it opens posting under D-144) and, after the commit, is reported to `checkVerificationStatus` (best effort, never undoes the check). `TELEGRAM_GATEWAY_TOKEN` is env only and never logged; without it the method is unavailable and hidden; with it Telegram is the **default** channel ("We send the code to your Telegram") and SMS is the fallback the user picks ("No Telegram? Get the code by SMS"), which today is the admin relay (D-137). A number Telegram cannot reach answers `503 telegram_send_failed`, uses the challenge up and points to SMS (or to Aadhaar or LinkedIn when SMS is not available). No webhook, no `callback_url` and no `getUpdates`: nothing calls us back, and the admin relay's Telegram bot (D-137) is unrelated. Dev and test use `FakeTelegramGateway` (`TELEGRAM_GATEWAY_FAKE=true`, refused elsewhere), which treats `+919000000000` as a number without Telegram and hands every other code to the admin relay when it runs, else to the dev log; the startup guard also refuses a token without `ID_HASH_KEY`.

**Deployment (Caddy, master's VM).** One block per admin host, next to the main site's; the same web container and the same API, `/api/*` proxied with the `Host` header kept (Caddy's default, which also sets `X-Forwarded-Host`):

```
admin.doorwayliving.in {
	handle /api/* {
		reverse_proxy 127.0.0.1:<api port>
	}
	handle {
		reverse_proxy 127.0.0.1:<web port>
	}
}
```

API env: `ADMIN_HOSTS=admin.doorwayliving.in` (DEV `admin.doorway.129-225-77-1.sslip.io`) and `ADMIN_CONSOLE_EMAILS=<the admins' Google emails>`; the web needs nothing (`ADMIN_API_BASE_URL` empty = same origin). Add the admin origin to the Google OAuth client's **Authorized JavaScript origins** (§13.2). No CORS entry is needed: the console calls its own origin.

---

## 14. Notifications

- **D-65** An `EmailSender` interface with two implementations: `LogEmailSender` (dev/test: writes the email to the log) and `SmtpEmailSender` (prod: any SMTP provider via `MAIL_*` env). Emails: tenancy invite, tenancy confirm request, tenancy confirmed/declined/ended, review window open, reviews published, rent record to confirm, report outcome, verification approved / rejected (with the reason), listing approved / rejected (with the reason), identity check verified / rejected (with the reason, D-105). Emails never contain document data or ID numbers. No in-app notification centre in v1. *Build note (PLAN B5)*: only `LogEmailSender` exists so far, in every profile; it logs the masked recipient and subject at `INFO` and the body (which can hold an invite link) only at `DEBUG`, which `dev` turns on. `SmtpEmailSender` comes before real users are invited.
- SMS is used for one thing only: the phone OTP code (D-101), through an `SmsSender` interface with `LogSmsSender` (dev/test: writes the code to the log), `HttpSmsSender` (prod: a provider configured by `SMS_*` env) and, from design v4, `AdminRelaySmsSender` (preview only: sends nothing and shows the pending code to admins in the admin console, also logged in `dev`; refused under `prod` without `PREVIEW_MODE=true`, D-137). The SMS text has the code and the product name (`app.brand.name`) only; the code is never logged in prod.

---

## 15. Image storage

```java
public interface ImageStorage {
    StoredObject put(Visibility visibility, String key, InputStream data, long size, String contentType);
    void delete(Visibility visibility, String key);
    String publicUrl(String key);                          // PUBLIC objects only
    String signedUrl(String key, Duration ttl);            // PRIVATE objects only
}
enum Visibility { PUBLIC, PRIVATE }
```

- **D-66** Two implementations chosen by `STORAGE_BACKEND`: `local` (default; files under `STORAGE_LOCAL_DIR`, default `./data/media`; public files served by the API at `/api/v1/media/**`, private files served only through HMAC-signed short-lived URLs) and `s3` (any S3-compatible service: AWS S3, Cloudflare R2, MinIO; `S3_ENDPOINT`, `S3_REGION`, `S3_PUBLIC_BUCKET`, `S3_PRIVATE_BUCKET`, `S3_PUBLIC_BASE_URL`, credentials from env). Code depends only on the interface.
- **D-67** Uploads: `image/jpeg`, `image/png`, `image/webp`, ≤ 15 MB each, ≤ 10 per property; content type sniffed from bytes (not trusted from the client); images re-encoded server-side to WebP (max 2048 px long side) plus a 480 px thumbnail; **EXIF stripped** (removes GPS location). Keys are random (`properties/{publicId}/{uuid}.webp`), never the original filename. Tenancy documents and verification documents (D-94, D-95): PDF/JPEG/PNG ≤ 10 MB, stored PRIVATE, `sha256` recorded, served only through `signedUrl`. `ImageStorage` is the one storage abstraction for both images and private documents.
- `property_images(property_id, storage_key, thumb_key, width, height, size_bytes, display_order, is_primary)`.
- *Design v4*: the rental-agreement PR (PLAN B18) lands before B9, so it adds the `ImageStorage` interface above with only the `local` PRIVATE implementation and HMAC signed URLs, which it needs for encrypted agreement files (D-131). B9 then adds `PUBLIC`, `s3` and image processing behind the same interface.

---

## 16. Dev-only seed data

- **D-68** A seeder runs only when the `dev` profile is active **and** `SEED_DEMO=true`, and only into an empty database. It refuses to run under `prod`. Every seeded user and property has `is_demo=true`, and the web shows a "Demo data" badge on such rows. The seeder keeps the structural trust rules (both parties confirmed, correct party per review direction, one review per direction per tenancy, the repeat-pair rule, content rules) so the seed also exercises them. It writes historical timestamps (`created_at`, `email_verified_at`, tenancy dates, review `submitted_at`/`published_at`) directly through a seed-only path, so the time-based guards (review window D-39, account age D-45, email verification D-35) are evaluated as of the seeded historical dates, not now. Seed users get `email_verified_at` set by the seeder. The seed-only path exists only in the `dev` profile.
- **D-69** Seeded people are clearly fake: display names like "Demo Landlord BLR-1", "Demo Tenant PNQ-3"; emails `…@example.test` (reserved TLD, RFC 2606); **no phone numbers**; no Google ids and no passwords (seed users sign in only through dev login, D-77). Addresses use real locality names but fictional buildings ("Demo Residency A", unit "D-101").

Seed plan (8 properties, 4 landlords, 12 tenants):

| City | Property (fictional) | Locality | Landlord | Tenancies | Rent history (₹/month) |
|---|---|---|---|---|---|
| BLR | Demo Residency A, 2BHK apt | Koramangala | Demo Landlord BLR-1 | ended 2022-04→2024-03, current 2024-05→ | 32,000 → 34,000 → 36,500 |
| BLR | Demo Heights B, 1BHK apt | HSR Layout | Demo Landlord BLR-1 | ended 2023-01→2025-06 | 22,000 → 23,500 |
| BLR | Demo Villa C, 3BHK villa | Whitefield | Demo Landlord BLR-2 | ended 2021-07→2023-06, current 2023-08→ | 48,000 → 52,000 → 55,000 |
| BLR | Demo Studio D, studio | Indiranagar | Demo Landlord BLR-2 | current 2025-02→ | 19,000 |
| PNQ | Demo Towers E, 2BHK apt | Baner | Demo Landlord PNQ-1 | ended 2022-09→2024-08, current 2024-10→ | 24,000 → 25,500 → 27,000 |
| PNQ | Demo Park F, 1BHK apt | Hinjewadi | Demo Landlord PNQ-1 | ended 2023-03→2025-02 | 16,500 → 17,500 |
| PNQ | Demo Court G, 3BHK house | Kothrud | Demo Landlord PNQ-2 | ended 2021-05→2024-04, current 2024-06→ | 30,000 → 32,000 → 34,000 |
| PNQ | Demo Nest H, room in PG | Viman Nagar | Demo Landlord PNQ-2 | two overlapping ended flatmate tenancies 2024-01→2025-01 | 9,000 each |

Every property has at least one tenancy with reviews in all three directions published (`TENANT_TO_LANDLORD`, `LANDLORD_TO_TENANT`, `TENANT_TO_PROPERTY`); these are ended tenancies where the table shows them. Demo Studio D's only tenancy is current and has been `ACTIVE` for more than 60 days, so its window is open under D-39. The seed also has a spread of ratings (1–5, not all 5s), at least one review response, one `AWAITING_REVEAL` review (to show double-blind), and one open report for the admin queue. One demo admin (`demo-admin@example.test`, role `ADMIN`, in the dev-login picker).

- **D-97** Seeded verification states, so the admin queue has items to show:
  - All 4 demo landlords and 12 demo tenants above are `APPROVED` (their seeded tenancies and reviews need it, D-91), with identity methods set by the seeder (*changed in design v3*: see D-115 for which method each user has). *Changed in design v4 (B17)*: every demo applicant (approved, pending and rejected) has a verified phone, and the demo admin gives every demo home, confirmed tenancy and published review the green tick (`VERIFIED`, decided one day after the seed time), so the demo ratings stay visible under D-135.
  - Pending queue: **Demo Landlord BLR-3** (`LANDLORD`, `PENDING`, LinkedIn given, PAN uploaded); **Demo Tenant PNQ-7** (`TENANT`, `PENDING`, no LinkedIn, rental agreement (*changed in design v4*: a `rental_agreements` row, D-115, not a verification document); *design v3*: phone verified + masked Aadhaar pending instead of PAN, D-115); **Demo Tenant BLR-7** (`TENANT`, `PENDING`, LinkedIn given, PAN (*changed in design v4*: its rental agreement is dropped, because rental agreements are `rental_agreements` rows, D-123, D-131), flagged `DUPLICATE_ID` because its fake PAN matches BLR-8's, to show the flag).
  - Rejected: **Demo Tenant BLR-8** (`TENANT`, `REJECTED`, `ID_UNREADABLE`, note "The PAN photo is blurred. Upload a sharper photo.", can resubmit).
  - Pending listing: **Demo Annex I** (Jayanagar, Bangalore, 1BHK, landlord BLR-2, `PENDING_REVIEW`, one property proof). It is not published, so the 4 + 4 published demo properties and every per-city count stay as they are.
  - Seed documents are generated placeholder images that read "DEMO DOCUMENT · NOT REAL"; their numbers are fake (last 4 like `001D`) and their HMACs come from the dev `ID_HASH_KEY`. Under D-96 only `PENDING` ID checks have an ID file (BLR-3 and BLR-7 PAN, PNQ-7 masked Aadhaar); every decided ID check (the approved users, BLR-8's rejected PAN) has its `ID_PROOF` row `PURGED` with no file. The property proof keeps its placeholder file (*changed in design v4*: rental agreements are seeded as `rental_agreements` rows with "DEMO AGREEMENT · NOT REAL" files, D-115). These users have no tenancies and no reviews. Exact review texts live in the seeder source and stay generic (no names, phones or addresses, per D-43).

- **D-115** Design-v3 seed additions (the 4 + 4 published properties and every per-city count stay as they are):
  - **Areas** (real locality names and PIN codes, D-69): BLR Koramangala (560034, 560095), HSR Layout (560102), Whitefield (560066), Indiranagar (560038), Jayanagar (560011, 560041); PNQ Baner (411045), Hinjewadi (411057), Kothrud (411038), Viman Nagar (411014). Every seeded property points at its area. Separately from the demo seed, `V11__launch_city_areas.sql` adds 10–12 real, `ACTIVE`, non-demo areas with PIN codes to each of `HYD`, `GGN`, `DEL`, `NOI` and `MUM` in every profile (skipping a name the city already has), so their users can pick an area instead of suggesting one; the demo seed ignores these rows when it checks for an empty database.
  - **One gated society per city, each with 2 towers of 2 wings**: BLR **Demo Greens** (Koramangala, `GATED_COMMUNITY`, developer "Demo Builders"): Tower 1 (Wing A, Wing B), Tower 2 (Wing A, Wing B); Demo Residency A is in Tower 1, Wing A. PNQ **Demo Meadows** (Baner, `GATED_COMMUNITY`): Tower 1 (Wing A, Wing B), Tower 2 (Wing A, Wing B); Demo Towers E is in Tower 2, Wing B. All other seeded properties are standalone (no society).
  - **Proposed location**: the pending listing Demo Annex I proposes a new society **Demo Enclave** (Jayanagar) with Tower 1, both `PENDING`, so the listing queue shows a location proposal (D-109).
  - **Owners with several properties**: BLR-1 (Residency A, Heights B), BLR-2 (Villa C, Studio D, pending Annex I), PNQ-1 (Towers E, Park F), PNQ-2 (Court G, Nest H), so every owner profile shows a portfolio and an `OWNER_HOMES` roll-up.
  - **Identity methods** (D-99–D-103), replacing the design-v2 LinkedIn checks of D-97: every approved seeded user has at least one `VERIFIED` ID method, spread over the three (landlords BLR-1 PAN, BLR-2 masked Aadhaar, PNQ-1 PAN, PNQ-2 phone; tenants alternate PAN, phone and masked Aadhaar). `LINKEDIN` is `VERIFIED` for BLR-1, PNQ-2 and Demo Tenant PNQ-2 only, so both badge states appear. Pending queue: BLR-3 (PAN and LinkedIn `PENDING`), BLR-7 (PAN `PENDING`, `DUPLICATE_ID` with BLR-8), PNQ-7 (phone `VERIFIED`, masked Aadhaar `PENDING`, so "ID verified" shows while the application waits, D-105). Rejected: BLR-8 (PAN `REJECTED`, `ID_UNREADABLE`).
  - *Changed in design v4* (D-117): every seeded tenant and landlord with an application (approved, pending or rejected) also has a `VERIFIED` `PHONE_OTP` row, so the demo users stay approved and the pending ones stay submittable; the PAN, Aadhaar and LinkedIn rows above stay as the extra badges. Rental agreements (PLAN B18): Demo Residency A's current tenancy has a `KA_ESTAMP` agreement confirmed by both sides with the anchor `VERIFIED` ("Agreement verified"), Demo Towers E's current tenancy has a registered `MH_LEAVE_LICENCE` agreement confirmed by both sides with no lookup yet ("Agreement matched"), and Demo Tenant PNQ-7's pending application carries an onboarding agreement in `SUBMITTED`. Their fields are fictional, their anchor numbers are well-formed fakes, and each has a placeholder "DEMO AGREEMENT · NOT REAL" file.
  - *Design v4 (open by default)* (D-134): the seeded published properties, confirmed tenancies and published reviews are `VERIFIED` (green tick), so the headline averages of a fresh seed stay as they are (a database seeded before this migration is not re-seeded, D-68: its demo reviews read `UNVERIFIED` after the one-time recompute until an admin ticks them); one review per city (on Demo Studio D and Demo Nest H) is left `UNVERIFIED` to show the "Not verified yet" group.
  - Seeded phone rows store only a fake `last4` (e.g. `0001`) and an HMAC from the dev key; `users.phone` stays null, so the seed still has no phone numbers (D-69). Seeded Aadhaar rows have fake last 4 like `0007`; only the pending one (PNQ-7) has a placeholder "MASKED AADHAAR · DEMO" file (D-96).

---

## 17. Configuration (env)

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | none, required | `dev`, `test` or `prod`; the API refuses to start if unset (D-64) |
| `PORT` | `8080` | |
| `DB_URL` | `jdbc:sqlite:./data/rentrix.db` in `dev`; none (required) in `prod`; the API Docker image sets `jdbc:sqlite:/app/data/rentrix.db` | MySQL/Postgres URL switches DB (D-09). The old `DB_HOST`/`DB_PORT`/`DB_NAME` are ignored. In `prod`, a `jdbc:sqlite:` URL logs one startup warning: the data lives inside the container and is lost on redeploy unless a persistent disk is mounted at `/app/data` or `DB_URL` points to a managed database |
| `DB_POOL_MAX` | `10` | connection pool size |
| `ID_NODE` | random per process | 0–1023, set per instance when running several (D-80) |
| `LOG_FORMAT` | `ecs` | structured JSON console logs (`ecs`, `logstash` or `gelf`); every line carries the request id as `traceId` |
| `DB_USERNAME`, `DB_PASSWORD` | empty | |
| `JWT_SECRET` | built-in default only in `dev`/`test` | required (≥ 32 bytes) in any other profile (D-64) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` only in `dev`/`test`; empty in `prod` | comma list; empty in `prod` logs one warning and allows no cross-origin browser request (fail closed without blocking start, so the existing Render deployment keeps starting) |
| `APP_BASE_URL` | `http://localhost:5173` in `dev`/`test`; empty in `prod` | web app URL for links in emails (tenancy invites, D-65); empty gives relative links |
| `ADMIN_HOSTS` | `admin.localhost` in `dev`/`test`; empty in `prod` | comma list of the admin console's host names (D-154); empty = no admin console |
| `ADMIN_CONSOLE_EMAILS` | empty | comma list of verified Google emails that may sign in on the admin host (D-19, D-155). *Replaces `ADMIN_EMAILS`, which is no longer read* |
| `TRUSTED_PROXIES` (every profile) | loopback and private ranges (`10.x`, `172.16-31.x`, `192.168.x`, `127.x`, `::1`) | regex of reverse-proxy IPs whose `X-Forwarded-For` / `-Proto` / `-Host` / `-Port` are honoured (Tomcat `RemoteIpValve`, `forward-headers-strategy: native`); any other peer's forwarded headers are ignored, so the sign-in rate limit (§13) keys on the real client IP and a client cannot fake its origin. See **Behind a reverse proxy** below |
| `ID_HASH_KEY` | built-in default only in `dev`/`test` | HMAC key for PAN, phone and OTP codes (D-101, D-102, D-106); a set value must be ≥ 32 bytes. Required in any other profile, like `JWT_SECRET`, from PLAN B12 (PAN). Until then (B14) it is required outside `dev`/`test` only with `SMS_BACKEND=http`, so a deployment without an SMS provider keeps starting; without the key, phone OTP is unavailable (`503`) |
| `SMS_BACKEND` | `log` in `dev`/`test`; none in `prod` | `log`, `http` (D-101) or `admin-relay` (*design v4*, preview only, D-137); without it in `prod` the phone method is unavailable (`503 phone_otp_unavailable`) and the API still starts; `log` in `prod` refuses to start; `admin-relay` in `prod` refuses to start unless `PREVIEW_MODE=true`, and logs one `WARN` naming `admin-relay` on every start |
| `PREVIEW_MODE` | `false` | *design v4*: `true` marks a preview deployment and allows `SMS_BACKEND=admin-relay` under `prod` (D-137). Live mode = `PREVIEW_MODE` off and `SMS_BACKEND=http` configured |
| `ADMIN_RELAY_TELEGRAM_BOT_TOKEN`, `ADMIN_RELAY_TELEGRAM_CHAT_IDS` | — | optional with `SMS_BACKEND=admin-relay` (D-137): both set pushes each held code to these Telegram chats (chat ids: comma list); the token is a secret, env only, never logged |
| `TRUECALLER_APP_KEY`, `TRUECALLER_APP_NAME` | — (a fake key in `dev`/`test`) | D-138: the Truecaller web app's key (`partnerKey`) and name (`partnerName`, default "Doorway Living"), one app per deployment. Secrets from env only, never logged. No key = no "Verify with Truecaller" button |
| `TRUECALLER_ENABLED` | `true` | D-138: `false` hides the Truecaller button even with a key set |
| `TRUECALLER_FAKE` | `true` in `dev`/`test`; `false` | D-138: `FakeTruecallerVerifier` instead of Truecaller (token `fake:<number>`); `true` outside `dev`/`test` refuses to start |
| `SMS_HTTP_URL`, `SMS_API_KEY`, `SMS_SENDER_ID` | — | only with `SMS_BACKEND=http` (URL and key required, else the API refuses to start); `HttpSmsSender` posts JSON `{to, sender, message}` with `Authorization: Bearer SMS_API_KEY`; a provider with another request shape gets its own `SmsSender`. Secrets from env only |
| `OTP_TTL_MINUTES` | `10` | phone OTP lifetime (D-101) |
| `AGREEMENT_FILE_KEY` | built-in default only in `dev`/`test` | *design v4*: AES-256-GCM key for rental-agreement files (D-131), ≥ 32 bytes, env only; required in any other profile once PLAN B18 ships (the API refuses to start without it) |
| `AGREEMENT_EXTRACTOR` | `manual` | D-127; `manual` is the only accepted value until a vision extractor is approved |
| `AGREEMENT_FILE_RETENTION_DAYS` | `30` | D-131: days after the later of the admin decision and the other party's confirmation before an agreement file is deleted (90 days after upload when no other party is linked) |
| `VERIFICATION_DOC_RETENTION_DAYS` | `90` | D-96: property proofs only (ID files go at decision; rental agreements follow D-131 and `AGREEMENT_FILE_RETENTION_DAYS`) |
| `GOOGLE_CLIENT_ID` | none (never committed) | Google sign-in (D-70, D-74); empty in `prod` logs one warning and turns Google sign-in off (`503`, D-77) |
| `DEV_LOGIN_ENABLED` | `true` in `dev`, `false` in `test` | D-77; any profile other than `dev`/`test` refuses to start if `true` |
| `STORAGE_BACKEND` | `local` | `local` or `s3` (D-66) |
| `STORAGE_LOCAL_DIR` | `./data/media` | |
| `S3_*` | — | only when `s3` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | — | prod email |
| `SEED_DEMO` | `false` | D-68 |
| `RATINGS_HEADLINE` | `all` | D-135: `all` or `verified` (green-tick reviews only); rebuilt by the nightly repair after a switch |
| `SCHEDULING_ENABLED` | `true`; `false` in `test` | in-process scheduled jobs (D-08) |
| `VITE_API_BASE_URL` (web, build-time) | `/api/v1` | overridden at runtime by `config.js` (D-04) |
| `window.__RENTRIX_CONFIG__.apiBaseUrl` (web, runtime, `dist/config.js`) | empty (use build value) | D-04 |
| `API_PROXY_TARGET` (web dev server only) | `http://localhost:8080` | where `npm run dev` proxies `/api` |
| `API_BASE_URL` (web Docker image, run time) | empty | written into `/config.js` at container start; must be a URL the browser can reach (D-04) |
| `ADMIN_API_BASE_URL` (web Docker image, run time) | empty | `adminApiBaseUrl` in `/config.js`: where the admin console calls the API (D-155); empty = same-origin `/api/v1` on the admin host |

**Behind a reverse proxy** (Caddy on the VM, Render): the proxy terminates TLS and forwards to the API over plain HTTP, for example Caddy to `127.0.0.1:8090`. The browser's `Origin` is the public origin (`https://…`), so without the proxy's `X-Forwarded-Proto` / `-Host` the API sees `http://127.0.0.1:8090`, treats a same-origin call as cross-origin and refuses every `POST` with `403` unless the public origin is in `CORS_ALLOWED_ORIGINS`. The API honours those headers from `TRUSTED_PROXIES` in every profile, so a same-origin deployment needs no CORS entry for itself. Set `TRUSTED_PROXIES` to the proxy hop's IP when that hop is not loopback or a private range; keep `CORS_ALLOWED_ORIGINS` for web origins that really differ from the API's (`doorwayliving.in` calling `api.doorwayliving.in`).

**Upgrading from the old app**: v1 starts from a fresh, empty database (new Flyway V1 baseline); point `DB_URL` at a new database, because the old schema and data are not migrated and are left as they are. See `rentrix-server/README.md` for the env changes.

A local developer needs nothing but Java 21 and Node: SQLite file + local media folder + dev login, no MySQL, no S3, no Docker, no Google account. Google sign-in is optional locally (set `GOOGLE_CLIENT_ID`).

---

## 18. Path to scale

The v1 shape grows without a rewrite:

1. **Single box** (launch): one API instance + SQLite + local media + static web on a CDN/static host. Mount a persistent disk at `/app/data` (SQLite file and media); without one, a container redeploy loses all data, and `prod` logs a warning at startup.
2. **Managed DB**: set `DB_URL` to Postgres (recommended) or MySQL; run 2+ API instances behind a load balancer (stateless, D-06). Switch `STORAGE_BACKEND=s3` with a CDN in front of the public bucket.
3. **Read load**: property search and aggregates are already precomputed (D-33); add HTTP caching (`Cache-Control`, `ETag`) on public property and rating endpoints, then a DB read replica.
4. **Search**: Postgres full-text on locality/building; move to OpenSearch only if needed.
5. **Background work**: the in-process scheduler (D-08) moves to a queue (e.g. SQS) only when email volume or image processing needs it.
6. **Rate limits**: in-memory per instance in v1 (Bucket4j); move to a shared Redis store when running many instances.
7. **New cities**: an admin adds the `cities` row and activates it (D-83); pickers, search and copy follow from the data; no code change or release.

---

## 19. Not in v1

- Payments, rent collection, deposit escrow.
- In-app chat or messaging (enquiries only, D-31).
- Listing marketplace features: availability calendars, visit booking, brokerage.
- Tenancy verification by document alone when the other party is not on Rentrix ("unclaimed landlord"); v1 needs both parties (D-34).
- Property ownership transfer, co-owners, agencies/property managers.
- Email/password login, phone OTP **login**, and social logins other than Google (D-70, D-71). Phone OTP is used only to verify a phone (D-101).
- Automated KYC (Aadhaar e-KYC, PAN verification APIs, DigiLocker): v1 PAN and masked-Aadhaar checks are admin-reviewed uploads with masked numbers; a provider can be added behind `IdentityCheckProvider` later (D-104). Any full Aadhaar number, in any form (D-103).
- Passport, driving licence and voter ID as ID methods (dropped in design v3; they can return as further methods).
- Automated LinkedIn verification (D-93).
- Automatic extraction of rental-agreement fields (OCR or LLM vision) and automatic redaction of Aadhaar numbers inside uploaded files: v1 uses the manual form and encrypted storage (D-127, D-131). Any automated or scraped lookup on government portals (SHCIL, IGR Maharashtra, Kaveri): the lookup is a manual admin step (D-130).
- External PIN-code lookup or import (areas are seed and admin data in v1, D-108); merging duplicate societies (admins point listings at the existing row instead, D-109); tower or wing pages of their own (they are sections of the society page).
- Native mobile apps (the web is responsive).
- Push / in-app notifications (email only, D-65).
- ML fraud detection (rule-based flags only, D-45).
- Review translation, photos in reviews.
- Migration of data from the v0.1.x schema (treated as test data, D-01).

---

## 20. Superseded documents

These are marked obsolete with a pointer to this file:
`architecture/ARCHITECTURE.md`, `architecture/EntityRelationships.txt`, `architecture/ROADMAP.md`,
`architecture/UI folder structure.txt`, `architecture/backend.md`, `architecture/frontend.md`, `architecture/security.md`,
`EntityRelationships.txt`, `api/*.md`, `database/sample-data.md`,
and the SQL seed text files in `rentrix-docs/` and `rentrix-docs/database/`.
*Changed in design v3*: `database/schema.md` (table reference for the design-v3 tables and migration plan) and `database/er-diagram.md` (location hierarchy and identity ER diagrams) are current again; their decisions live here.
`architecture/SETUP.md` (frontend tooling setup) and `configuration/*` stay until the implementation PRs replace them.
