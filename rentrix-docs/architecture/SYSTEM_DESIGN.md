# Rentrix — System Design (v1, two-sided trust platform)

Status: **current**. This document is the single source of truth for the v1 rebuild.
It supersedes every older architecture, API and database doc in `rentrix-docs/` (see §20).
Date: 2026-11-21.

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
- Live cities: **Bangalore (Bengaluru)** and **Pune** only.

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
| `HomePage.tsx` fake stats ("Now live in 18 cities", "Cities covered: 18", "2,400+ flats", "9,800+ reviews", "6,200+ tenants") and testimonials (incl. "Tenant · Mumbai") | Removed (D-03). Search hint names only Bangalore/Pune localities |
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
- **D-04** `rentrix-web` is a static SPA (React 19, Vite, TypeScript, TanStack Query, React Router, shadcn/ui). Build output is plain files; it can be served by any static host or CDN. The only runtime config is `VITE_API_BASE_URL`.
- **D-05** The web holds no business rules that the API does not also enforce. Permission checks in the UI are cosmetic; the API is the authority.

### 3.2 API tier
- **D-06** `rentrix-server` is a stateless Spring Boot 4 (Java 21) REST API under `/api/v1`. No HTTP session, no in-memory state that must survive between requests. Any instance can serve any request.
- **D-07** Layering: `controller` → `service` → `repository`, DTOs at the edge, entities never serialised directly. Package by feature (`user`, `city`, `property`, `tenancy`, `review`, `enquiry`, `report`, `admin`, `auth`, `media`, plus `security`, `config` and `common`) instead of by layer.
- **D-08** Scheduled work (review reveal, window close, aggregate repair) runs in-process with a DB row lock (`scheduled_job_lock` table, ShedLock-style), so it is safe with several instances and needs no extra service. Jobs call `common.scheduling.JobLock.runLocked(name, lockAtMostFor, lockAtLeastFor, job)`; `SCHEDULING_ENABLED=false` turns the scheduler off (the `test` profile does, and tests call jobs directly).

### 3.3 Data tier
- **D-09** SQLite is the default database: in the `dev` profile `DB_URL` defaults to `jdbc:sqlite:./data/rentrix.db` (the `test` profile uses a fresh file under `target/`). `prod` has no default and refuses to start without `DB_URL` (fail closed, like D-64); a `jdbc:sqlite:` URL is a valid production value for the single-box stage (§18). Setting `DB_URL` (+ `DB_USERNAME`, `DB_PASSWORD`) to a MySQL 8 or PostgreSQL 15+ JDBC URL switches the database with no code change.
- **D-10** One Flyway migration set, written in a portable SQL subset (no `AUTO_INCREMENT`/`ENGINE`, no vendor JSON functions, `CHECK` constraints only where all three support them). Where a statement must differ, it goes in `db/migration/vendor/{vendor}` using Flyway's `{vendor}` location placeholder (the folder is added with the first such statement). A column *type* that differs on every table is a Flyway placeholder instead (D-81). JPA runs with `ddl-auto=validate`. Decimal columns are declared `NUMERIC(p,s)` (not `DECIMAL`), because the SQLite driver reports `DECIMAL` as a float type and schema validation would fail.
- **D-11** SQLite runs with `journal_mode=WAL`, `foreign_keys=ON`, `busy_timeout=5000`. SQLite deployments run **one** API instance (single writer). Multiple instances require MySQL or Postgres.
- **D-12** IDs: internal `BIGINT` primary keys; every externally visible row also has a `public_id` (UUIDv7, stored as `CHAR(36)`). The API only ever exposes `public_id`, so IDs cannot be enumerated. Both are generated by the API, not the database (D-80).
- **D-13** Money is stored as whole rupees in `BIGINT` (`monthly_rent_inr`, `deposit_inr`). Currency is INR only in v1.
- **D-14** Timestamps are UTC (`TIMESTAMP`; `DATETIME(6)` on MySQL, D-81), kept at millisecond precision so they round-trip identically on all three databases; SQLite stores them as UTC text `yyyy-MM-dd HH:mm:ss.SSS`; tenancy and rent dates are calendar `DATE` (no time zone). Rent periods are month-granular in the UI.
- **D-15** Soft delete only where history matters (users, properties, reviews); a `deleted_at` column, not a boolean. Tenancies and rent records are never hard-deleted; they change status.
- **D-80** Internal ids are time-ordered 64-bit values generated in the API (`TimeOrderedIds`: 41 bits of milliseconds since 2026-01-01, 10 bits of node id, 12 bits of sequence starting at a random value each millisecond). This keeps the DDL identical on all three databases (no `AUTO_INCREMENT`, `IDENTITY` or sequences), needs no database round trip, and avoids the second write transaction a table-based id generator would need on SQLite's single writer. Several instances set `ID_NODE` (0–1023) each to rule out collisions; without it the node id is random per process. `public_id` is a UUIDv7 generated in the API.
- **D-81** The only vendor difference in the baseline is the timestamp column type: MySQL's `TIMESTAMP` ends in 2038 and is converted through the session time zone, so migrations write `${timestamp_type}`, which the API sets at startup from the connection's database product (`DATETIME(6)` on MySQL, `TIMESTAMP` elsewhere; `MigrationPlaceholders`). Running the migrations with the Flyway CLI needs `-placeholders.timestamp_type=…`.

