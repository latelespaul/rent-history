# Rentrix — Roadmap

The story and history of a place before you move in.

**Design principles**
1. **Reviews first, listings second** — the product is tenant trust
2. **Anyone can add a review target** — bootstrap the corpus
3. **Landlord verification is opt-in** — create incentive, don't gate participation
4. **Moderation over prevention** — let users create, moderate afterward
5. **Proof-of-living compounds trust** — signals tier over time

---

## ✅ v0.1.0 — Foundation
*Shipped*

- Auth (JWT + refresh)
- Flat listings + detail pages
- Deploy pipeline (Vercel + Render + Aiven)
- Docker multi-stage build

---

## ✅ v0.1.1 — Rich flat data
*Shipped*

- Flat entity expansion (embedded Address, richer fields)
- PATCH support for partial updates
- Soft-delete via Hibernate `@SQLDelete` + `@SQLRestriction`
- Extended filters (furnished, parking, propertyType)
- Admin controls (verify, hide)
- Frontend updates for new Flat shape

---

## ✅ v0.1.2 — Tenant-created review targets
*Shipped*

**Core insight:** reviews are the product, listings are an ingredient.
Tenants should be able to add a flat they've lived in so they can review it,
even if no landlord has listed it.

### Backend
- `createdBy`, `verified`, `visible` fields on `Flat`
- Any authenticated user can create a flat (unverified)
- Landlord/admin-created flats default to `verified = true, visible = true`
- First approved review auto-promotes a hidden flat to visible
- Batch-compute `averageRating` + `reviewCount`
- Review-based filters (`minRating`, `hasReviews`)

### Frontend
- "Add a place to review" flow
- My Flats page with role-aware labels
- FlatForm (create + edit)
- Verified badge on cards and detail
- Review filter UI
- Navbar overhaul (avatar dropdown, "Add place" CTA)

---

## ✅ v0.1.3 — Legal, images, and verified stays
*Shipped*

### Legal
- Privacy Policy (GDPR + DPDP + CCPA)
- Terms of Service

### Flat images
- S3 storage (`rentrix-media`, public read)
- EXIF stripping, resize to 1920px, JPEG output
- Multi-image gallery with lightbox
- Owner-only uploader (drag-drop, set-primary, delete)
- Max 10 images per flat

### Proof-of-living
- S3 private bucket (`rentrix-private`)
- Upload rent agreement, utility bill, or bank statement
- 15-minute presigned URL for admin view
- "Verified stay" badge on approved reviews with proof

---

## ✅ v0.1.4 — Multi-file uploads
*Shipped*

- Multi-proof schema (`review_proofs` table, up to 3 per review)
- Grid-based uploader (5 cols for flat images, 3 cols for proofs)
- Multi-file picker support
- Admin proof list + per-file signed URL opening
- Multipart upload limits raised to 15 MB

---

## ✅ v0.1.5 — Foundation audit
*Shipped 2026-10-13 — no new features*

Full backend audit. Cleaned up debt before v0.1.6.

### Critical fixes
- NPE on `FlatSpecification` when filters empty
- Wrong field name for `available` filter (`isAvailable` → `available`)
- Deleted reviews counted in `averageRating` and `hasReviews` aggregates
- In-memory pagination from EntityGraph on paginated queries
- Test context broken by `@Profile("!test")` on S3 beans
- `User` soft-delete was actually hard-delete (added `@SQLDelete` + `@SQLRestriction`)
- Aiven schema drift (`missing column [name]`) — backfilled via SQL

### Hardening
- `@SQLDelete` + `@SQLRestriction` on `Review`, `FlatImage`, `ReviewProof`
- `@BatchSize(20)` on `Flat.images` and `Review.proofs` (replaces EntityGraph on paginated queries)
- Added validation to `User` entity and `AddressDto`
- Fixed `User.getUsername()` contract (was returning `firstName`)
- Removed dead code: `UserServiceImpl`, `Issue` feature, unused mappers, duplicate repository

### Tests
- Suite grew: 64 → 67
- Fixed broken assertions (Java `assert` is a no-op without `-ea`)
- Added `-ea` to Surefire
- New tests: `available` filter, self-review prevention, unverify flat

### Config
- `pom.xml` cleanup (removed Postgres, H2 to test scope, versions to properties)
- Removed unused `problemDetails.enabled` and dialect overrides
- `docker-compose.yml` uses MySQL 8.4 (was Postgres)

---

## 🚧 v0.1.6 — Upload hardening + observability
*Next*

### Upload hardening (top priority)
Real users hit 502s on large image uploads. Free-tier Render (512 MB)
OOMs during Thumbnailator image processing.

- [ ] Client-side image compression (`browser-image-compression`)
  - Resize in browser before upload
  - Cuts server memory 10× and eliminates OOM risk
- [ ] Presigned S3 URLs — browser uploads directly, backend only stores metadata
- [ ] Async image processing on a background thread
- [ ] Increase dimension cap (6000×6000 → 8000×8000) after memory work
- [ ] Consider Cloudflare R2 (better latency, zero egress)
- [ ] Move S3 bucket to `us-west-1` to match Render region

### Observability
- [ ] Prometheus + Grafana (local + Grafana Cloud free tier)
- [ ] Actuator `/prometheus` endpoint exposure
- [ ] Custom business metrics (signups/day, uploads/day, ratings submitted)
- [ ] Alerts on 5xx spike, OOM, high latency

### Features
- [ ] Bottom navigation on mobile (hamburger still works)
- [ ] Loading skeleton polish
- [ ] Denormalized `averageRating` + `reviewCount` for scale
- [ ] Password reset flow
- [ ] SEO / OG meta tags

### Infrastructure
- [ ] Flyway for schema migrations (replaces manual `ALTER TABLE`)
- [ ] Upgrade Render to Starter ($7/mo) — removes cold starts, 5× CPU

---

## 🚧 v0.2.0 — Landlord claiming
*Planned*

- [ ] Landlord "claim this flat" flow (email/phone verification)
- [ ] Claimed flats show "Verified by landlord" badge
- [ ] Landlord can mark flat `available = true` → appears in rental listings
- [ ] Reviews from verified stays ranked higher than unverified
- [ ] Admin analytics dashboard

---

## 💭 v0.3.0+ — Later

- [ ] Flat amenities as a list
- [ ] Map integration (lat/lng on address)
- [ ] Email verification
- [ ] Image uploads on reviews (photos of the flat)
- [ ] Public API for third-party integrations
- [ ] Custom domain (`rentix.app`)
- [ ] Payment rails (if ever needed)

---

## 🗂 Archived — deferred / superseded

Items that were scoped but dropped or replaced:

- ~~`PageResponse<T>` DTO~~ — shipped in v0.1.2
- ~~Address normalization + fuzzy dedup~~ — deferred indefinitely; not a priority at current scale
- ~~Email verification~~ — deferred to v0.3.0+