# Rentrix — System Design (v1, two-sided trust platform)

Status: **current**. This document is the single source of truth for the v1 rebuild.
It supersedes every older architecture, API and database doc in `rentrix-docs/` (see §20).
Date: 2026-11-21. Revised 2026-11-21 (design v2): data-driven cities (§5.2), admin console (§8.1), onboarding with approvals (§7.3), verification documents (§5.13–§5.14). Revised 2026-11-22 (**design v3**): a profile is ID verified with any one of Aadhaar, PAN or phone OTP, LinkedIn is an optional bonus badge (§7.4, D-99–D-106); location hierarchy City → Area → Society → Tower → Wing → Unit (§5.17–§5.19, D-107–D-110); rating roll-ups per society, area and owner and browsing by them (D-111–D-113); migrations and seed for both (D-114, D-115).

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
- Every account that writes trust data (tenancies, reviews, listings) is approved by an admin first (§7.3). Reading needs no approval.
- A profile (tenant or landlord) is **ID verified** when at least one of Aadhaar, PAN or phone (OTP) is verified. LinkedIn is an optional bonus badge, never required (§7.4).
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
  - **No migration needed** for the roll-ups (D-111): `rating_aggregates.subject_type` is `VARCHAR(16)` with no `CHECK`, so the new values `SOCIETY`, `AREA`, `OWNER_HOMES` fit.

---

## 4. Roles

- **D-16** A `User` has a role set: any of `TENANT`, `LANDLORD`, plus `ADMIN` for staff. One person can be both tenant and landlord with one account. A user picks at least one of `TENANT`/`LANDLORD` on first sign-in (onboarding, D-73) and can add the other later from settings. Roles are never removed by the user once they are attached to a tenancy.
- **D-17** The role a user plays **in a tenancy** comes from the tenancy (`landlord_id` / `tenant_id`), not from the account. The account role set only gates actions such as "create a property" (needs `LANDLORD`) or "send an enquiry" (needs `TENANT`).
- **D-18** A user has two separate reputations: **as landlord** and **as tenant**. They are aggregated and shown separately and never mixed.
- **D-19** `ADMIN` is never self-assigned and there is no self-service path to it. It comes from config or the database only: (a) at sign-in, to verified Google emails listed in `ADMIN_EMAILS` (env, D-78); (b) an existing admin grants or revokes it from the admin console, which writes or deletes the `user_roles` row (the DB flag). Nobody can grant or revoke their own `ADMIN`; an `ADMIN_EMAILS` admin is removed only by editing the env. Every grant and revoke is audited (D-87). In the dev seed, `demo-admin@example.test` has `ADMIN` and signs in through dev login (D-77, D-97). Admins cannot write reviews in their admin capacity; an admin who is also a party to a tenancy acts as a normal user there (and D-88 applies).

| Role | Can do |
|---|---|
| Anonymous | Browse cities, areas, societies and properties, see property, society and area cards with aggregate rating and count, see public landlord summary (name, aggregate as landlord, badges). Sign up / log in. |
| Logged in, verification not approved | Read everything a logged-in user can read (all review texts, rent history, landlord profiles; D-55, D-56). Fill in and submit verification (§7.3). Report content, withdraw own reviews, delete the account. No trust writes (D-91). |
| `TENANT` (approved) | Everything a logged-in user can, plus: send enquiries, start a tenancy request on a property, confirm tenancies, review landlord and property of own confirmed tenancies. |
| `LANDLORD` (approved) | Everything a logged-in user can, plus: create and manage own properties and submit them for listing approval, invite tenants to tenancies, confirm tenancy requests, review tenants of own confirmed tenancies, view profiles of tenants who enquired. A not-yet-approved landlord can save property drafts only. |
| `ADMIN` | Admin console (§8.1): view, search and filter all records; approve or reject landlord and tenant verification, identity checks (PAN, masked Aadhaar, LinkedIn) and property listings with their new areas or societies; moderation (reports queue, hide/restore content, void tenancies, suspend users); activate cities; grant admin; view the audit log. |

Approval is per role: a user who is both tenant and landlord has one verification for each (D-89).

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
User 1───0..4 IdentityVerification (one per method: AADHAAR, PAN, PHONE_OTP, LINKEDIN)
User 1───N VerificationDocument N───0..1 VerificationApplication | IdentityVerification | Property (listing proof)
User 1───N PhoneOtpChallenge
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
| `display_name` | 2–60 chars, initialised from `google_name`, user-editable. Shown publicly as first word + last initial, e.g. "Demo L." |
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
| `pincode_prefixes` | comma list of pincode prefixes, e.g. `56` for `BLR`, `41` for `PNQ` (added by the cities migration, D-84) |

- **D-20** Cities are data, not an enum. The `cities` table is the **only** list of cities: no city code, name, count or "only these cities" rule is hard-coded anywhere in the API, the web, UI copy or error messages (test fixtures may use the seed codes). Launch rows: `BLR` Bangalore (alias Bengaluru, Karnataka) and `PNQ` Pune (Maharashtra), both `live=true`. The API rejects any `city` that is not a live row (`422 city_not_live`, detail "Rentrix is not live in this city yet."). City pickers and filters list only `GET /cities` results. The 4 + 4 demo properties of the seed stay as they are (§16).
- **D-82** `GET /cities` returns the active cities as `{code, name, aliases[], state}`, ordered by name. Optional `?q=` matches code, name, any alias or state, case-insensitive, as a prefix starting at any word, so multi-word queries work ("beng" finds Bangalore through "Bengaluru"; "navi mumbai" and "mumbai" both find Navi Mumbai). The web city picker filters the same fields client-side over the loaded list and calls `?q=` only when the list is paged (more than 200 active cities, not expected in v1). `GET /properties?city=` takes a code; free text never reaches the property search.
- **D-83** Adding a city is a database row, not a release: an admin creates it from the admin console (`POST /admin/cities`, created inactive) or a Flyway data migration inserts it, then an admin activates it (`PATCH /admin/cities/{code}` with `live=true`). Activate, deactivate and edits are audited (D-87). Deactivating hides the city from pickers, search and new properties; existing properties, tenancies, rent history and reviews in it stay readable by direct link (like archived, D-21) and their parties can still end tenancies and review. Codes are never reused.
- **D-84** Pincode rule from data: a property's 6-digit pincode must start with one of its city's `pincode_prefixes`. A city with no prefixes accepts any valid 6-digit pincode. This replaces the hard-coded `56xxxx`/`41xxxx` rule.
- **D-85** City UI everywhere is driven by the list: the city control is a searchable picker (combobox) on Home, Search, onboarding (home city), the property form and admin filters, never a fixed two-option toggle. Home shows one city card per active city with counts from `GET /stats` (a city with none shows "No properties yet", D-03), ordered by published property count, then name; the first 8 show, then "Show all N cities". Launch copy is built from the list: with up to 3 active cities it names them ("Starting in Bangalore and Pune, more cities coming"; "A, B and C" for three), with more it counts them ("Live in 8 cities, more coming"). The product never says "only".

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
| `review_reason`, `reviewed_by`, `reviewed_at` | last listing decision (added by the verification-core migration, D-98) |
| `is_demo`, `deleted_at` | |

- **D-21** Only users with `LANDLORD` create properties. A property is visible to others only when `PUBLISHED`; it reaches `PUBLISHED` only through admin listing approval (D-92). Archiving hides it from search but keeps its tenancies, rent history and reviews readable via direct link.
- *Build order (PLAN B4a/B4b)*: the first properties slice ships before the location hierarchy (B15) and the approval gate (B11, B12) are built. Until B4b, `locality` is client text (trimmed, single-spaced), the location lock covers city, locality, building, street and pincode, and `submit` checks only the state; B4b switches to the area-based location of D-110 and adds the gates. No deployment has property rows before then, so nothing is backfilled.
- **D-22** Ownership transfer between landlords is not in v1. The tenancy stores its own `landlord_id`, so history stays correct if transfer is added later.
- After publishing, the location fields (area, society, tower, wing, building, street, pincode) are locked like the design-v2 location fields (D-92).