---

## 4. Roles

- **D-16** A `User` has a role set: any of `TENANT`, `LANDLORD`, plus `ADMIN` for staff. One person can be both tenant and landlord with one account. A user picks at least one of `TENANT`/`LANDLORD` on first sign-in (onboarding, D-73) and can add the other later from settings. Roles are never removed by the user once they are attached to a tenancy.
- **D-17** The role a user plays **in a tenancy** comes from the tenancy (`landlord_id` / `tenant_id`), not from the account. The account role set only gates actions such as "create a property" (needs `LANDLORD`) or "send an enquiry" (needs `TENANT`).
- **D-18** A user has two separate reputations: **as landlord** and **as tenant**. They are aggregated and shown separately and never mixed.
- **D-19** `ADMIN` is never self-assigned. It is granted at startup to the emails in `ADMIN_EMAILS` (env) or by another admin. Admins cannot write reviews in their admin capacity; an admin who is also a party to a tenancy acts as a normal user there.

| Role | Can do |
|---|---|
| Anonymous | Browse cities and properties, see property cards with aggregate rating and count, see public landlord summary (name + aggregate as landlord). Sign up / log in. |
| `TENANT` | Everything a logged-in user can, plus: send enquiries, start a tenancy request on a property, review landlord and property of own confirmed tenancies. |
| `LANDLORD` | Everything a logged-in user can, plus: create and manage own properties, invite tenants to tenancies, confirm tenancy requests, review tenants of own confirmed tenancies, view profiles of tenants who enquired. |
| `ADMIN` | Moderation: reports queue, hide/restore content, void tenancies, suspend users, view audit log. |

---

## 5. Entities

```
User 1───N Property (landlord_id)
User 1───N Tenancy (as landlord)      Property 1───N Tenancy
User 1───N Tenancy (as tenant)        Tenancy  1───N RentRecord
Tenancy 1───0..3 Review (one per direction)
Review 1───0..1 ReviewResponse        Review 1───N ReviewRevision
Property 1───N PropertyImage          Tenancy 1───N TenancyDocument
Tenant  1───N Enquiry N───1 Property
Report N───1 (Review | ReviewResponse | User | Property | Tenancy)
RatingAggregate: one row per (subject_type, subject_id)
City: reference table (2 live rows)
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
| `phone` | optional, E.164, private; never shown publicly |
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
| `code` | PK: `BLR`, `PNQ` |
| `name` | "Bangalore", "Pune" |
| `aliases` | "Bengaluru" for `BLR` (search matches aliases) |
| `state` | "Karnataka", "Maharashtra" |
| `live` | boolean |

- **D-20** Cities are data, not an enum. v1 ships exactly two rows, both `live=true`: `BLR` Bangalore (alias Bengaluru, Karnataka) and `PNQ` Pune (Maharashtra). The API rejects any `city` that is not a live row (`422 city_not_live`). City pickers and filters list only `GET /cities` results. Adding a city later is one migration row, no code.

### 5.3 Property (`properties`, has `public_id`)
| Column | Notes |
|---|---|
| `landlord_id` | FK users; current owner |
| `city_code` | FK cities, must be live |
| `locality` | e.g. "Koramangala"; free text, trimmed, normalised for search into `locality_key` (lower-case, single spaces; indexed with `city_code`, `status`) |
| `building_name`, `street` | public |
| `unit_label` | flat/house number; **private** (parties + admin only) |
| `pincode` | 6 digits, validated against city prefix (`56xxxx` BLR, `41xxxx` PNQ) |
| `property_type` | `APARTMENT`, `INDEPENDENT_HOUSE`, `VILLA`, `STUDIO`, `ROOM`, `PG` |
| `bedrooms`, `bathrooms`, `area_sqft`, `furnishing` (`UNFURNISHED`, `SEMI`, `FULL`), `parking` | optional facts |
| `description` | ≤ 2000 chars |
| `status` | `DRAFT`, `PUBLISHED`, `ARCHIVED` |
| `is_demo`, `deleted_at` | |

- **D-21** Only users with `LANDLORD` create properties. A property is visible to others only when `PUBLISHED`. Archiving hides it from search but keeps its tenancies, rent history and reviews readable via direct link.
- **D-22** Ownership transfer between landlords is not in v1. The tenancy stores its own `landlord_id`, so history stays correct if transfer is added later.

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
Primary key `(subject_type, subject_id)`, where `subject_type` ∈ `LANDLORD` (user), `TENANT` (user), `PROPERTY`.
Columns: `review_count`, `rating_sum`, `average` (decimal 3,2), `bayes_score` (decimal 4,3), `count_1` … `count_5`, `sub_a_avg`, `sub_b_avg`, `sub_c_avg`, `last_review_at`, `updated_at`.

- **D-33** Aggregates are stored rows, updated in the same DB transaction as any change to a review that counts (publish, edit, hide, restore, supersede). The update recomputes the subject's row from source reviews (not an increment), so it is always self-correcting. A nightly job recomputes all rows as a repair pass.

### 5.11 Report and ModerationAction
- `reports(public_id, reporter_id, target_type, target_id, reason, details ≤ 1000, status, handled_by, resolution_note, created_at, handled_at)`.
  `target_type` ∈ `REVIEW`, `REVIEW_RESPONSE`, `USER`, `PROPERTY`, `TENANCY`.
  `reason` ∈ `FAKE_TENANCY`, `PERSONAL_DATA`, `HARASSMENT_OR_HATE`, `OFF_TOPIC`, `CONFLICT_OF_INTEREST`, `SPAM`, `OTHER`.
  `status` ∈ `OPEN`, `ACTIONED`, `DISMISSED`.
- `moderation_actions(admin_id, action, target_type, target_id, report_id?, reason, created_at)`: append-only audit log of every admin action.

### 5.12 Supporting tables
`user_roles`, `refresh_tokens` (D-63), `share_links` (tenant reputation links, D-54; hashed token, 30-day expiry, revocable), `invite_tokens` (tenancy invites to people without an account; hashed, single use, 14-day expiry), `scheduled_job_lock` (D-08), `property_images` (§15), `idempotency_keys` (§12.1; added in migration `V2`: `scope` = caller's user `public_id`, unique `(scope, idempotency_key)`, `request_hash`, `state` IN_PROGRESS/COMPLETED, stored response, `expires_at`).

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
- **D-35** Guards at creation: both accounts email-verified; `landlord_id ≠ tenant_id`; dates valid; no overlapping tenancy for the same tenant + property; a user may create at most 5 pending tenancies at once; an invite email cannot be the initiator's own email or an existing account with a different email in the payload.
- **D-36** Ending: either party sets `end_date` (≤ today). The other party is notified and can dispute within 14 days (→ report `FAKE_TENANCY` / admin review). An admin `VOID` (fake or mistaken tenancy) hides all its reviews, removes them from aggregates and removes its rent records from rent history; it is logged in `moderation_actions`.
- `evidence_level` is `MUTUAL` after confirmation. If a party uploads an agreement and an admin accepts it, it becomes `DOCUMENT`, shown as a stronger badge ("Agreement checked"). Document upload is optional in v1.

---

## 7. Review lifecycle and trust rules

### 7.1 Trust rules
- **D-37** Only a party of a tenancy in status `ACTIVE` or `ENDED` can review, and only in the directions their side allows: the tenant writes `TENANT_TO_LANDLORD` and `TENANT_TO_PROPERTY`; the landlord writes `LANDLORD_TO_TENANT`.
- **D-38** One review per direction per tenancy (`UNIQUE(tenancy_id, direction)`). So each tenancy yields at most three reviews.
- **D-39** Review window: opens when the tenancy has been `ACTIVE` for 60 days, or when it `ENDED` (whichever is first); closes 180 days after `end_date`. While the tenancy is current the window stays open.
- **D-40** A review written during an `ACTIVE` tenancy may be **updated once** after the tenancy ends (within the closing window). The update creates a revision and resets `edit_until`.
- **D-41** Edit window: the author may edit text and ratings for **7 days** after `published_at` (or after submission while hidden for reveal). After that the review is frozen; only admins can hide it. Authors may withdraw (delete) their own review at any time; withdrawal removes it from aggregates and cannot be undone for that tenancy+direction.
- **D-42** The subject can never edit, delete or hide a review about them. Their tools are one public response (§5.7) and reporting (§8).
- **D-43** Content rules checked at submit (rejects with `422`): length 30–2000; no phone numbers, emails, URLs or unit numbers in the body (regex detection); basic profanity list. These keep personal data out of public text.
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
- **D-48** Post-moderation: published reviews are live without admin approval (the tenancy gate replaces the old `PENDING` admin approval). Admin action happens on reports and automated flags.

---

## 8. Abuse reporting and moderation

- **D-49** Any logged-in user can report a review, response, user, property or tenancy once per target. Parties of the tenancy behind a review may also report it.
- **D-50** Reports never auto-hide content, so a subject cannot hide a fair review by reporting it. Exception: a report with reason `PERSONAL_DATA` from a party of the tenancy auto-hides the **text** (rating still counts) until an admin decides, because exposure of personal data is the higher harm.
- **D-51** Admin outcomes: dismiss; hide review/response (removed from public and aggregates; author is notified with the reason); void tenancy (D-36); suspend user (their pending tenancies cancel, their authored published reviews are hidden pending review). Every action writes `moderation_actions`. Target: reports handled within 72 h.
- Hidden is reversible (restore). Admins never edit review text.

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

---

## 10. Privacy: public vs logged-in

- **D-54** Tenants are not searchable. There is no tenant directory. A tenant's profile and tenant reputation are visible only to: the tenant; landlords of their tenancies; a landlord who received an open enquiry from them (until `expires_at`); anyone holding a share link the tenant created (`/me/share-links`, revocable, 30-day expiry); admins.
- **D-55** Landlord profiles (short name, landlord aggregate, published properties, reviews received as landlord) are visible to any **logged-in** user. Anonymous visitors see the landlord's short name and aggregate only on property pages.
- **D-56** Property pages: anonymous visitors see city, locality, building/street, facts, images, aggregate rating and count. Logged-in users also see review texts, responses and the full rent history.
- **D-57** Never shown to anyone except the user, the other party of a shared tenancy (where relevant) and admins: email, phone (unless shared via enquiry), `unit_label`, tenancy documents, exact tenancy dates of others (public views show month/year only), deposit amounts.
- **D-58** Account deletion (DPDP Act 2023 aligned): personal data is erased (email, phone, `google_sub`, `google_name`, `avatar_url` cleared, display name replaced). Signing in again with the same Google account creates a new, empty user. Reviews the user **wrote** stay, attributed to "Former Rentrix user", and keep counting. Reviews **about** the user are hidden with their profile. Property reviews and confirmed rent records stay (they describe the property, not the person).

---

## 11. Permission matrix

Legend: ✅ allowed · 🔒 own only / condition noted · — not allowed.
"Party" = landlord or tenant of the tenancy in question.

| Action | Anonymous | Logged-in | Tenant party | Landlord party | Admin |
|---|---|---|---|---|---|
| List cities, search properties | ✅ | ✅ | ✅ | ✅ | ✅ |
| View property card + aggregate | ✅ | ✅ | ✅ | ✅ | ✅ |
| View property reviews text + rent history | — | ✅ | ✅ | ✅ | ✅ |
| View property `unit_label` | — | — | ✅ | ✅ | ✅ |
| Create / edit / archive property | — | 🔒 `LANDLORD`, own | — | 🔒 own | hide only |
| View landlord profile + reviews | aggregate only | ✅ | ✅ | ✅ | ✅ |
| View tenant profile + reviews | — | 🔒 self, enquiry grant, share link (D-54) | ✅ self | ✅ | ✅ |
| Send enquiry | — | 🔒 `TENANT` | — | — | — |
| Create tenancy request | — | 🔒 `TENANT` on a published property, or `LANDLORD` on own property | — | — | — |
| Confirm / decline tenancy | — | — | 🔒 if counterparty | 🔒 if counterparty | — |
| End tenancy | — | — | ✅ | ✅ | ✅ |
| Void tenancy | — | — | — | — | ✅ |
| Add rent record | — | — | ✅ | ✅ | — |
| Confirm / dispute rent record | — | — | 🔒 other party | 🔒 other party | ✅ |
| Upload / view tenancy document | — | — | ✅ | ✅ | ✅ |
| Write `TENANT_TO_LANDLORD`, `TENANT_TO_PROPERTY` | — | — | ✅ (window open) | — | — |
| Write `LANDLORD_TO_TENANT` | — | — | — | ✅ (window open) | — |
| Edit / withdraw review | — | — | 🔒 author, D-41 | 🔒 author, D-41 | — |
| Respond to review | — | — | 🔒 subject | 🔒 subject / property landlord | — |
| Report content | — | ✅ | ✅ | ✅ | — |
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
  Status use: `400` malformed, `401` no/invalid token, `403` authenticated but not allowed, `404` not found **or not visible to the caller** (no existence leak), `409` conflict (duplicate review, overlapping tenancy, state transition not allowed), `422` validation/business rule, `429` rate limited (with `Retry-After`).
- Dates `YYYY-MM-DD`, timestamps ISO-8601 UTC, money as integer rupees with field suffix `Inr`.
- Writes that may be retried by clients accept an optional `Idempotency-Key` header (stored 24 h) on `POST /tenancies`, `POST .../reviews`, `POST /reports`.
  Endpoints opt in with `@Idempotent`. Keys are 1–64 chars from `A-Z a-z 0-9 . _ : -` (else `400 idempotency_key_invalid`) and are scoped to the signed-in caller. The first request reserves the key; a `2xx` response is stored and replayed for the same key and same request (method, path, query, body) with `Idempotent-Replayed: true`. A non-`2xx` response releases the key so the client can fix and retry with it. Same key with a different request: `422 idempotency_key_reused`; same key while the first request still runs: `409 idempotency_request_in_progress`. Expired keys are deleted hourly by a locked job (D-08).
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
| GET / PATCH / DELETE | `/me` | profile (display name, phone, home city), add role, account deletion (D-58). A `PATCH` that adds a role returns `{user, accessToken}` with the new roles and rotates the refresh cookie (D-73) |
| POST | `/me/onboarding` | once, first sign-in: `{roles:[TENANT|LANDLORD], homeCity?, acceptTerms:true}` → `{user, accessToken}` with the new roles + rotated refresh cookie (D-73) |
| GET | `/me/tenancies?role=&status=` | |
| GET | `/me/reviews/written`, `/me/reviews/received?as=LANDLORD|TENANT` | received includes own `AWAITING_REVEAL` state only as "pending" |
| GET | `/me/review-tasks` | tenancies with an open review window and missing reviews |
| GET | `/me/enquiries?as=TENANT|LANDLORD` | |
| GET / POST / DELETE | `/me/share-links` | tenant reputation share links |
| GET | `/me/properties` | landlord's own, all statuses |

**Reference & stats**
| GET | `/cities` | live cities only |
| GET | `/stats` | real counts: published properties, confirmed tenancies, published reviews, per live city |

**Properties**
| Method | Path | Notes |
|---|---|---|
| GET | `/properties?city=BLR&locality=&type=&bedrooms=&minRating=&q=&sort=` | published only; `city` must be live |
| GET | `/properties/{id}` | fields per D-56 |
| POST | `/properties` | `LANDLORD`; creates `DRAFT` |
| PATCH | `/properties/{id}` | owner; includes `status` transitions |
| DELETE | `/properties/{id}` | owner; archives (D-21) |
| GET / POST | `/properties/{id}/images` | upload ≤ 10 images |
| PATCH / DELETE | `/properties/{id}/images/{imageId}` | order, primary, delete |
| GET | `/properties/{id}/rent-history` | logged-in; D-26 |
| GET | `/properties/{id}/reviews` | logged-in for text; `TENANT_TO_PROPERTY` only |
| GET | `/properties/{id}/rating` | aggregate; public |
| POST | `/properties/{id}/enquiries` | `TENANT` |

**Users (public profiles)**
| GET | `/users/{id}` | shape depends on D-54/D-55; `404` when not visible |
| GET | `/users/{id}/reviews?as=LANDLORD|TENANT` | same visibility |
| GET | `/share/{token}` | tenant profile via share link |

**Tenancies**
| Method | Path | Notes |
|---|---|---|
| POST | `/tenancies` | `{propertyId, counterpartyEmail?, startDate, endDate?, initialRentInr, depositInr?}` |
| GET | `/tenancies/{id}` | parties + admin |
| POST | `/tenancies/{id}/confirm`, `/decline`, `/cancel`, `/end` | state transitions (§6) |
| GET / POST | `/tenancies/{id}/rent-records` | |
| POST | `/rent-records/{id}/confirm`, `/rent-records/{id}/dispute` | other party |
| GET / POST | `/tenancies/{id}/documents` | private; GET returns signed URLs |
| POST | `/tenancies/{id}/reviews` | `{direction, ratingOverall, ratingA?, ratingB?, ratingC?, body}` |

**Reviews**
| GET | `/reviews/{id}` | visibility follows its subject |
| PATCH | `/reviews/{id}` | author, within edit window |
| DELETE | `/reviews/{id}` | author withdraws |
| POST / PATCH | `/reviews/{id}/response` | subject, one per review |

**Reports**
| POST | `/reports` | `{targetType, targetId, reason, details?}` |

**Admin** (`ADMIN` only)
| GET | `/admin/reports?status=` |
| POST | `/admin/reports/{id}/resolve` | `{outcome: DISMISS|HIDE|VOID_TENANCY|SUSPEND_USER, note}` |
| POST | `/admin/reviews/{id}/hide`, `/restore` |
| POST | `/admin/tenancies/{id}/void` |
| POST | `/admin/tenancy-documents/{id}/accept`, `/reject` |
| POST | `/admin/users/{id}/suspend`, `/reinstate` |
| GET | `/admin/audit?targetType=&targetId=` |

**Ops**: `GET /actuator/health` (public, no details), probes `GET /actuator/health/liveness` and `GET /actuator/health/readiness` (readiness group includes the DB; the API container health check uses readiness), `GET /v3/api-docs` + Swagger UI only when the `dev` or `test` profile is explicitly active (D-64).

- **D-61** The OpenAPI document generated by springdoc is the contract the web builds against; types in `rentrix-web/src/types` are generated from it (`openapi-typescript`) instead of hand-written.

---

## 13. Authentication

- **D-62** Access token: JWT, HS256, 15 min, claims `sub` (user public_id), `roles`, `iat`, `exp`, `jti`. Sent as `Authorization: Bearer`. The web keeps it in memory only (never `localStorage`). Role and onboarding checks read `roles` from the JWT; every call that changes the caller's roles (`POST /me/onboarding`, role-adding `PATCH /me`) returns a fresh access token and rotates the refresh cookie (D-73), so the claim is never stale for the caller.
- **D-63** Refresh token: opaque random 256-bit value, 30-day lifetime, **rotated on every refresh**, stored only as a SHA-256 hash in `refresh_tokens(user_id, token_hash, family_id, expires_at, revoked_at)`. Reuse of a rotated token revokes the whole family (theft detection). Delivered as an `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` cookie. This keeps API instances stateless while allowing logout and revocation.
- **D-64** Fail closed: `SPRING_PROFILES_ACTIVE` has no default (the `${SPRING_PROFILES_ACTIVE:dev}` default in `application.yaml` is removed), and the API refuses to start unless it is set explicitly to `dev`, `test` or `prod`. The hard-coded dev `JWT_SECRET` exists only when `dev` or `test` is explicitly active; any other profile refuses to start without a real `JWT_SECRET` of at least 32 bytes. Secrets come from env only, never from tracked files. Local run: `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run`.
- Web and API must be same-site for the cookie (e.g. `rentrix.in` and `api.rentrix.in`); in dev the Vite dev server proxies `/api` to the API. CORS allow-list from `CORS_ALLOWED_ORIGINS`.
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

- **D-70** "Sign in with Google" is the only production login, for tenants and landlords alike. The web uses Google Identity Services (GIS) to get an **ID token** in the browser and posts it to `POST /auth/google`. The API verifies it server-side: RS256 signature against Google's JWKS (`https://www.googleapis.com/oauth2/v3/certs`, cached per its `Cache-Control`), `iss` ∈ {`accounts.google.com`, `https://accounts.google.com`}, `aud` = `GOOGLE_CLIENT_ID`, `exp` not passed (60 s clock skew), `email_verified` = true. Any failure → `401 google_token_invalid`. The API then issues its **own** access + refresh tokens (D-62, D-63); the Google token is never stored or forwarded, so the API stays stateless.
- **D-71** No passwords in v1: no password column, no signup form, no password reset, no email-verification flow (Google already verified the email). This removes a whole class of credential storage risk.
- **D-72** One Google account = one Rentrix user, keyed by `google_sub`, never by email (Google emails can change; the `sub` does not). On every sign-in the API refreshes `email`, `email_verified_at`, `google_name` and `avatar_url` from the token; `display_name` is changed only by the user. If the token's email already belongs to a **different** user (rare: account recreated at Google, or a seed user email), sign-in fails with `409 account_email_conflict` and an admin resolves it; accounts are never merged automatically.
- **D-73** First sign-in creates the user with no roles and `onboarded_at = null`; the response has `needsOnboarding: true`. The web then shows a one-step role pick (tenant, landlord, or both; optional home city; accept terms) that calls `POST /me/onboarding` once; the response carries a fresh access token with the new roles (and rotates the refresh cookie), and the web replaces its in-memory token. Until onboarding completes, only `GET /me`, `POST /me/onboarding`, `DELETE /me`, `/auth/*` and public reads are allowed (`403 onboarding_required` otherwise). The other role can be added later via `PATCH /me` (D-16), which also returns a fresh access token and rotates the refresh cookie (D-62).
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
- **D-77** Dev-only login: when the `dev` or `test` profile is explicitly active (D-64) and `DEV_LOGIN_ENABLED=true` (default `true` in `dev`), the API exposes `GET /auth/dev-users` and `POST /auth/dev-login`, and the login page shows a "Dev login" picker listing the seed users (D-68), so the 4 Bangalore + 4 Pune demo data can be clicked through and screenshotted with no Google account and no network. The beans (and Swagger UI) are not created in any other profile (the endpoints return `404`), and the app **refuses to start** if `DEV_LOGIN_ENABLED=true` with any profile other than `dev`/`test`. There is no default profile, so a deployment that forgets to set one does not start (D-64). Dev login works only for `is_demo` users. When `GOOGLE_CLIENT_ID` is unset in dev, the Google button is hidden and dev login is the only way in; in `prod` the app refuses to start without `GOOGLE_CLIENT_ID`.
- **D-78** Admins (D-19) are matched on the verified Google email in `ADMIN_EMAILS` at sign-in.