### 5.4 Tenancy (`tenancies`, has `public_id`)
| Column | Notes |
|---|---|
| `property_id`, `landlord_id`, `tenant_id` | `landlord_id` must equal the property's landlord at creation; `landlord_id ≠ tenant_id` |
| `start_date`, `end_date` | `end_date` null = current; `start_date ≤ today`; `end_date ≥ start_date` |
| `initial_rent_inr`, `deposit_inr` | rent at start (becomes the first RentRecord); deposit optional |
| `deposit_refunded_inr` | optional, set when ending |
| `status` | see §6 |
| `decline_reason` | reason given by the counterparty on decline (D-34) |
| `initiated_by` | `LANDLORD` or `TENANT` |
| `invite_email` | when the counterparty has no account yet |
| `confirmed_at`, `ended_at`, `voided_at`, `void_reason` | |
| `evidence_level` | `MUTUAL` (both confirmed) or `DOCUMENT` (admin checked an uploaded agreement) |

- **D-23** One tenancy row = one tenant on one property for one continuous period. Flatmates are separate tenancies on the same property and may overlap. The same tenant cannot have two overlapping tenancies on the same property.
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

- **D-25** The first RentRecord is created from `initial_rent_inr` and is `CONFIRMED` when the tenancy is confirmed. Later records (rent increases) are added by either party and become `CONFIRMED` only when the other party confirms. Only `CONFIRMED` records appear in public rent history.
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
  `action` adds, to the D-51 set: `APPROVE_APPLICATION`, `REJECT_APPLICATION`, `REVOKE_APPROVAL`, `APPROVE_PROPERTY`, `REJECT_PROPERTY`, `VIEW_DOCUMENT`, `CREATE_CITY`, `UPDATE_CITY`, `ACTIVATE_CITY`, `DEACTIVATE_CITY`, `GRANT_ADMIN`, `REVOKE_ADMIN`, and from design v3 `VERIFY_IDENTITY`, `REJECT_IDENTITY`, `REVOKE_IDENTITY` (replacing design v2's `SET_LINKEDIN_CHECK`), `CREATE_AREA`, `UPDATE_AREA`, `APPROVE_LOCATION`, `UPDATE_LOCATION`. `target_type` adds `APPLICATION`, `DOCUMENT`, `CITY`, `IDENTITY`, `AREA`, `SOCIETY`, `TOWER`, `WING`.

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

Previous revisions are not overwritten. `verification_events(application_id, revision, event, actor_id, code, note, created_at)`, append-only, keeps the history: `event` ∈ `SUBMITTED`, `APPROVED`, `REJECTED`, `REVOKED`, `IDENTITY_CHECKED`. Admin events are also written to `moderation_actions` (D-87) in the same transaction; the record detail merges both into one timeline.

### 5.14 VerificationDocument (`verification_documents`, has `public_id`, verification-documents migration)
Private files uploaded for verification (D-95).
| Column | Notes |
|---|---|
| `owner_id` | the uploading user |
| `kind` | `ID_PROOF`, `RENTAL_AGREEMENT`, `PROPERTY_PROOF` |
| `identity_verification_id` | set for `ID_PROOF`: the PAN or Aadhaar check it proves (§5.15) |
| `application_id` | set for `RENTAL_AGREEMENT` |
| `property_id` | set for `PROPERTY_PROOF` (listing approval, D-92) |
| `id_type` | `ID_PROOF` only: `PAN` or `AADHAAR_MASKED` (D-102, D-103). *Changed in design v3*: passport, driving licence and voter ID are dropped; the masked number fields moved to `identity_verifications` |
| `storage_key`, `content_type`, `size_bytes`, `sha256` | PRIVATE object (D-66) |
| `status` | `UPLOADED`, `ACCEPTED`, `REJECTED`, `PURGED` (file deleted: `ID_PROOF` as soon as its check is decided, other kinds by retention, D-96) |
| `reviewed_by`, `reviewed_at`, `purge_after`, `deleted_at` | `purge_after` is null for `ID_PROOF` (its file never waits for the retention job) |

### 5.15 IdentityVerification (`identity_verifications`, has `public_id`, identity migration)
One row per user per method; the user-level record behind the "ID verified" and "LinkedIn verified" badges (D-99, D-100). It serves both roles: a user who is tenant and landlord verifies once.
| Column | Notes |
|---|---|
| `user_id`, `method` | unique together; `method` ∈ `AADHAAR`, `PAN`, `PHONE_OTP` (ID methods) and `LINKEDIN` (bonus, never counts as ID) |
| `status` | `PENDING` (submitted, waiting for an admin check), `VERIFIED`, `REJECTED`, `REVOKED` |
| `last4` | `PAN`: last 4 characters; `AADHAAR`: the last 4 digits printed on the masked Aadhaar; `PHONE_OTP`: last 4 digits of the phone; null for `LINKEDIN`. The full value is never stored (D-106) |
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
- `society_wings`: `tower_id` (FK), `name` (e.g. "Wing A"), `name_key` (unique per tower), `sort_order`, `status`. A tower may have no wings.

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
- **D-109** Creating location rows. Landlords pick existing rows in the property form (area, then society, tower, wing). If one is missing they may propose it inline ("Add a society", "Add a tower", "Add a wing", "Suggest an area"); proposed rows are created `PENDING`, linked to the draft, and visible only to the proposer and admins. A proposal whose name matches a `PENDING` row under the same parent joins that row instead of creating a duplicate (the home goes on it, a joined area gains the joiner's PIN code, and it keeps its first proposer, so its approval or rejection covers every home on it); a matching `ACTIVE` or `HIDDEN` row is a `409 location_exists`. By id (detail pages, property filters, reuse in a draft) a `PENDING` row is also readable by a landlord who has a home on it; lists and pickers still show it only to its proposer and admins, and errors never show details of a `PENDING` row the caller cannot read. Listing approval (D-92) approves them together with the property; the admin can instead point the listing at an existing row when the proposal is a duplicate (`UPDATE_LOCATION`, audited). Admins also create, rename and hide areas, societies, towers and wings directly in the admin console. Rows are never hard-deleted; hiding keeps existing properties readable.
- **D-110** Placement rules, checked by the API on create and edit: `area_id` is required; `wing_id` needs `tower_id`, `tower_id` needs `society_id`; the wing belongs to the tower, the tower to the society, the society to the area, and the area to `city_code` (`422 location_mismatch`). `locality` is copied from the area. Location fields are locked after publish (D-92).

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

- **D-34** Two-party confirmation is the trust anchor. A tenancy starts as `PENDING_CONFIRMATION` and becomes `ACTIVE` (or `ENDED`, if created with an end date in the past) only when the **other** party confirms. Unconfirmed tenancies enable nothing: no reviews, no public rent history.
  - Landlord-initiated: landlord picks own property, enters tenant email, dates, rent. Tenant gets an email invite; they sign in with Google from the link (creating an account if needed), then confirm. The invite binds only to an account whose verified Google email equals `invite_email`.
  - Tenant-initiated: tenant picks a published property, enters dates and rent. The property's landlord confirms or declines.
  - The counterparty sees all fields before confirming and can decline with a reason; corrections are made by declining and re-creating (no counter-offer flow in v1).
- **D-35** Guards at creation: the initiator's verification for their side is `APPROVED` (D-91), and the counterparty's must be `APPROVED` before they can confirm, while declining needs no approval (an invitee without an account signs in, onboards and is approved first; the 14-day expiry still runs, so admins aim to decide within 48 h); both accounts email-verified; `landlord_id ≠ tenant_id`; dates valid; no overlapping tenancy for the same tenant + property; a user may create at most 5 pending tenancies at once; an invite email cannot be the initiator's own email or an existing account with a different email in the payload.
- **D-36** Ending: either party sets `end_date` (≤ today). The other party is notified and can dispute within 14 days (→ report `FAKE_TENANCY` / admin review). An admin `VOID` (fake or mistaken tenancy) hides all its reviews, removes them from aggregates and removes its rent records from rent history; it is logged in `moderation_actions`.
- `evidence_level` is `MUTUAL` after confirmation. If a party uploads an agreement and an admin accepts it, it becomes `DOCUMENT`, shown as a stronger badge ("Agreement checked"). Document upload is optional in v1.

---

## 7. Review lifecycle and trust rules

### 7.1 Trust rules
- **D-37** Only a party of a tenancy in status `ACTIVE` or `ENDED` can review, and only in the directions their side allows: the tenant writes `TENANT_TO_LANDLORD` and `TENANT_TO_PROPERTY`; the landlord writes `LANDLORD_TO_TENANT`.
- **D-38** One review per direction per tenancy (`UNIQUE(tenancy_id, direction)`). So each tenancy yields at most three reviews.
- **D-39** Review window: opens when the tenancy has been `ACTIVE` for 60 days, or when it `ENDED` (whichever is first); closes 180 days after `end_date`. While the tenancy is current the window stays open.
- **D-40** A review written during an `ACTIVE` tenancy may be **updated once** after the tenancy ends (within the closing window), as long as it is still `AWAITING_REVEAL` (D-41); a published review gets no post-end update. The update creates a revision and resets `edit_until`.
- **D-41** Edit window: the author may edit text and ratings for **7 days** after submission, and only while the review is `AWAITING_REVEAL`. Once it or its counter-review publishes (D-46), or a property review publishes at once (D-47), its text and ratings are frozen, so neither side can react to the other's review. Once frozen, only admins can hide a review; the subject's one response (§5.7) is unaffected. Authors may withdraw (delete) their own review at any time; withdrawal removes it from aggregates and cannot be undone for that tenancy+direction.
- **D-42** The subject can never edit, delete or hide a review about them. Their tools are one public response (§5.7) and reporting (§8).
- **D-43** Content rules checked at submit (rejects with `422`): length 30–2000; no phone numbers, emails, URLs or unit numbers in the body (regex detection); basic profanity list. A unit number is flagged only with an explicit marker (`flat|unit|apt|apartment|house|door|room` followed by `no`, `no.`, `number` or `#`), a block-unit code tied to those words or to `tower|wing|block` (`flat B 402`, `tower C-1204`, `wing #1203`), a bare code like `A-1203`, or the home's own unit label. Numbers followed by years, months, weeks, days, km or min, and ranks like `#1`, are never flagged. These keep personal data out of public text.
- **D-44** Repeat-pair rule: if the same landlord and tenant have more than one tenancy together, only the **most recent** tenancy's person reviews count in their aggregates (older ones stay visible with `counts_in_aggregate=false`). Property reviews from the same tenant: only the latest counts for that property. This stops a colluding pair from inflating ratings by cycling tenancies.
- **D-45** Account-age and volume guards: a user's first review needs an email-verified account at least 7 days old; at most 10 reviews submitted per user per 24 h. Admin dashboards flag landlords whose tenants are all created through landlord invites to brand-new accounts (signal for fake tenants).

### 7.2 Review states and reveal

```
 submit ──▶ checks pass ──▶ AWAITING_REVEAL ──(other side submits | 14 d | window close)──▶ PUBLISHED
                    │                                                                  │
                    └──(property review)──────────────────────────────▶ PUBLISHED      │
                                                                                       ▼
                                       WITHDRAWN ◀── author        HIDDEN ◀── admin ──▶ (restore) PUBLISHED
```

- **D-46** Double-blind reveal for the landlord↔tenant pair: a person review stays `AWAITING_REVEAL` (visible only to its author) until the counter-review for the same tenancy is submitted, or 14 days pass since the first of the two was submitted, or the review window closes. Then both publish together. This prevents retaliation reviews.
- **D-47** `TENANT_TO_PROPERTY` reviews publish immediately after checks (there is no counter-review).
- **D-48** Post-moderation: published reviews are live without admin approval (the tenancy gate replaces the old `PENDING` admin approval). Admin action happens on reports and automated flags. Admin approval applies to **people and listings** (§7.3), not to each review: an approved party of a confirmed tenancy publishes without a further check.

### 7.3 Onboarding with approvals

```
 Google sign-in ─▶ role pick (D-73) ─▶ verification, per role
                                         1 Profile   2 Verify ID (≥1 of Aadhaar · PAN · phone OTP)   3 LinkedIn (optional)   4 Rental agreement (tenant, optional)
                                         └──────────────────────────── DRAFT ──────────────────────────────────────┘
                                                              │ submit
                                                              ▼
                                  ┌──────────────────────── PENDING ──── admin ──▶ APPROVED ─▶ trust writes open
                                  │ resubmit (revision+1)        │                     │
                                  └──── REJECTED (code + note) ◀─┘ admin               └─ admin revoke ─▶ REJECTED
```

- **D-89** Onboarding has two layers. (1) Account onboarding: the first-sign-in role pick (D-73), unchanged. (2) Verification: for each role the user holds, one `verification_application` (§5.13) that an admin approves. Steps, in order, each saved as a draft: **Profile** (display name, home city from the city picker); **Verify ID**: at least one of Aadhaar, PAN or phone OTP (D-99, D-101–D-103); **LinkedIn**, optional, can be skipped (D-93, D-100); **Rental agreement**, tenant only and optional ("if you rent now or rented before"; it helps the admin, and tenancy-level agreements stay in §5.8). Landlords prove each property at listing time instead (D-92). Then **Submit** → `PENDING`. Adding the other role later (D-16) starts a second application; identity checks are per user (§5.15), so a `VERIFIED` method counts for the new role at once and nothing is uploaded again, but an admin still approves the new role. *Changed in design v3*: the ID step comes second and offers three methods; LinkedIn moved after it and is optional.
- **D-90** States: `DRAFT` → (user submits) `PENDING` → (admin) `APPROVED` or `REJECTED`. A rejection needs a reason code and a note, both shown to the user; the user edits any step and resubmits (`PENDING`, `revision + 1`), with no limit except the D-45-style rate limit (3 submissions per 24 h). An admin can revoke an approval (→ `REJECTED`, with reason) when new facts appear; suspension (D-51) stays the tool for abuse. Every submit and decision is audited (D-87). Users get an email on each decision (D-65). Target: decisions within 48 h; the admin dashboard shows the oldest waiting item.
- **D-91** The approval gate. **Reading never needs approval**: browsing, search, property pages, rent history, landlord profiles and every review text follow only the visibility rules of D-54–D-56. An `APPROVED` application for the matching role **and** at least one `VERIFIED` ID method (D-99, checked together, D-105) are required to: create a tenancy request or confirm a tenancy; add, confirm or dispute a rent record; write or edit a review; post a review response; send an enquiry (`TENANT`); submit a property for listing (`LANDLORD`). Otherwise the API returns `403 approval_required` with `{role, status}`. The check reads the application from the database on each gated write (not from the JWT), so a decision takes effect at once. Safety exits are never gated: report content, withdraw an own review, decline a tenancy request, cancel an own pending request, end a tenancy already confirmed, delete the account. Admins are not gated in their admin actions.
- **D-92** Property listing approval. Statuses: `DRAFT` → (landlord submits) `PENDING_REVIEW` → (admin) `PUBLISHED`, or `REJECTED` with reason → (landlord edits) → `PENDING_REVIEW`; `PUBLISHED` → `ARCHIVED` as before. Submitting needs an approved landlord and one `PROPERTY_PROOF` document for that property (a rental agreement for it, or proof of ownership such as a property-tax receipt or electricity bill in the landlord's name). After publishing, location fields (city, locality, building, street, pincode) are locked; to change them the landlord asks an admin. Facts, description and images stay editable without re-review. The 4 + 4 seeded demo properties are `PUBLISHED`.
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

- **D-99** **Verified = any one ID method.** A user is *ID verified* when at least one of `AADHAAR`, `PAN` or `PHONE_OTP` in `identity_verifications` is `VERIFIED`. LinkedIn is an optional bonus: it never counts towards ID verified and is never required. The rule is the same for tenants and landlords, and it is per user, so one check serves both roles. This replaces every design-v2 rule that made LinkedIn a step every applicant had to answer.
- **D-100** **Badges.** Two public badges, shown on tenant and landlord profiles, on property-page landlord cards and on review author lines: **"ID verified"** (shield-check, teal) when D-99 holds, and **"LinkedIn verified"** (link icon, info blue) when the `LINKEDIN` row is `VERIFIED`. Badges are a yes/no signal only: the public never sees which ID method was used, the masked value, the LinkedIn URL or any reject reason (D-57, D-95). Search and browse cards show the landlord's "ID verified" badge; a "LinkedIn verified" landlord also gets the second badge, which gives them the edge without being required. Tenant badges are visible only where the tenant profile is visible (D-54).
- **D-101** **Phone OTP** (self-service, no admin). `POST /me/identity/phone/start {phone}` validates an Indian mobile number (E.164 `+91` and 10 digits starting 6–9), creates a `phone_otp_challenges` row and sends a 6-digit code by SMS through the `SmsSender` interface (`LogSmsSender` in dev/test writes it to the log; `HttpSmsSender` for a provider in prod, `SMS_*` env). The code is stored only as an HMAC, expires after 10 minutes and allows 5 attempts; a user can request 3 codes per hour and 10 per day, and one phone number gets at most 5 codes per day across all accounts (`429` with `Retry-After`). `POST /me/identity/phone/confirm {challengeId, code}` marks `PHONE_OTP` `VERIFIED`, stores `last4` + `value_hmac` and sets `users.phone`. If the same phone is verified on another active account, both applications get the flag `DUPLICATE_PHONE` for the admin (shared family phones exist, so nothing is blocked). Changing the phone (`PATCH /me`) sets the old row to `REVOKED` with `PHONE_CHANGED`. In `prod` with no SMS provider configured, the phone method is hidden (`GET /me/identity` lists it as unavailable) and its endpoints return `503 phone_otp_unavailable`; the API still starts. This is verification of a phone, not a login method: sign-in stays Google only (D-70).
- **D-102** **PAN** (admin-checked upload now). The user types the PAN and uploads the card (PDF, JPEG or PNG, ≤ 10 MB) through `POST /me/documents` (`kind=ID_PROOF`, `idType=PAN`). The API checks the format `^[A-Z]{5}[0-9]{4}[A-Z]$`, stores only `last4` and `value_hmac` on the `PAN` row (`PENDING`), and drops the typed value. An admin compares the name and number on the card with the profile and the masked last 4, then marks it `VERIFIED` or `REJECTED` (`ID_UNREADABLE`, `ID_NAME_MISMATCH`). The card file shows the full PAN, so it is kept only until that decision and deleted as soon as it is recorded (D-96); a resubmission uploads a new file. The same `value_hmac` on two accounts flags both applications `DUPLICATE_ID`; nothing is rejected automatically.
- **D-103** **Aadhaar, masked copy only.** To stay within the Aadhaar Act's limits on collecting and storing Aadhaar numbers, Rentrix never asks for, receives, stores or logs a full 12-digit Aadhaar number. The user downloads the **masked Aadhaar** from UIDAI (it prints only the last 4 digits, `XXXX XXXX 1234`), uploads it (`idType=AADHAAR_MASKED`) and types only those last 4 digits; the user also ticks a consent line ("I agree that Rentrix checks my masked Aadhaar to verify my identity"). The API accepts exactly 4 digits and refuses any 12-digit input with `422 aadhaar_full_number_refused`. An admin checks that the copy is masked and that the name and last 4 match, then marks it `VERIFIED` or `REJECTED`. An upload that shows the full number is rejected with `AADHAAR_NOT_MASKED`. Like every ID file, the masked copy is deleted as soon as the decision (approve or reject) is recorded (D-96). There is no `value_hmac` for Aadhaar, so Aadhaar cannot be used for duplicate detection; PAN and phone cover that.
- **D-104** **Provider later.** Checks sit behind `IdentityCheckProvider` (`start(method, input)`, `result(ref)`). v1 has only `AdminReviewProvider` (the admin queue) and the OTP flow. A later PR may add a licensed KYC provider (PAN verification against the Income Tax database, DigiLocker or Aadhaar offline e-KYC through a licensed agency) that returns only name match, last 4 and a reference id; it sets `checked_by=PROVIDER`, `provider`, `provider_ref`, and the stored data stays the same masked shape. No provider is called in tests.
- **D-105** **How identity feeds the approval flow (§7.3).** Identity checks are per user; approvals stay per role (D-89). (1) Submitting an application needs at least one ID method `VERIFIED` or `PENDING` (`422` with `missing: ["identity"]` otherwise). (2) An admin can approve only when at least one ID method is `VERIFIED`; deciding the application in the console also decides its `PENDING` PAN or Aadhaar check in the same transaction (both audited, D-87), and that check's ID file is deleted once the decision commits (D-96). An admin can also verify or reject a method on its own. (3) The gate D-91 requires the `APPROVED` application and a current `VERIFIED` ID method; if a method is revoked and none remains, gated writes return `403 id_verification_required` until the user verifies another method, without an admin re-approval. (4) Badges follow D-100 and do not wait for approval: a user with a verified phone shows "ID verified" while their application is still pending, but still cannot write until approved.
- **D-106** **Stored data, masked only.** Rentrix keeps per method: status, method, timestamps, who decided it and why, `last4` and (PAN and phone only) an HMAC for duplicate detection. The uploaded PAN card or masked Aadhaar file is held only until the admin decides, then deleted (D-96). Never stored, logged, emailed or returned: a full PAN, any Aadhaar number beyond its last 4 digits, an OTP code. The phone number itself is stored once, in `users.phone`, private (D-57). Masks in the UI: PAN `XXXXXX234F`, Aadhaar `XXXX XXXX 1234`, phone `+91 XXXXXX3210`. Masked values are visible only to the owner and admins; nobody else sees them, not even the other party of a tenancy. Request logs exclude `idNumber`, `panNumber`, `phone`, `code` and multipart bodies.

---

## 8. Abuse reporting and moderation

- **D-49** Any logged-in user can report a review, response, user, property or tenancy once per target. Parties of the tenancy behind a review may also report it.
- **D-50** Reports never auto-hide content, so a subject cannot hide a fair review by reporting it. Exception: a report with reason `PERSONAL_DATA` from a party of the tenancy auto-hides the **text** (rating still counts) until an admin decides, because exposure of personal data is the higher harm.
- **D-51** Admin outcomes: dismiss; hide review/response (removed from public and aggregates; author is notified with the reason); void tenancy (D-36); suspend user (their pending tenancies cancel, their authored published reviews are hidden pending review). Every action writes `moderation_actions`. Target: reports handled within 72 h.
- Hidden is reversible (restore). Admins never edit review text.

### 8.1 Admin console

- **D-86** The admin console (web `/admin`, API `/admin/**`, `ADMIN` only) gives read access to every record: landlords and tenants (users by role), properties in every status, tenancies, reviews (including `AWAITING_REVEAL` and `HIDDEN`), reports, verification applications, cities and the audit log. Each list has search (name, email, `public_id`, locality, building) and filters (role, city, status, verification status, demo data, created date range), with D-59 pagination. Screens: dashboard (queue counts, oldest waiting item, recent decisions), approvals queue (landlord, tenant, property listing, and from design v3 identity checks: PAN, masked Aadhaar, LinkedIn), areas and societies (D-109), records lists, record detail with its audit trail, reports queue (D-51) and cities (D-83). Admins see private fields that D-57 allows them (email, phone, `unit_label`), but ID numbers only masked, like everyone else; document files only through logged signed URLs (D-95). No bulk export in v1.
- **D-87** Every admin decision writes one append-only `moderation_actions` row: **who** (`admin_id`), **when** (`created_at`), **what** (`action`, `target_type`, `target_ref`) and **why** (`reason`). A reason is required for every reject, revoke, hide, void, suspend, city deactivation and admin revoke; approvals take an optional note. The user's own steps (submit, resubmit) are recorded in `verification_events` (§5.13), so `moderation_actions` stays an admin-only log. Rows of both are never updated or deleted; on account deletion (D-58) the rows stay and refer to the anonymised user. Each record detail page shows the trail for that record.
- **D-88** Conflict of interest: an admin cannot decide their own verification, approve their own property, or moderate a review, tenancy or report they are a party to (`403 admin_conflict`). Another admin must act. The dev seed has one admin and no admin-party records, so this never blocks the demo.

---

## 9. Rating math

For each subject (landlord user, tenant user, property), over reviews with `status = PUBLISHED` and `counts_in_aggregate = true`:

- `n` = review count, `average` = `rating_sum / n`, shown with one decimal.
- **D-52** Public display threshold: with `n < 3` the UI shows "New · n reviews" and the individual reviews, but no star average, so one review does not define a person.
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
- **D-95** Verification documents are private. ID proofs, onboarding rental agreements and property proofs are stored with `Visibility.PRIVATE` through the `ImageStorage` interface (§15) under random keys (`verification/{userPublicId}/{uuid}`), encrypted at rest; JPEG and PNG files are re-encoded to strip EXIF (resolution kept so they stay readable), PDFs are stored as uploaded; `sha256` is recorded. Only the owner and admins can open them, through short-lived signed URLs (5 min, D-30): `GET /me/documents/{id}/url` and `GET /admin/documents/{id}/url`; each admin URL writes a `VIEW_DOCUMENT` audit row (D-87). Public and party-facing APIs never return document data, ID numbers or their last 4, LinkedIn URLs or reject reasons. The only public signals are the **"ID verified"** and **"LinkedIn verified"** badges on profiles, landlord cards and review author lines (*changed in design v3*: per user, from D-99 and D-100, no longer per approved role). Request and error logs exclude multipart bodies and the `idNumber` field.
- **D-96** Retention (DPDP data minimisation): an **ID proof** file (PAN card, masked Aadhaar) is deleted as soon as the admin records the decision (approve or reject) on its identity check; a resubmission uploads a new file. Rental agreements and property proofs are deleted 90 days after their application or listing is decided (`VERIFICATION_DOC_RETENTION_DAYS`). Any document file is deleted at once when the user deletes the account (D-58) or replaces the file before a decision. The `identity_verifications` rows (`last4`, `value_hmac`, D-106), statuses and audit rows stay, so duplicates are still caught and decisions stay explained. ID files are deleted by the decision itself; a nightly job with the D-08 lock purges due rental agreements and property proofs. Both set `status = PURGED`.
- **D-58** Account deletion (DPDP Act 2023 aligned): personal data is erased (email, phone, `google_sub`, `google_name`, `avatar_url` cleared, display name replaced). Signing in again with the same Google account creates a new, empty user. Reviews the user **wrote** stay, attributed to "Former Rentrix user", and keep counting. Reviews **about** the user are hidden with their profile. Property reviews and confirmed rent records stay (they describe the property, not the person).

---

## 11. Permission matrix

Legend: ✅ allowed · 🔒 own only / condition noted · Ⓐ needs an `APPROVED` verification for that role (D-91) · — not allowed.
"Party" = landlord or tenant of the tenancy in question. "Logged-in" includes users whose verification is not yet approved.

| Action | Anonymous | Logged-in | Tenant party | Landlord party | Admin |
|---|---|---|---|---|---|
| List cities, search properties | ✅ | ✅ | ✅ | ✅ | ✅ |
| Browse areas and societies, view society and area pages + aggregates (D-112) | ✅ | ✅ | ✅ | ✅ | ✅ |
| View owner portfolio (all published properties + roll-ups, D-113) | aggregate only | ✅ | ✅ | ✅ | ✅ |
| View property card + aggregate | ✅ | ✅ | ✅ | ✅ | ✅ |
| View property reviews text + rent history (no approval needed) | — | ✅ | ✅ | ✅ | ✅ |
| View property `unit_label` | — | — | ✅ | ✅ | ✅ |
| Create / edit draft property | — | 🔒 `LANDLORD`, own | — | 🔒 own | hide only |
| Submit property for listing approval | — | 🔒 `LANDLORD`, own, Ⓐ | — | 🔒 own, Ⓐ | — |
| Archive own property | — | 🔒 `LANDLORD`, own | — | 🔒 own | hide only |
| View landlord profile + reviews (no approval needed) | aggregate only | ✅ | ✅ | ✅ | ✅ |
| View tenant profile + reviews | — | 🔒 self, enquiry grant, share link (D-54) | ✅ self | ✅ | ✅ |
| Send enquiry | — | 🔒 `TENANT`, Ⓐ | — | — | — |
| Create tenancy request | — | 🔒 `TENANT` on a published property, or `LANDLORD` on own property; Ⓐ | — | — | — |
| Confirm tenancy | — | — | 🔒 if counterparty, Ⓐ | 🔒 if counterparty, Ⓐ | — |
| Decline tenancy request (counterparty) / cancel own pending request (initiator), no approval needed | — | — | 🔒 if counterparty or initiator | 🔒 if counterparty or initiator | — |
| End tenancy | — | — | ✅ | ✅ | ✅ |
| Void tenancy | — | — | — | — | ✅ |
| Add rent record | — | — | ✅ Ⓐ | ✅ Ⓐ | — |
| Confirm / dispute rent record | — | — | 🔒 other party, Ⓐ | 🔒 other party, Ⓐ | ✅ |
| Upload / view tenancy document | — | — | ✅ | ✅ | ✅ |
| Write `TENANT_TO_LANDLORD`, `TENANT_TO_PROPERTY` | — | — | ✅ Ⓐ (window open) | — | — |
| Write `LANDLORD_TO_TENANT` | — | — | — | ✅ Ⓐ (window open) | — |
| Edit review | — | — | 🔒 author, D-41, Ⓐ | 🔒 author, D-41, Ⓐ | — |
| Withdraw review | — | — | 🔒 author | 🔒 author | — |
| Respond to review | — | — | 🔒 subject, Ⓐ | 🔒 subject / property landlord, Ⓐ | — |
| Report content | — | ✅ | ✅ | ✅ | — |
| Fill in / submit / resubmit own verification | — | 🔒 own, per held role | ✅ | ✅ | — |
| Verify own phone (OTP), submit PAN or masked Aadhaar, add LinkedIn (D-101–D-103) | — | 🔒 own | 🔒 own | 🔒 own | — |
| Propose a new area / society / tower / wing in a property draft (D-109) | — | 🔒 `LANDLORD`, own draft | — | 🔒 own draft | ✅ create directly |
| Verify / reject / revoke an identity check | — | — | — | — | ✅ not own (D-88) |
| Upload / view own verification documents | — | 🔒 owner (D-95) | 🔒 owner | 🔒 owner | ✅ logged |
| View all records, search and filter (admin console) | — | — | — | — | ✅ (D-86) |
| Approve / reject verification and property listings | — | — | — | — | ✅ not own (D-88) |
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
    "type": "https://rentrix.in/problems/review-window-closed",
    "title": "Review window closed",
    "status": 422,
    "code": "review_window_closed",
    "detail": "Reviews for this tenancy closed on 2026-08-31.",
    "instance": "/api/v1/tenancies/0193.../reviews",
    "errors": [ { "field": "body", "code": "too_short", "message": "At least 30 characters." } ],
    "traceId": "4f1c..."
  }
  ```
  Status use: `400` malformed, `401` no/invalid token, `403` authenticated but not allowed (including `approval_required` with `approval: {role, status}` in the body, D-91, and `admin_conflict`, D-88), `404` not found **or not visible to the caller** (no existence leak), `409` conflict (duplicate review, overlapping tenancy, state transition not allowed, `concurrent_update` when the database refused a lock to a simultaneous write; nothing was saved, so retry), `422` validation/business rule, `429` rate limited (with `Retry-After`).
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
| GET | `/auth/dev-users` | **`dev`/`test` profile only** (D-77): seed users for the login picker |
| POST | `/auth/dev-login` | **`dev`/`test` profile only** (D-77): `{userId}` of a seed user → same response as `/auth/google` |

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
| GET | `/me/verification` | one entry per held role: `{id?, role, status, revision, steps: {profile, identity, linkedin, rentalAgreement}, submittedAt?, decision: {code, note, decidedAt}?}`; `id` is the application's `public_id` (absent until the first submit); each step is `DONE`, `PENDING`, `MISSING`, `REJECTED`, `SKIPPED` or `NOT_APPLICABLE`; `decision` carries code and note for `REJECTED`, only `decidedAt` for `APPROVED` (the approve note is admin-only); `GET /me` carries a short `verification: {TENANT: status, LANDLORD: status}` (a held role without an application reports `DRAFT`) and `badges: {idVerified, linkedinVerified}` |
| POST | `/me/verification/{role}/submit` | `DRAFT` or `REJECTED` → `PENDING`; `422 verification_incomplete` with the missing steps in `missing` (`profile` without a display name and home city; `identity` when no ID method is `VERIFIED` or `PENDING`, D-105); `409 invalid_transition` when already `PENDING`/`APPROVED`; `429` with `Retry-After` after 3 submits in 24 h; a role the caller does not hold is `404`. *Design v3*: the design-v2 `PUT /me/verification/{role}` (LinkedIn fields) is replaced by `/me/identity/linkedin` |
| GET | `/me/identity` | `{idVerified, linkedinVerified, methods: [{method, status, masked, checkedBy, verifiedAt?, reject: {code, note}?, available}]}`; masked values only (D-106) |
| POST | `/me/identity/phone/start` | `{phone}` (Indian mobile; `+91`, `91` or bare 10 digits, spaces allowed) → `{challengeId, expiresAt}`; sends an SMS code; limits and `429` per D-101; `409 phone_already_verified` for the number already verified on this account; `503 phone_otp_unavailable` without a provider in prod; `503 sms_send_failed` when the provider refuses (that code is used up and still counts toward the limits); `429 rate_limited` with `Retry-After` also when parallel starts for the same number queue up for more than a few seconds |
| POST | `/me/identity/phone/confirm` | `{challengeId, code}` → `PHONE_OTP` `VERIFIED`, returns the `GET /me/identity` body; wrong code `422 otp_invalid` (attempts left), after 5 wrong codes `422 otp_attempts_exceeded`, expired or already used `422 otp_expired`; another user's challenge `404` |
| POST | `/me/identity/pan` | `{panNumber, documentId}` → `PAN` `PENDING`; format check, keeps last 4 + HMAC only (D-102) |
| POST | `/me/identity/aadhaar` | `{last4, documentId, consent: true}` → `AADHAAR` `PENDING`; anything but 4 digits `422`, 12 digits `422 aadhaar_full_number_refused` (D-103) |
| PUT / DELETE | `/me/identity/linkedin` | `{url}` → `LINKEDIN` `PENDING` (normalised, never fetched, D-93); delete removes it |
| POST | `/me/documents` | multipart `{kind, role?, propertyId?, idType?, file}` → `{id, kind, idType, status}`; `idType` ∈ `PAN`, `AADHAAR_MASKED` for `ID_PROOF`; ID numbers go through `/me/identity/*`, never this endpoint |
| GET | `/me/documents` | own documents, metadata only, masked numbers |
| GET | `/me/documents/{id}/url` | owner only: 5-min signed URL (D-95) |
| DELETE | `/me/documents/{id}` | owner, only while the application or listing is not `PENDING`/decided |

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
| POST | `/properties/{id}/submit` | owner, Ⓐ landlord, needs a `PROPERTY_PROOF` document: `DRAFT`/`REJECTED` → `PENDING_REVIEW` (D-92) |
| DELETE | `/properties/{id}` | owner; archives a `PUBLISHED` listing (D-21); one never published (`DRAFT`, `PENDING_REVIEW`, `REJECTED`) is soft-deleted, since it has no history to keep |
| GET / POST | `/properties/{id}/images` | upload ≤ 10 images |
| PATCH / DELETE | `/properties/{id}/images/{imageId}` | order, primary, delete |
| GET | `/properties/{id}/rent-history` | logged-in (D-56); D-26: `{propertyId, rows: [{fromMonth, toMonth?, monthlyRentInr, maintenanceInr?, share}]}`, months as `YYYY-MM` only (D-57), no tenant identity, confirmed records of `ACTIVE`/`ENDED` tenancies only, oldest first. A row runs to the month before the next confirmed record of its tenancy, or to the tenancy's end month; `toMonth` is absent while the rent is current. `share` is true on `ROOM`/`PG` properties |
| GET | `/properties/{id}/reviews` | logged-in for text; `TENANT_TO_PROPERTY` only |
| GET | `/properties/{id}/rating` | aggregate; public |
| POST | `/properties/{id}/enquiries` | `TENANT` |

**Users (public profiles)**
| GET | `/users/{id}` | shape depends on D-54/D-55; `404` when not visible; carries `badges: {idVerified, linkedinVerified}` (D-100) and, for landlords, `landlordRating` + `homesRating` (`OWNER_HOMES`) |
| GET | `/users/{id}/properties?city=&sort=` | owner portfolio grouped by city, area, society, with `PROPERTY` aggregates (D-113) |
| GET | `/users/{id}/reviews?as=LANDLORD|TENANT` | same visibility |
| GET | `/share/{token}` | tenant profile via share link |

**Tenancies**
| Method | Path | Notes |
|---|---|---|
| POST | `/tenancies` | `{propertyId, counterpartyEmail?, startDate, endDate?, initialRentInr, depositInr?}`. The caller's side follows from the property: its landlord must give `counterpartyEmail`. The answer is the same whether or not that email has an account (privacy: no account lookup by email): the landlord gets `tenant` = null and a masked `inviteEmail` (`p***@g***.com`). Internally, an existing email-verified account with the TENANT role is linked so it sees the request in `/me/tenancies` and gets a confirm-request email; otherwise a single-use invite is emailed. For such a request `409 tenancy_overlap` is checked when the invite is claimed and on confirm, against confirmed tenancies only (a sibling pending request never blocks), not at creation; anyone else is the tenant, on a `PUBLISHED` property, and sends no email. `startDate ≤ today`; `endDate` only for a tenancy that already ended (`≤ today`). The landlord's property must be `PUBLISHED` or `ARCHIVED` (`422 property_not_listed`). Guards D-35: `403 approval_required` (D-91), `403 role_required`, `422 email_not_verified`, `422 invite_self`, `409 tenancy_overlap` (tenant-started requests), `422 too_many_pending` (5) |
| GET | `/tenancies/{id}` | parties + admin, else `404`. `{id, property{id, buildingName, locality, city, unitLabel, propertyType}, myRole, landlord{id, shortName}, tenant? (hidden from the landlord until a landlord-started request is confirmed), inviteEmail? (masked for the landlord, full for admin), startDate, endDate?, initialRentInr, currentRentInr?, depositInr?, depositRefundedInr?, status, initiatedBy, declineReason?, evidenceLevel?, createdAt, expiresAt? (pending), confirmedAt?, endedAt?, actions{confirm, decline, cancel, end, addRentRecord}}` |
| POST | `/tenancies/{id}/confirm`, `/decline`, `/cancel`, `/end` | state transitions (§6); a transition the state does not allow is `409 invalid_transition`. `confirm` (counterparty, Ⓐ) → `ACTIVE`, or `ENDED` when the request has an end date, and writes the first `CONFIRMED` rent record; `decline` (counterparty, optional `{reason}`) and `cancel` (initiator) are not gated; `end` `{endDate, depositRefundedInr?}` (either party or admin, not gated) on `ACTIVE` only |
| POST | `/tenancy-invites/claim` | `{token}` from the invite link (`APP_BASE_URL/tenancy-invite?token=…`; in the body so it stays out of URLs and logs). Binds a landlord's invite to the caller when their verified email equals the invited one (`403 invite_email_mismatch` otherwise), the caller holds `TENANT` and no overlap exists; single use, expires with the request (14 d). The invitee then confirms or declines as the counterparty |
| GET / POST | `/tenancies/{id}/rent-records` | parties + admin; `POST {effectiveFrom, monthlyRentInr, maintenanceInr?}` by a party (Ⓐ) on an `ACTIVE`/`ENDED` tenancy (`409 tenancy_not_confirmed` otherwise) → `PROPOSED`. `effectiveFrom` is stored as the first of its month and must lie from the start month to the end date, or to next month while current (`422 outside_tenancy`); one live record per month (`409 rent_record_exists`) |
| POST | `/rent-records/{id}/confirm`, `/rent-records/{id}/dispute` | the party who did not add it (Ⓐ) or an admin; `PROPOSED` only (`409 invalid_transition`) |
| GET / POST | `/tenancies/{id}/documents` | private; GET returns signed URLs |
| POST | `/tenancies/{id}/reviews` | `{direction, ratingOverall, ratingA?, ratingB?, ratingC?, body}` |

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
| GET | `/admin/applications?role=&status=PENDING&flag=&city=&q=&sort=submittedAt,asc` | approvals queue |
| GET | `/admin/applications/{id}` | profile, identity methods (status, masked value, documents), LinkedIn URL + status, flags, previous revisions and decisions |
| POST | `/admin/applications/{id}/approve` | `{note?, identity?: [{id, result: VERIFIED|REJECTED, code?, note?}]}` → `APPROVED`; needs ≥1 ID method `VERIFIED` after the identity results apply (`422 no_verified_id` otherwise, and none of the call is kept); `identity` decides only the applicant's `PENDING` PAN or Aadhaar checks; own application `403 admin_conflict` (D-88), not `PENDING` `409 invalid_transition` (D-105) |
| POST | `/admin/applications/{id}/reject` | `{code, note}` → `REJECTED` |
| POST | `/admin/applications/{id}/revoke` | `{code, note}` → `REJECTED` from `APPROVED` |
| GET | `/admin/identity?method=&status=PENDING` | identity checks queue (PAN, masked Aadhaar, LinkedIn) |
| POST | `/admin/identity/{id}/verify`, `/reject`, `/revoke` | one method; reject and revoke `{code, note}`; audited `VERIFY_IDENTITY` / `REJECT_IDENTITY` / `REVOKE_IDENTITY`; verify and reject delete the method's ID file at once (D-96) |
| GET / POST / PATCH | `/admin/areas`, `/admin/areas/{id}` | areas with PIN codes; create, rename, aliases, PIN codes, hide (D-108, D-109) |
| GET / POST / PATCH | `/admin/societies`, `/admin/societies/{id}`, `/admin/societies/{id}/towers`, `/admin/towers/{id}/wings` | create, rename, hide; `PENDING` rows are listed with their listing |
| GET | `/admin/properties?status=PENDING_REVIEW&city=&q=` | all statuses available |
| POST | `/admin/properties/{id}/approve`, `/reject` | listing decision (D-92); reject `{code, note}`; approve also approves the listing's `PENDING` location rows, or takes `{useAreaId?, useSocietyId?, useTowerId?, useWingId?}` to point the listing at existing rows instead (D-109) |
| GET | `/admin/documents/{id}/url` | 5-min signed URL; writes `VIEW_DOCUMENT` (D-95) |
| GET | `/admin/users?role=LANDLORD|TENANT&verification=&status=&city=&demo=&q=` | landlord and tenant records |
| GET | `/admin/users/{id}` | record detail: profile, roles, verification per role, documents (masked), tenancies, reviews written/received, reports, audit trail |
| GET | `/admin/tenancies?status=&city=&q=`, `/admin/reviews?status=&direction=&city=&q=` | records lists |
| GET | `/admin/tenancies/{id}`, `/admin/reviews/{id}`, `/admin/properties/{id}` | record detail with audit trail |
| GET | `/admin/reports?status=` | reports queue |
| POST | `/admin/reports/{id}/resolve` | `{outcome: DISMISS|HIDE|VOID_TENANCY|SUSPEND_USER, note}` |
| POST | `/admin/reviews/{id}/hide`, `/restore` | |
| POST | `/admin/tenancies/{id}/void` | |
| POST | `/admin/tenancy-documents/{id}/accept`, `/reject` | |
| POST | `/admin/users/{id}/suspend`, `/reinstate` | |
| POST / DELETE | `/admin/users/{id}/admin-role` | grant / revoke `ADMIN` (D-19); not self |
| GET | `/admin/cities` | all cities, active and inactive |
| POST | `/admin/cities` | `{code, name, aliases[], state, pincodePrefixes[]}` → created inactive (D-83) |
| PATCH | `/admin/cities/{code}` | `{live?, name?, aliases?, state?, pincodePrefixes?}`; deactivation needs `reason` |
| GET | `/admin/audit?targetType=&targetRef=&adminId=&action=` | audit log |

**Ops**: `GET /actuator/health` (public, no details), probes `GET /actuator/health/liveness` and `GET /actuator/health/readiness` (readiness group includes the DB; the API container health check uses readiness), `GET /v3/api-docs` + Swagger UI only when the `dev` or `test` profile is explicitly active (D-64).

- **D-61** The OpenAPI document generated by springdoc is the contract the web builds against; types in `rentrix-web/src/types` are generated from it (`openapi-typescript`) instead of hand-written. The web commits a snapshot of the document (`rentrix-web/openapi/openapi.json`, refreshed with `npm run gen:api:fetch` against a dev API) and generates `src/types/api.gen.ts` from it, so web builds need no running API; O2's drift check compares the snapshot with the API's.

---

## 13. Authentication

- **D-62** Access token: JWT, HS256, 15 min, claims `sub` (user public_id), `roles`, `iat`, `exp`, `jti`. Sent as `Authorization: Bearer`. The web keeps it in memory only (never `localStorage`). Role and onboarding checks read `roles` from the JWT; every call that changes the caller's roles (`POST /me/onboarding`, role-adding `PATCH /me`) returns a fresh access token (D-73), so the claim is never stale for the caller. The refresh cookie is not rotated there: its `Path=/api/v1/auth` keeps the browser from sending it to `/me`, and `/auth/refresh` re-reads the roles from the database, so an old refresh token never brings back stale roles.
- **D-63** Refresh token: opaque random 256-bit value, 30-day lifetime, **rotated on every refresh**, stored only as a SHA-256 hash in `refresh_tokens(user_id, token_hash, family_id, expires_at, revoked_at)`. Rotation is a conditional update (`revoked_at IS NULL`), so two simultaneous rotations of one token cannot both succeed. **Reuse grace** (30 s, `app.auth.refresh-reuse-grace`): presenting the family's most recently rotated token again within 30 s of its rotation (two tabs, or a lost race, refreshing at once) returns a fresh access token for the user and **no** new cookie; the current successor stays valid and is not rotated again. Any other reuse of a rotated or revoked token (outside the window, or an older token in the chain) revokes the whole family (theft detection). The web also serialises refreshes across tabs (W3). Delivered as an `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` cookie. This keeps API instances stateless while allowing logout and revocation.
- **D-64** Fail closed: `SPRING_PROFILES_ACTIVE` has no default (the `${SPRING_PROFILES_ACTIVE:dev}` default in `application.yaml` is removed), and the API refuses to start unless it is set explicitly to `dev`, `test` or `prod`. The hard-coded dev `JWT_SECRET` exists only when `dev` or `test` is explicitly active; any other profile refuses to start without a real `JWT_SECRET` of at least 32 bytes. Secrets come from env only, never from tracked files. Local run: `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run`.
- Web and API must be same-site for the cookie (production: `doorwayliving.in` and `api.doorwayliving.in`); in dev the Vite dev server proxies `/api` to the API. CORS allow-list from `CORS_ALLOWED_ORIGINS`; when it is empty in `prod` the API starts with a warning and allows no cross-origin origin (an old Render deployment without the variable keeps starting).
- Rate limit on `/auth/google` and `/auth/dev-login`: 20 requests per 15 min per IP (`429`).
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
- **D-73** First sign-in creates the user with no roles and `onboarded_at = null`; the response has `needsOnboarding: true`. The web then shows a one-step role pick (followed by the verification steps of §7.3, D-89) (tenant, landlord, or both; optional home city; accept terms) that calls `POST /me/onboarding` once; the response carries a fresh access token with the new roles, and the web replaces its in-memory token. Until onboarding completes, only `GET /me`, `POST /me/onboarding`, `DELETE /me`, `/auth/*` and public reads are allowed (`403 onboarding_required` otherwise); an `ADMIN` keeps `/admin/**`. "Not onboarded" is read from the token: its `roles` hold neither `TENANT` nor `LANDLORD`. The other role can be added later via `PATCH /me` (D-16), which also returns a fresh access token (D-62).
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
- **D-77** Dev-only login: when the `dev` or `test` profile is explicitly active (D-64) and `DEV_LOGIN_ENABLED=true` (default `true` in `dev`), the API exposes `GET /auth/dev-users` and `POST /auth/dev-login`, and the login page shows a "Dev login" picker listing the seed users (D-68), so the 4 Bangalore + 4 Pune demo data can be clicked through and screenshotted with no Google account and no network. The beans (and Swagger UI) are not created in any other profile (the endpoints return `404`), and the app **refuses to start** if `DEV_LOGIN_ENABLED=true` with any profile other than `dev`/`test`. There is no default profile, so a deployment that forgets to set one does not start (D-64). Dev login works only for `is_demo` users. When `GOOGLE_CLIENT_ID` is unset in dev, the Google button is hidden and dev login is the only way in. In `prod` without `GOOGLE_CLIENT_ID` the API starts with one warning and `POST /auth/google` answers `503 google_sign_in_disabled` (same fail-closed-but-start rule as an empty CORS list, so the existing Render service and the compose smoke test keep starting); set it before opening sign-in to users.
- **D-78** Admins (D-19) are matched on the verified email in `ADMIN_EMAILS` each time a token is issued (sign-in and refresh). This env grant is added to the token's `roles` and to `GET /me`, but it is **not** written to `user_roles`, so removing the email from `ADMIN_EMAILS` removes that admin within one refresh, while `ADMIN` rows granted from the console (B13) stay.

### 13.2 Creating the Google OAuth client (setup steps)

- **D-79** These steps are in the root `README.md` (added with the backend auth PR, B3):
  1. Open Google Cloud Console → select or create a project (e.g. "rentrix-dev").
  2. APIs & Services → OAuth consent screen: user type **External**, app name "Rentrix (dev)", support email, scopes `openid`, `email`, `profile` only. While in "Testing", add your own Google account as a test user.
  3. APIs & Services → Credentials → **Create credentials → OAuth client ID** → application type **Web application**.
  4. Authorized JavaScript origins: `http://localhost:5173` and `http://localhost` (GIS needs both for local dev). Add the production web origins `https://doorwayliving.in` and `https://www.doorwayliving.in` for production. No redirect URIs are needed.
  5. Copy the client ID (`…apps.googleusercontent.com`). Do **not** download or commit the JSON.
  6. Export it for the API only: `export GOOGLE_CLIENT_ID=…apps.googleusercontent.com` (shell, or an untracked `.env` file; `.env` is git-ignored). The web picks it up from `/auth/config`.
  7. Start the API and web; "Sign in with Google" appears on the login page.

---

## 14. Notifications

- **D-65** An `EmailSender` interface with two implementations: `LogEmailSender` (dev/test: writes the email to the log) and `SmtpEmailSender` (prod: any SMTP provider via `MAIL_*` env). Emails: tenancy invite, tenancy confirm request, tenancy confirmed/declined/ended, review window open, reviews published, rent record to confirm, report outcome, verification approved / rejected (with the reason), listing approved / rejected (with the reason), identity check verified / rejected (with the reason, D-105). Emails never contain document data or ID numbers. No in-app notification centre in v1. *Build note (PLAN B5)*: only `LogEmailSender` exists so far, in every profile; it logs the masked recipient and subject at `INFO` and the body (which can hold an invite link) only at `DEBUG`, which `dev` turns on. `SmtpEmailSender` comes before real users are invited.
- SMS is used for one thing only: the phone OTP code (D-101), through an `SmsSender` interface with `LogSmsSender` (dev/test: writes the code to the log) and `HttpSmsSender` (prod: a provider configured by `SMS_*` env). The SMS text has the code and "Rentrix" only; the code is never logged in prod.

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
  - All 4 demo landlords and 12 demo tenants above are `APPROVED` (their seeded tenancies and reviews need it, D-91), with identity methods set by the seeder (*changed in design v3*: see D-115 for which method each user has).
  - Pending queue: **Demo Landlord BLR-3** (`LANDLORD`, `PENDING`, LinkedIn given, PAN uploaded); **Demo Tenant PNQ-7** (`TENANT`, `PENDING`, no LinkedIn, rental agreement; *design v3*: phone verified + masked Aadhaar pending instead of PAN, D-115); **Demo Tenant BLR-7** (`TENANT`, `PENDING`, LinkedIn given, PAN + rental agreement, flagged `DUPLICATE_ID` because its fake PAN matches BLR-8's, to show the flag).
  - Rejected: **Demo Tenant BLR-8** (`TENANT`, `REJECTED`, `ID_UNREADABLE`, note "The PAN photo is blurred. Upload a sharper photo.", can resubmit).
  - Pending listing: **Demo Annex I** (Jayanagar, Bangalore, 1BHK, landlord BLR-2, `PENDING_REVIEW`, one property proof). It is not published, so the 4 + 4 published demo properties and every per-city count stay as they are.
  - Seed documents are generated placeholder images that read "DEMO DOCUMENT · NOT REAL"; their numbers are fake (last 4 like `001D`) and their HMACs come from the dev `ID_HASH_KEY`. Under D-96 only `PENDING` ID checks have an ID file (BLR-3 and BLR-7 PAN, PNQ-7 masked Aadhaar); every decided ID check (the approved users, BLR-8's rejected PAN) has its `ID_PROOF` row `PURGED` with no file. Rental agreements and the property proof keep their placeholder files. These users have no tenancies and no reviews. Exact review texts live in the seeder source and stay generic (no names, phones or addresses, per D-43).

- **D-115** Design-v3 seed additions (the 4 + 4 published properties and every per-city count stay as they are):
  - **Areas** (real locality names and PIN codes, D-69): BLR Koramangala (560034, 560095), HSR Layout (560102), Whitefield (560066), Indiranagar (560038), Jayanagar (560011, 560041); PNQ Baner (411045), Hinjewadi (411057), Kothrud (411038), Viman Nagar (411014). Every seeded property points at its area.
  - **One gated society per city, each with 2 towers of 2 wings**: BLR **Demo Greens** (Koramangala, `GATED_COMMUNITY`, developer "Demo Builders"): Tower 1 (Wing A, Wing B), Tower 2 (Wing A, Wing B); Demo Residency A is in Tower 1, Wing A. PNQ **Demo Meadows** (Baner, `GATED_COMMUNITY`): Tower 1 (Wing A, Wing B), Tower 2 (Wing A, Wing B); Demo Towers E is in Tower 2, Wing B. All other seeded properties are standalone (no society).
  - **Proposed location**: the pending listing Demo Annex I proposes a new society **Demo Enclave** (Jayanagar) with Tower 1, both `PENDING`, so the listing queue shows a location proposal (D-109).
  - **Owners with several properties**: BLR-1 (Residency A, Heights B), BLR-2 (Villa C, Studio D, pending Annex I), PNQ-1 (Towers E, Park F), PNQ-2 (Court G, Nest H), so every owner profile shows a portfolio and an `OWNER_HOMES` roll-up.
  - **Identity methods** (D-99–D-103), replacing the design-v2 LinkedIn checks of D-97: every approved seeded user has at least one `VERIFIED` ID method, spread over the three (landlords BLR-1 PAN, BLR-2 masked Aadhaar, PNQ-1 PAN, PNQ-2 phone; tenants alternate PAN, phone and masked Aadhaar). `LINKEDIN` is `VERIFIED` for BLR-1, PNQ-2 and Demo Tenant PNQ-2 only, so both badge states appear. Pending queue: BLR-3 (PAN and LinkedIn `PENDING`), BLR-7 (PAN `PENDING`, `DUPLICATE_ID` with BLR-8), PNQ-7 (phone `VERIFIED`, masked Aadhaar `PENDING`, so "ID verified" shows while the application waits, D-105). Rejected: BLR-8 (PAN `REJECTED`, `ID_UNREADABLE`).
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
| `ADMIN_EMAILS` | empty | D-19, D-78 |
| `TRUSTED_PROXIES` (every profile) | loopback and private ranges (`10.x`, `172.16-31.x`, `192.168.x`, `127.x`, `::1`) | regex of reverse-proxy IPs whose `X-Forwarded-For` / `-Proto` / `-Host` / `-Port` are honoured (Tomcat `RemoteIpValve`, `forward-headers-strategy: native`); any other peer's forwarded headers are ignored, so the sign-in rate limit (§13) keys on the real client IP and a client cannot fake its origin. See **Behind a reverse proxy** below |
| `ID_HASH_KEY` | built-in default only in `dev`/`test` | HMAC key for PAN, phone and OTP codes (D-101, D-102, D-106); a set value must be ≥ 32 bytes. Required in any other profile, like `JWT_SECRET`, from PLAN B12 (PAN). Until then (B14) it is required outside `dev`/`test` only with `SMS_BACKEND=http`, so a deployment without an SMS provider keeps starting; without the key, phone OTP is unavailable (`503`) |
| `SMS_BACKEND` | `log` in `dev`/`test`; none in `prod` | `log` or `http` (D-101); without it in `prod` the phone method is unavailable (`503 phone_otp_unavailable`) and the API still starts; `log` in `prod` refuses to start |
| `SMS_HTTP_URL`, `SMS_API_KEY`, `SMS_SENDER_ID` | — | only with `SMS_BACKEND=http` (URL and key required, else the API refuses to start); `HttpSmsSender` posts JSON `{to, sender, message}` with `Authorization: Bearer SMS_API_KEY`; a provider with another request shape gets its own `SmsSender`. Secrets from env only |
| `OTP_TTL_MINUTES` | `10` | phone OTP lifetime (D-101) |
| `VERIFICATION_DOC_RETENTION_DAYS` | `90` | D-96 (rental agreements and property proofs; ID files go at decision) |
| `GOOGLE_CLIENT_ID` | none (never committed) | Google sign-in (D-70, D-74); empty in `prod` logs one warning and turns Google sign-in off (`503`, D-77) |
| `DEV_LOGIN_ENABLED` | `true` in `dev`, `false` in `test` | D-77; any profile other than `dev`/`test` refuses to start if `true` |
| `STORAGE_BACKEND` | `local` | `local` or `s3` (D-66) |
| `STORAGE_LOCAL_DIR` | `./data/media` | |
| `S3_*` | — | only when `s3` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | — | prod email |
| `SEED_DEMO` | `false` | D-68 |
| `SCHEDULING_ENABLED` | `true`; `false` in `test` | in-process scheduled jobs (D-08) |
| `VITE_API_BASE_URL` (web, build-time) | `/api/v1` | overridden at runtime by `config.js` (D-04) |
| `window.__RENTRIX_CONFIG__.apiBaseUrl` (web, runtime, `dist/config.js`) | empty (use build value) | D-04 |
| `API_PROXY_TARGET` (web dev server only) | `http://localhost:8080` | where `npm run dev` proxies `/api` |
| `API_BASE_URL` (web Docker image, run time) | empty | written into `/config.js` at container start; must be a URL the browser can reach (D-04) |

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
7. **New cities**: an admin adds the `cities` row and activates it (D-83); pickers, Home cards, search and copy follow from the data; no code change or release.

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