### 13.2 Creating the Google OAuth client (setup steps)

- **D-79** This PR is design only, so the README is not changed here. These steps go into the root `README.md` in the backend auth implementation PR (the PR that adds `POST /auth/google`):
  1. Open Google Cloud Console → select or create a project (e.g. "rentrix-dev").
  2. APIs & Services → OAuth consent screen: user type **External**, app name "Rentrix (dev)", support email, scopes `openid`, `email`, `profile` only. While in "Testing", add your own Google account as a test user.
  3. APIs & Services → Credentials → **Create credentials → OAuth client ID** → application type **Web application**.
  4. Authorized JavaScript origins: `http://localhost:5173` and `http://localhost` (GIS needs both for local dev). Add the production web origin later (e.g. `https://rentrix.in`). No redirect URIs are needed.
  5. Copy the client ID (`…apps.googleusercontent.com`). Do **not** download or commit the JSON.
  6. Export it for the API only: `export GOOGLE_CLIENT_ID=…apps.googleusercontent.com` (shell, or an untracked `.env` file; `.env` is git-ignored). The web picks it up from `/auth/config`.
  7. Start the API and web; "Sign in with Google" appears on the login page.

---

## 14. Notifications

- **D-65** An `EmailSender` interface with two implementations: `LogEmailSender` (dev/test: writes the email to the log) and `SmtpEmailSender` (prod: any SMTP provider via `MAIL_*` env). Emails: tenancy invite, tenancy confirm request, tenancy confirmed/declined/ended, review window open, reviews published, rent record to confirm, report outcome. No in-app notification centre in v1.

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
- **D-67** Uploads: `image/jpeg`, `image/png`, `image/webp`, ≤ 15 MB each, ≤ 10 per property; content type sniffed from bytes (not trusted from the client); images re-encoded server-side to WebP (max 2048 px long side) plus a 480 px thumbnail; **EXIF stripped** (removes GPS location). Keys are random (`properties/{publicId}/{uuid}.webp`), never the original filename. Tenancy documents: PDF/JPEG/PNG ≤ 10 MB, stored PRIVATE, `sha256` recorded.
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

Every property has at least one tenancy with reviews in all three directions published (`TENANT_TO_LANDLORD`, `LANDLORD_TO_TENANT`, `TENANT_TO_PROPERTY`); these are ended tenancies where the table shows them. Demo Studio D's only tenancy is current and has been `ACTIVE` for more than 60 days, so its window is open under D-39. The seed also has a spread of ratings (1–5, not all 5s), at least one review response, one `AWAITING_REVEAL` review (to show double-blind), and one open report for the admin queue. One demo admin (`demo-admin@example.test`). Exact review texts live in the seeder source and stay generic (no names, phones or addresses, per D-43).

---

## 17. Configuration (env)

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | none, required | `dev`, `test` or `prod`; the API refuses to start if unset (D-64) |
| `PORT` | `8080` | |
| `DB_URL` | `jdbc:sqlite:./data/rentrix.db` in `dev`; none (required) in `prod` | MySQL/Postgres URL switches DB (D-09) |
| `DB_POOL_MAX` | `10` | connection pool size |
| `ID_NODE` | random per process | 0–1023, set per instance when running several (D-80) |
| `LOG_FORMAT` | `ecs` | structured JSON console logs (`ecs`, `logstash` or `gelf`); every line carries the request id as `traceId` |
| `DB_USERNAME`, `DB_PASSWORD` | empty | |
| `JWT_SECRET` | built-in default only in `dev`/`test` | required (≥ 32 bytes) in any other profile (D-64) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` only in `dev`/`test`; none (required) in `prod` | comma list; the API refuses to start in `prod` if empty |
| `APP_BASE_URL` | `http://localhost:5173` | links in emails |
| `ADMIN_EMAILS` | empty | D-19, D-78 |
| `GOOGLE_CLIENT_ID` | none (never committed) | Google sign-in (D-70, D-74); required in `prod` |
| `DEV_LOGIN_ENABLED` | `true` in `dev`, `false` in `test` | D-77; any profile other than `dev`/`test` refuses to start if `true` |
| `STORAGE_BACKEND` | `local` | `local` or `s3` (D-66) |
| `STORAGE_LOCAL_DIR` | `./data/media` | |
| `S3_*` | — | only when `s3` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | — | prod email |
| `SEED_DEMO` | `false` | D-68 |
| `SCHEDULING_ENABLED` | `true`; `false` in `test` | in-process scheduled jobs (D-08) |
| `VITE_API_BASE_URL` (web, build-time) | `/api/v1` | |

**Upgrading from the old app**: v1 starts from a fresh, empty database (new Flyway V1 baseline); point `DB_URL` at a new database, because the old schema and data are not migrated and are left as they are. See `rentrix-server/README.md` for the env changes.

A local developer needs nothing but Java 21 and Node: SQLite file + local media folder + dev login, no MySQL, no S3, no Docker, no Google account. Google sign-in is optional locally (set `GOOGLE_CLIENT_ID`).

---

## 18. Path to scale

The v1 shape grows without a rewrite:

1. **Single box** (launch): one API instance + SQLite + local media + static web on a CDN/static host.
2. **Managed DB**: set `DB_URL` to Postgres (recommended) or MySQL; run 2+ API instances behind a load balancer (stateless, D-06). Switch `STORAGE_BACKEND=s3` with a CDN in front of the public bucket.
3. **Read load**: property search and aggregates are already precomputed (D-33); add HTTP caching (`Cache-Control`, `ETag`) on public property and rating endpoints, then a DB read replica.
4. **Search**: Postgres full-text on locality/building; move to OpenSearch only if needed.
5. **Background work**: the in-process scheduler (D-08) moves to a queue (e.g. SQS) only when email volume or image processing needs it.
6. **Rate limits**: in-memory per instance in v1 (Bucket4j); move to a shared Redis store when running many instances.
7. **New cities**: insert a `cities` row (D-20) plus locality hints; no code change.

---

## 19. Not in v1

- Payments, rent collection, deposit escrow.
- In-app chat or messaging (enquiries only, D-31).
- Listing marketplace features: availability calendars, visit booking, brokerage.
- Tenancy verification by document alone when the other party is not on Rentrix ("unclaimed landlord"); v1 needs both parties (D-34).
- Property ownership transfer, co-owners, agencies/property managers.
- Email/password login, phone OTP login, and social logins other than Google (D-70, D-71).
- KYC (Aadhaar/PAN).
- Native mobile apps (the web is responsive).
- Push / in-app notifications (email only, D-65).
- ML fraud detection (rule-based flags only, D-45).
- Review translation, photos in reviews.
- Cities other than Bangalore and Pune.
- Migration of data from the v0.1.x schema (treated as test data, D-01).

---

## 20. Superseded documents

These are marked obsolete with a pointer to this file:
`architecture/ARCHITECTURE.md`, `architecture/EntityRelationships.txt`, `architecture/ROADMAP.md`,
`architecture/UI folder structure.txt`, `architecture/backend.md`, `architecture/frontend.md`, `architecture/security.md`,
`EntityRelationships.txt`, `api/*.md`, `database/schema.md`, `database/er-diagram.md`, `database/sample-data.md`,
and the SQL seed text files in `rentrix-docs/` and `rentrix-docs/database/`.
`architecture/SETUP.md` (frontend tooling setup) and `configuration/*` stay until the implementation PRs replace them.
