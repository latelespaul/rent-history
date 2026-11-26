# Rentrix — UX design (v1)

Status: **current**. Date: 2026-11-21. Revised 2026-11-21 (**design v2**): searchable city picker and list-driven city cards instead of the Bangalore/Pune toggle; verification onboarding with admin approval; admin console. Revised 2026-11-22 (**design v3**): ID verified with any one of Aadhaar (masked), PAN or phone OTP, LinkedIn as an optional bonus badge; browse by area and by gated society, society page, owner profile with all properties and a Homes rating (§2.8, §2.9, §8.2). Revised 2026-11-22 (**design v4**): product name Doorway Living; account-created screen, "Signed in as" toast and return to the page the user came from; phone required for every user, PAN / Aadhaar / LinkedIn as optional extra badges; onboarding straight into the stepper; verification progress in the account menu; rental-agreement verification with tier badges; *open by default*: a user with a verified phone posts at once, everything shows "Not verified yet" until an admin gives the green tick "Verified by Doorway Living", and a mobile admin approvals queue (§2.2, §2.7, §2.10, §8.3, D-132–D-137).
Scope: user flows, screen inventory, visual style, design tokens, icon set and static mockups for the v1 rebuild.
Source of truth for roles, entities, permissions, API, auth and trust rules: [`../architecture/SYSTEM_DESIGN.md`](../architecture/SYSTEM_DESIGN.md).
This document cites its decisions as `D-xx`. It introduces no deviation from them; UX-only choices are listed in §8.

```
rentrix-docs/design/
├── DESIGN.md            this file
├── tokens.css           design tokens (CSS custom properties), imported by the web later
├── icons.html           icon sheet
├── icons/               icons.js (source), <name>.svg (60 files), sprite.svg, style-spec.json, preview.html, LICENSE-lucide
├── mockups/             one static HTML page per screen + mock.css, mock.js, index.html
├── screenshots/         PNG renders: <screen>-desktop.png (1440 px), some also -mobile.png (390 px)
└── tools/               build-icons.mjs, render.mjs (headless Chromium via Playwright)
```
Design-v2 renders (new and changed screens) are also copied to `rentrix-docs/screenshots/design-v2/`; design-v3 renders to `rentrix-docs/screenshots/design-v3/`; design-v4 renders to `rentrix-docs/screenshots/design-v4/`.

---

## 1. Design principles

1. **Proof before opinion.** Every rating sits next to its proof: "Verified tenancy · Apr 2022 – Mar 2024". The verified badge (shield-check icon, teal) is the most repeated element in the product. *Changed by D-169, D-171*: on a review the proof is "Verified tenant", a coarse stay badge ("Stayed < 1 yr", "Stayed 1-2 yrs" or "Stayed 2+ yrs") and the badges, never the stay dates or a year, so the landlord cannot tell which tenant wrote it; the card shows only the year it was published, "Published 2026", for every stay.
2. **Honest numbers only** (D-03). No invented stats or testimonials. Counts come from `GET /stats` and are shown as they are. Under 3 reviews there is no star average, only "New · n reviews" (D-52).
3. **Two sides, two colours.** Tenant things use indigo-blue, landlord things use clay. A user who is both sees both reputations side by side, never mixed (D-18).
4. **Privacy is visible.** Wherever data is hidden, the UI says so with a lock icon and a short reason ("Flat number visible only to the landlord and tenants"). Users must understand why they can or cannot see something (D-54–D-57).
5. **One way in.** "Sign in with Google" is the only production login (D-70). The dev seed login looks deliberately different (purple, dashed, "DEV ONLY") so it can never be mistaken for product UI (D-77).
6. **Structure first.** v1 mockups fix layout, hierarchy and states. Visual polish (photography, illustration) comes later on the same tokens.

---

## 2. User flows

Legend: `[screen]` = a mockup; `→` = navigation; `(API)` = the call behind a step.

### 2.1 Anonymous visitor
```
[Home] ─ pick city (city picker, GET /cities) + locality ─▶ [Search results]
   │                                                     │ open card
   │                                                     ▼
   │                                            [Property page]  facts, images, aggregate rating
   │                                              │  rent history + review text are signed-in only (D-56)
   └──── Sign in ◀──────────────────────────────── "Sign in to see rent history and reviews"
```
- Landlord: only the short name + landlord aggregate on property pages (D-55).
- No tenant data at all (D-54).

### 2.2 Sign in and first-sign-in role pick (both roles)
```
[Sign in] ─ GET /auth/config ─▶ Google button (GIS) ─▶ ID token ─▶ POST /auth/google
    │                                                                   │
    │                                              needsOnboarding? ────┤
    │                                                  yes              │ no
    │                                                   ▼               ▼
    │                                            [Role pick]        back to the page the user came from
    │                                   tenant ☐  landlord ☐ (≥1), home city, terms
    │                                   POST /me/onboarding → fresh access token
    │                                                   ▼
    │                                   tenant → [My tenancies]   landlord → [Landlord dashboard]
    │
    └─ dev profile only: [Dev login] picker → POST /auth/dev-login (seed users, D-77)
```
- Until onboarding completes, every other write returns `403 onboarding_required`; the web routes the user back to [Role pick] (D-73).
- After the role pick comes verification (§2.7). The user can read everything at once. *Changed in design v4 (open by default)*: with a verified phone the user can post at once; trust writes no longer wait for approval (D-132).
- *Changed in design v4* (D-118, D-119): a new user goes role pick → **[Account created]** ("Welcome to Doorway Living, your account is created", name, email, role, "Verify your phone number") → the stepper. A returning user gets the toast **"Signed in as <name>"** ([Returning sign-in toast]) and lands back on the page they came from (`?next=`), not on the role home.
- The other role is added later from settings (`PATCH /me`), shown as an "Also a landlord?" card on [My tenancies] (D-16).
- An invite link (tenancy invite email) goes through the same sign-in, then lands on the tenancy to confirm (D-34).

### 2.3 Tenant
```
            ┌──────────────── [Search results] ◀── [Home]
            ▼
     [Property page] ── Send enquiry (share contact ☐) ──▶ landlord gets tenant-profile access, 90 days (D-31)
            │
            ├─ Record a tenancy (dates, rent) ─▶ PENDING_CONFIRMATION ─▶ landlord confirms ─▶ ACTIVE (D-34)
            │
            ▼
     [My tenancies]  ─ review tasks (GET /me/review-tasks)
            │            ├─ Review the home     ─▶ [Write review] TENANT_TO_PROPERTY  ─▶ published at once (D-47)
            │            └─ Review the landlord ─▶ [Write review] TENANT_TO_LANDLORD ─▶ AWAITING_REVEAL (D-46)
            ├─ confirm / dispute rent changes (D-25)
            ├─ upload agreement (private, optional)
            ├─ end tenancy
            └─ "See what landlords see" ─▶ [Tenant profile] (self view) · create share link (D-54)
     [Landlord profile] ◀── from any property page (signed in, D-55)
```

### 2.4 Landlord
```
     [Landlord dashboard]
        ├─ Add property ─▶ property form (DRAFT → PUBLISHED, D-21)
        ├─ Invite a tenant (email, dates, rent) ─▶ tenant signs in from email, confirms (D-34)
        ├─ Confirm / decline tenancy requests from tenants
        ├─ Enquiries ─▶ [Tenant profile] (access through the enquiry, D-54) ─▶ invite to a tenancy
        ├─ Review your tenant ─▶ [Write review] LANDLORD_TO_TENANT ─▶ AWAITING_REVEAL (D-46)
        ├─ Respond publicly to a review about you (one response, D-42)
        └─ See public profile ─▶ [Landlord profile]
```

### 2.5 Reporting (all signed-in users)
Every review, response, profile, property and tenancy has a "Report" action (flag icon). It opens a small dialog: reason (the 7 reasons of §5.11), optional details, submit (`POST /reports`). A report never hides content, except `PERSONAL_DATA` from a party, which hides the text (D-49, D-50). The dialog is a component, not a separate screen.

### 2.6 Admin
```
[Admin dashboard] ── queue counts, oldest waiting (48 h target), recent decisions   (GET /admin/overview)
   ├─ [Approvals] tabs Landlords | Tenants | Listings
   │     queue item ─▶ detail: profile, LinkedIn (open, Matches / Doesn't match), documents via 5-min link (logged),
   │                   masked ID number, flags (Duplicate ID), history
   │                   ├─ Approve (note optional)          ─▶ APPROVED, user emailed
   │                   └─ Reject (reason code + note)      ─▶ REJECTED, user sees reason, can resubmit
   ├─ [Records] Landlords | Tenants | Properties | Tenancies | Reviews | Reports   search + filters (city picker, status, verification, demo, date)
   │     row ─▶ [Record detail] private fields, roles, verification, related records, audit trail
   ├─ Reports queue ─▶ dismiss / hide / void / suspend (D-51)
   └─ [Cities] add city (created inactive) ─▶ activate ─▶ appears in every picker at once (D-83)
```
- Admins are assigned by `ADMIN_CONSOLE_EMAILS` or by another admin, never self-service (D-19). The admin console has its own host (`admin.doorwayliving.in`, SYSTEM_DESIGN §13.3); the main site never shows an admin link, badge or route. The dev seed admin signs in through the admin host's dev login.
- Every decision writes an audit row: who, when, what, why (D-87). An admin cannot decide their own records (D-88).

### 2.7 Verification (tenant and landlord)
*Changed in design v3*: step 2 is "Verify ID" with three methods, LinkedIn moved to step 3 and is optional (D-99–D-105).
```
[Role pick] ─▶ [1 Profile] ─▶ [2 Verify ID: Aadhaar | PAN | phone OTP, ≥1] ─▶ [3 LinkedIn, optional] ─▶ [4 Rental agreement, tenant, optional] + Submit
                                                                                   │ POST /me/verification/{role}/submit
                                                                                   ▼
                       [Pending] ── admin approves ──▶ [Approved]  trust writes open, "ID verified" badge
                           │
                           └── admin rejects ──▶ [Rejected] reason + note ─▶ fix a step ─▶ resubmit ─▶ [Pending]
```
- Each step saves a draft; the stepper shows done / current / next.
- While not approved, a slim banner on every page links to the status; gated actions (write review, record or confirm a tenancy, enquiry, submit a listing) show [Write review · pending] style states instead of an error (D-91).
- Landlords do steps 1–3; each property is checked at listing time with a proof document (D-92), shown on the landlord's property form (W5).
- Verify ID: phone OTP is verified at once (no admin); PAN and masked Aadhaar wait for an admin check, and the application can be submitted while they wait. "ID verified" shows as soon as any one method is verified; approval still opens trust writes (D-105).

*Changed in design v4* (D-117, D-120, D-121, D-132, D-133):
```
[Account created] ─▶ [1 Profile] ─▶ [2 Phone, required] ─▶ [3 ID, optional: PAN · masked Aadhaar · LinkedIn] ─▶ [4 Rental details, optional] ─▶ Request verification
                                                                                                                          ▼
                                                     [Submitted · waiting]  account menu "Waiting for review" until an admin decides
```
- Phone is the only required check, and it is required only to post; the ID cards and LinkedIn are extra badges with "Skip". PAN and Aadhaar show "Coming soon" until PLAN B12.
- *Changed by D-163 (captain 2026-11-24)*: posting needs no verification. Every "Verify to post" below now reads as the optional nudge "Verify to get a green tick"; the welcome screen leads with "Add your home" and offers verifying second.
- *Changed by D-144*: step 2 is "Verify (any one)": any one verified phone, masked Aadhaar or LinkedIn lets the user post, and step 3 "Aadhaar, LinkedIn, PAN" holds the other methods as extra badges. Read "Verify to post" for "Verify your phone to post" below and in §8.3.
- *Open by default* (D-132, D-133): verification is optional. With a verified phone the user adds homes, tenancies, rent and reviews at once; they show "Not verified yet". The last button reads "Request verification". When an admin approves, the profile gets the green tick "Verified by Doorway Living" (D-134); a rejection keeps everything visible without the tick.
- Rental details (both roles): upload the agreement of the home you rent now or rent out, type its key fields, see the first checks (§2.10).
- The account menu shows "Get verified: n of 4 steps", "Waiting for review", "Changes needed" or "Verify your phone to post" with a dot on the avatar until the green tick ([Account menu progress], D-121, D-133).

### 2.8 Browse by area and society (design v3)
```
[Search] tabs: Homes | Areas | Societies
   Areas ─ city picker + name or PIN code ─▶ [Browse areas]  area cards, area rating (D-108, D-112)
                                                │ open
                                                ▼
                                    area page (societies + standalone homes, same layout as [Browse societies])
   Societies ─ filters: area, kind, rating ─▶ [Browse societies]  society cards, Top rated / Most reviewed
                                                │ open
                                                ▼
                                    [Society page]  towers → wings → homes, society rating, reviews by tower
                                                │ home                      │ owner
                                                ▼                           ▼
                                         [Property page]           [Landlord / owner profile]
                                   breadcrumb City › Area › Society › Tower › Wing
```

### 2.9 Owner profile (design v3)
One landlord can own many properties. The landlord profile lists them all, grouped by city → area → society (standalone homes under their area), with each property's rating, the owner's **Rating as landlord** and the **Homes rating** roll-up of all their homes side by side (D-111, D-113). The owner shows as "Owner", never by name, with up to three badges under the label: "Google verified", "ID verified" and, only when that status exists, "Agreement verified" (D-168, replacing the D-100 pair).

### 2.10 Rental agreement (design v4)
```
[4 Rental details] or tenancy page ─ upload + consent + key fields ─▶ checks 1–8 (D-125) ─▶ tier Unverified
other party ─▶ [Tenancy · confirm agreement] same file (sha256) ─▶ "Agreement matched"
admin ─▶ [Admin · agreement in approvals] SHCIL / IGR lookup by hand ─▶ "Agreement verified"
```
- The tier badge shows on the tenancy, its rent-history rows and review author lines; the public never sees the agreement (D-126).
- The file is encrypted, seen only by the two parties and admins, and deleted 30 days after the later of the admin decision and the other party's confirmation; with no other party within 90 days of upload it is deleted anyway (D-131).
- The agreement tier is an extra signal next to the green tick of the tenancy (D-126, D-134).

---

## 3. Screen inventory

Routes are proposals for the React app. "Mockup" and "Screenshot" paths are relative to this folder.

| # | Screen | Route | Who | Main API | Key rules | Mockup | Screenshot |
|---|---|---|---|---|---|---|---|
| 1 | Home / search entry | `/` | anyone | `GET /cities`, `GET /stats` | D-03, D-20 | `mockups/home.html` | `screenshots/home-desktop.png`, `home-mobile.png` |
| 2 | Search results | `/search?city=BLR` | anyone | `GET /properties` | D-52, D-53, D-56 | `mockups/search.html` | `screenshots/search-desktop.png` |
| 3 | Property page | `/p/:id` | anyone (more when signed in) | `GET /properties/{id}`, `/rating`, `/rent-history`, `/reviews` | D-26, D-52, D-56, D-57 | `mockups/property.html` | `screenshots/property-desktop.png`, `property-mobile.png` |
| 4 | Landlord profile | `/u/:id` | signed in | `GET /users/{id}`, `/reviews?as=LANDLORD` | D-18, D-28, D-55 | `mockups/landlord-profile.html` | `screenshots/landlord-profile-desktop.png` |
| 5 | Tenant profile | `/u/:id?as=tenant`, `/share/:token` | self, own landlords, enquiry grant, share link | `GET /users/{id}`, `/reviews?as=TENANT` | D-31, D-54 | `mockups/tenant-profile.html` | `screenshots/tenant-profile-desktop.png` |
| 6 | Write review | `/tenancies/:id/review` | tenancy party, window open | `POST /tenancies/{id}/reviews` | D-29, D-37–D-43, D-46, D-47 | `mockups/write-review.html` | `screenshots/write-review-desktop.png` |
| 7 | My tenancies | `/me/tenancies` | signed in | `GET /me/tenancies`, `/me/review-tasks` | D-25, D-34, D-39 | `mockups/my-tenancies.html` | `screenshots/my-tenancies-desktop.png` |
| 8 | Landlord dashboard | `/me/dashboard` (Owner mode's home) | any signed-in user (without `LANDLORD`: how to add a home you own; PLAN R10) | `GET /me/properties`, `/me/tenancies?role=LANDLORD`, `/me/enquiries?as=LANDLORD` | D-21, D-34, D-42 | `mockups/landlord-dashboard.html` | `screenshots/landlord-dashboard-desktop.png` |
| 9 | Sign in / sign up (+ dev login) | `/login` | anonymous | `GET /auth/config`, `POST /auth/google`, dev: `/auth/dev-users`, `/auth/dev-login` | D-70–D-77 | `mockups/login.html` | `screenshots/login-desktop.png` |
| 10 | First sign-in role pick | `/welcome` | signed in, not onboarded | `POST /me/onboarding` | D-16, D-73 | `mockups/onboarding.html` | `screenshots/onboarding-desktop.png` |
| – | Icon sheet | – | design | – | §5 | `icons.html` | `screenshots/icons-desktop.png` |
| 11 | Home with many cities (v2) | `/` | anyone | `GET /cities`, `GET /stats` | D-83, D-85 | `mockups/home-many-cities.html` | `screenshots/home-many-cities-desktop.png`, `-mobile.png` |
| 12 | City picker component (v2) | – | everywhere a city is chosen | `GET /cities` | D-20, D-82, D-85 | `mockups/city-picker.html` | `screenshots/city-picker-desktop.png` |
| 13 | Verify 1 · Profile (v2) | `/verify/profile` | signed in, not approved | `PATCH /me` | D-89 | `mockups/verify-profile.html` | `screenshots/verify-profile-desktop.png` |
| 14 | Verify 3 · LinkedIn, optional (v2, changed v3) | `/verify/linkedin` | same | `PUT /me/identity/linkedin` | D-93, D-100 | `mockups/verify-linkedin.html` | `screenshots/verify-linkedin-desktop.png` |
| 15 | Verify 2 · Verify ID (v2, changed v3) | `/verify/id` | same | `GET /me/identity`, `POST /me/documents`, `POST /me/identity/pan` | D-99, D-102, D-105, D-106 | `mockups/verify-id.html` | `screenshots/verify-id-desktop.png`, `-mobile.png` |
| 16 | Verify 4 · Rental agreement + submit (v2; *changed in design v4: replaced by rental_agreements (D-123, D-131)*, see #36) | `/verify/agreement` | tenant | `POST /me/documents` (v2),  `POST /me/verification/TENANT/submit` | D-89, D-90 | `mockups/verify-agreement.html` | `screenshots/verify-agreement-desktop.png` |
| 17 | Verification pending (v2) | `/verify` | `PENDING` | `GET /me/verification` | D-90, D-91 | `mockups/verify-pending.html` | `screenshots/verify-pending-desktop.png` |
| 18 | Verification rejected (v2) | `/verify` | `REJECTED` | `GET /me/verification` | D-90 | `mockups/verify-rejected.html` | `screenshots/verify-rejected-desktop.png`, `-mobile.png` |
| 19 | Verification approved (v2) | `/verify` | `APPROVED` | `GET /me/verification` | D-91, D-95 | `mockups/verify-approved.html` | `screenshots/verify-approved-desktop.png` |
| 20 | Write review · pending approval (v2) | `/tenancies/:id/review`, from a property | not approved | `403 approval_required` | D-91 | `mockups/write-review-pending.html` | `screenshots/write-review-pending-desktop.png` |
| 21 | Admin · Dashboard (v2) | `/admin` | `ADMIN` | `GET /admin/overview` | D-86, D-90 | `mockups/admin-dashboard.html` | `screenshots/admin-dashboard-desktop.png` |
| 22 | Admin · Approvals (v2) | `/admin/approvals?role=` | `ADMIN` | `GET /admin/applications`, `POST …/approve`, `…/reject` | D-86–D-88, D-93–D-95 | `mockups/admin-approvals.html` | `screenshots/admin-approvals-desktop.png` |
| 23 | Admin · Records (v2) | `/admin/records/:type` | `ADMIN` | `GET /admin/users`, `/admin/properties`, `/admin/tenancies`, `/admin/reviews`, `/admin/reports` | D-59, D-86 | `mockups/admin-records.html` | `screenshots/admin-records-desktop.png` |
| 24 | Admin · Record detail (v2) | `/admin/users/:id` (and other types) | `ADMIN` | `GET /admin/users/{id}` | D-86, D-87 | `mockups/admin-record.html` | `screenshots/admin-record-desktop.png` |
| 25 | Admin · Cities (v2) | `/admin/cities` | `ADMIN` | `GET/POST/PATCH /admin/cities` | D-83, D-84 | `mockups/admin-cities.html` | `screenshots/admin-cities-desktop.png` |
| 26 | Verify 2 · Phone code (v3) | `/verify/id/phone` | signed in | `POST /me/identity/phone/start`, `/confirm` | D-101, D-106 | `mockups/verify-phone.html` | `screenshots/verify-phone-desktop.png`, `-mobile.png` |
| 27 | Verify 2 · Masked Aadhaar (v3) | `/verify/id/aadhaar` | signed in | `POST /me/documents`, `POST /me/identity/aadhaar` | D-103, D-106 | `mockups/verify-aadhaar.html` | `screenshots/verify-aadhaar-desktop.png` |
| 28 | Browse areas (v3) | `/areas?city=` | anyone | `GET /areas` | D-108, D-111, D-112 | `mockups/browse-areas.html` | `screenshots/browse-areas-desktop.png` |
| 29 | Browse societies (v3) | `/societies?city=&area=` | anyone | `GET /societies` | D-111, D-112 | `mockups/browse-societies.html` | `screenshots/browse-societies-desktop.png` |
| 30 | Society page (v3) | `/s/:id` | anyone (review text signed in) | `GET /societies/{id}`, `/reviews` | D-107, D-111, D-112, D-57 | `mockups/society.html` | `screenshots/society-desktop.png`, `-mobile.png` |
| 31 | Account created (v4) | `/welcome/created` | right after the role pick | `POST /me/onboarding` response | D-116, D-118 | `mockups/welcome-created.html` | `screenshots/welcome-created-desktop.png`, `-mobile.png` |
| 32 | Returning sign-in toast (v4) | any page, from `?next=` | returning user | `POST /auth/google`, `/auth/dev-login` | D-119 | `mockups/signin-toast.html` | `screenshots/signin-toast-desktop.png` |
| 33 | Account menu progress (v4) | top bar, every page | no green tick yet | `GET /me/verification` | D-121, D-133 | `mockups/account-menu-progress.html` | `screenshots/account-menu-progress-desktop.png` |
| 34 | Verify 2 · Phone, required (v4) | `/verify/phone` | signed in | `POST /me/identity/phone/start`, `/confirm` | D-101, D-117 | `mockups/verify-v4-phone.html` | `screenshots/verify-v4-phone-desktop.png`, `-mobile.png` |
| 35 | Verify 3 · ID, optional (v4) | `/verify/id` | signed in | `GET /me/identity`, `PUT /me/identity/linkedin` (PAN / Aadhaar from B12) | D-100, D-117, D-122 | `mockups/verify-v4-id.html` | `screenshots/verify-v4-id-desktop.png` |
| 36 | Verify 4 · Rental details (v4) | `/verify/rental` | signed in | `POST /me/agreements`, `/confirm` | D-120, D-123–D-127, D-131 | `mockups/verify-v4-rental.html` | `screenshots/verify-v4-rental-desktop.png`, `-mobile.png` |
| 37 | Verification requested · waiting for review (v4) | `/verify` | `PENDING` | `GET /me/verification` | D-90, D-121, D-132, D-133 | `mockups/verify-v4-submitted.html` | `screenshots/verify-v4-submitted-desktop.png` |
| 38 | Tenancy · confirm agreement (v4) | `/tenancies/:id/agreement` | the other party | `GET /tenancies/{id}/agreement`, `POST …/confirm` | D-125, D-126, D-128 | `mockups/tenancy-agreement.html` | `screenshots/tenancy-agreement-desktop.png` |
| 39 | Admin · agreement in approvals (v4) | `/admin/approvals?role=` (detail pane) | `ADMIN` | `GET /admin/applications/{id}`, `POST /admin/agreements/{id}/anchor` | D-124, D-125, D-130, D-134 | `mockups/admin-agreement.html` | `screenshots/admin-agreement-desktop.png` |
| 40 | Admin · approvals on a phone (v4) | `/admin/approvals` (390 px phone layout) | `ADMIN` | `GET /admin/queue` (B13), `POST /admin/{type}/{id}/approve`, `…/reject` | D-134, D-136, D-137 | `mockups/admin-approvals-mobile.html` | `screenshots/admin-approvals-mobile-desktop.png`, `-mobile.png` |

*Changed in design v4*: #31–#40 are new. *Open by default* (D-132–D-134): the v4 screens say "Request verification" and "Waiting for review", and the pending banner reads "Verify your phone to post"; #20 Write review · pending approval and the gate states of #19 and #22 are superseded, because a phone-verified user writes at once and approval only gives the green tick. #40 is the mobile-first queue an admin works from a phone (D-136). They replace #14 (LinkedIn is now a card in #35), #15 and #26 (#34 and #35: phone is its own required step, the ID chooser is optional) and #16 (#36). #10 Role pick now continues to #31; the brand reads Doorway Living on v4 pages (D-116).

*Changed in design v3*: #2 Search (tabs, area and society filters, owner badges), #3 Property (location breadcrumb, owner badges), #4 Landlord profile (now the owner profile: badges, all properties grouped, Homes rating), #5 Tenant profile ("ID verified" badge), #13–#19 verification steps (#15 is now "Verify 2 · Verify ID", the method chooser, route `/verify/id`; #14 is "Verify 3 · LinkedIn, optional"), #22 Admin approvals (identity checks per method, "ID checks" tab). The area page reuses the Browse societies layout filtered to one area, with its standalone homes below.

Screens named in flows but not mocked (they reuse the same components): property create/edit form (with proof upload and "Submit for review", D-92), record-a-tenancy form, tenancy detail (confirm/decline view), settings (profile, add role, verification status per role, share links, delete account), report dialog, admin reports queue (same queue + detail layout as Approvals).

### 3.1 Data in the mockups
All mockups use the dev seed of SYSTEM_DESIGN §16 (D-68, D-69): 4 Bangalore + 4 Pune fictional properties, "Demo Landlord BLR-1", "Demo Tenant PNQ-2" etc., `@example.test` emails, no phones. Public short names follow the D-28/D-55 examples ("Demo L.", "Demo T."). Every seeded row carries the "Demo data" badge (D-68).

| Mockup | Viewed as | Seed rows shown |
|---|---|---|
| Home | anonymous | per-city counts: BLR 4 properties / 6 tenancies, PNQ 4 / 7 |
| Home with many cities, City picker | anonymous | the 2 seed cities plus 7 **illustrative** rows (not in the seed) with "No properties yet" |
| Verify steps, Pending, Write review · pending | Demo Tenant BLR-7 | `PENDING`, LinkedIn + PAN `XXXXXX001D` + agreement (D-97) |
| Verification rejected | Demo Tenant BLR-8 | `REJECTED`, `ID_UNREADABLE` with note |
| Verification approved | Demo Tenant PNQ-2 | `APPROVED` tenant |
| Admin screens | `demo-admin@example.test` | queues: landlord BLR-3, tenants BLR-7 (Duplicate ID) and PNQ-7, listing Demo Annex I; record Demo Landlord BLR-1 |
| Verify ID, phone code (v3) | Demo Tenant BLR-7 | PAN `XXXXXX001D` being checked; phone code screen is **illustrative** (the seed has no phone numbers, D-69) |
| Masked Aadhaar (v3) | Demo Tenant PNQ-7 | phone verified (`XXXXXX0001`), masked Aadhaar `XXXX XXXX 0007` (D-115) |
| Account created, Verify v4 steps, Submitted (v4) | an **illustrative** new user ("New User", `new.user@example.test`) | not in the seed: the screens show a first sign-in; phone and agreement values are fictional |
| Sign-in toast, Tenancy · confirm agreement (v4) | Demo Tenant PNQ-2 / Demo Landlord BLR-1 | Demo Residency A with the seeded `KA_ESTAMP` agreement (D-115 v4) |
| Admin · agreement in approvals (v4) | `demo-admin@example.test` | Demo Tenant PNQ-7 with phone verified, masked Aadhaar pending and the seeded onboarding agreement |
| Browse areas / societies, Society page (v3) | anonymous / tenant | BLR areas with PIN codes; Demo Greens (Koramangala) with Tower 1 / Tower 2 × Wing A / Wing B, Demo Residency A in Tower 1 · Wing A; two **illustrative** societies on Browse societies (dashed outline) to show averages and sort |
| Owner profile (v3) | tenant | Demo Landlord BLR-1: ID + LinkedIn verified; Residency A (4, 3) + Heights B (4) → Homes rating 3.7 from 3 |
| Search | tenant | Demo Residency A, Villa C, Heights B, Studio D (Bangalore) |
| Property | tenant | Demo Residency A: rent 32,000 → 34,000 → 36,500; 2 property reviews; landlord BLR-1 |
| Landlord profile | tenant | Demo Landlord BLR-1: reviews 5, 4, 4 → 4.3 (the D-53 worked example, bayes 3.81) |
| Tenant profile | landlord BLR-1 | a Bangalore tenant who enquired about Demo Heights B; 1 review from Villa C's landlord |
| Write review / My tenancies | Demo Tenant PNQ-2 | current tenancy at Demo Towers E (Baner, ₹27,000); landlord review awaiting reveal |
| Dashboard | landlord BLR-1 | Demo Residency A (occupied), Demo Heights B (vacant) |

States that the seed does not contain (a pending tenancy request, a proposed rent change) are tagged **"illustrative"** in the mockup.

---

## 4. Visual style

Tokens live in [`tokens.css`](tokens.css) as `--rx-*` custom properties. Components use only tokens, never raw values.

### 4.1 Colour

| Role | Token | Value | Use |
|---|---|---|---|
| Page background | `--rx-paper` | `#fbf8f3` | warm paper, the default page colour |
| Surface | `--rx-surface` | `#ffffff` | cards, panels, inputs |
| Sunk | `--rx-sunk` | `#f3eee5` | inset areas, response blocks, image placeholders |
| Text | `--rx-ink-900` / `700` / `500` | `#142229` / `#2f4048` / `#5b6b72` | headings & body / secondary / muted |
| Line | `--rx-line` | `#e4dfd5` | borders, dividers |
| Primary | `--rx-teal-600` | `#0f766e` | primary buttons, links, verified badge, focus |
| Rating | `--rx-marigold-500` | `#e8a317` | filled stars and rating bars **only** |
| Accent | `--rx-cta` / `--rx-cta-fg` | `#f4b23e` / `#142229` | the **one** primary action per screen (button `variant="accent"`); hover `#e8a317`, disabled `#f6e3bd` / `#5c4a1f` |
| Tenant | `--rx-tenant-600` / `100` | `#3559b0` / `#e2e9fa` | tenant badges, avatars, role cards |
| Landlord | `--rx-landlord-600` / `100` | `#b0532d` / `#f8e4da` | landlord badges, avatars, response accent |
| Success / Warn / Danger / Info | `--rx-*-600` / `100` | see tokens | status badges and notices |
| Dev only | `--rx-dev-600` / `100` | `#7a3bd1` / `#efe5fc` | dev-login panel and mockup spec tags; never in prod UI |

Rules: two base colours, teal and cream (paper), do 90% of the work. Teal means "trust / action". Marigold appears in two places only: rating stars and bars (`--rx-marigold-*`), and the accent button. Red appears only for errors and destructive actions. Text on tinted badges uses the `600` shade on the `100` tint (contrast ≥ 4.5:1). A dark theme exists as token overrides under `[data-theme="dark"]`; v1 ships light only.

**One accent, one primary CTA per screen.** The accent (`--rx-cta`, marigold `#f4b23e`) goes only on the single action we most want pressed on a screen; every other button stays teal or neutral. Today that is three buttons: the landing hero's "Sign in / Sign up with Google", "Submit review" on the review form, and WhatsApp in the Invite friends sheet. A new accent use replaces one of these on its screen, never adds a second; `button.test.ts` lists the uses. Text on the accent is near-black `#142229` (8.7:1), never white (1.9:1, fails AA). The cta tokens are fixed in both themes; its focus ring is a 2 px `--rx-ink-900` outline, and disabled uses its own pale tint with dark text instead of fading.

### 4.2 Typography
- **Display** (`--rx-font-display`, Fraunces → Georgia fallback): page titles and the hero only. Weight 600.
- **Sans** (`--rx-font-sans`, Inter → system UI): everything else.
- **Mono**: code and the dev panel.
- Scale (rem): 0.75 · 0.875 · 1 (body) · 1.125 · 1.25 · 1.5 · 1.875 · 2.5 (hero). Line height 1.5 body, 1.2 headings.
- Money and ratings use tabular figures (`font-variant-numeric: tabular-nums`). Money is "₹36,500" with Indian digit grouping (`en-IN`), "/ month" after it (D-13).
- Dates in public views are month + year ("Apr 2022 – Mar 2024"); exact dates appear only to parties (D-57).

### 4.3 Spacing, radius, elevation, layout
- 4 px base: `--rx-space-1` (4) … `--rx-space-16` (64). Card padding 20, section padding 32, gaps 12–24.
- Radius: 6 (small controls), 10 (buttons, inputs), 16 (cards), pill (badges, chips).
- Two shadows: `sm` for cards, `md` for the search bar and popovers.
- Container 1120 px. Desktop layouts: main + 340 px sidebar (property, profiles, dashboard) or 260 px filter rail + results (search). Below 860 px everything becomes one column with a 16 px side gutter; on the property page the rating card moves above the content.

### 4.4 Components (mockup class → React component)

| Component | Class | Notes |
|---|---|---|
| Top bar | `.topbar` | brand, role-aware nav, "Demo data" badge, avatar; hamburger under 860 px |
| Button | `.btn` + `-primary`, `-secondary`, `-ghost`, `-sm`, `-lg`, `-block` | one primary per area |
| Google button | `.btn-google` | placeholder only; production renders the official GIS button (D-76) |
| Dev button / panel | `.btn-dev`, `.notice-dev` | purple dashed, "DEV ONLY" badge |
| Card | `.card` | surface + border + `sm` shadow |
| Property card | `.pcard`, `.pcard-h` | image, title, locality, facts, property rating, landlord rating, last confirmed rent (signed in) |
| Badge | `.badge` + `-verified`, `-demo`, `-tenant`, `-landlord`, `-success`, `-warn`, `-info`, `-dev` | tenancy and review states map to these (§4.5) |
| Stars | `.stars[data-stars]` | 5 star icons, filled = marigold; sizes sm/md/lg |
| Rating summary | `.score`, `.dist`, `.subratings` | average with one decimal, 5-row distribution, sub-rating averages |
| "New" rating | `.rating-new` | shown instead of the average when n < 3 (D-52) |
| Review block | `.review`, `.response` | author short name, verified period badge, stars, sub-ratings, text, response, report |
| Rent history | `.rentchart` + `.table` | step chart + table; gaps show as "vacant"; no tenant identity (D-26) |
| Notice | `.notice` + `-teal`, `-warn`, `-dev` | explains access and rules in plain words |
| Form | `.field`, `.label`, `.hint`, `.input`, `.select`, `.textarea`, `.checkrow` | inline validation under the field |
| Segmented / chips / tabs | `.segmented`, `.chip`, `.tabs` | small fixed option sets only (bedrooms, sort); **never for cities** |
| City picker (v2) | `.combo`, `.combo-pop`, `.combo-opt` | searchable combobox fed by `GET /cities`; filters on name, alias, state; no-match state; ARIA combobox + listbox (D-85) |
| City cards (v2) | `.city-grid`, `.city-card` | one card per active city, from the list; 2-column for 2 cities, 4-column grid beyond; first 8 + "Show all" |
| Stepper (v2) | `.stepper` | verification steps: done / current / next |
| File row / drop zone / document view (v2) | `.filerow`, `.drop`, `.docview` | private uploads; "View" opens a 5-min signed link (D-95) |
| Masked ID (v2) | `.masked` | mono, last 4 characters only, e.g. `XXXXXX001D` (D-94) |
| Status hero / pending banner (v2) | `.status-hero`, `.banner-pending` | verification state pages and the site-wide "not verified yet" banner |
| Admin (v2) | `.admin-tabs`, `.queue`, `.qitem`, `.filters`, `.timeline`, `.switch`, `.dialog`, `.badge-admin` | queue + detail layout, filter bar, audit timeline, city activation switch |
| Stat tile | `.stat` | only real numbers from the API |
| LinkedIn badge (v3) | `.badge-linkedin` | info blue with the link icon; never the LinkedIn logo (§5) |
| Identity method cards (v3) | `.methods`, `.method` (+ `.on`, `.done`) | Verify ID chooser: phone, PAN, masked Aadhaar |
| OTP input (v3) | `.otp` | 6 boxes, mono digits, resend timer below |
| Breadcrumb (v3) | `.crumbs` | City › Area › Society › Tower › Wing › home |
| Location card (v3) | `.loc-card` | area and society cards on browse pages |
| Towers grid (v3) | `.towers`, `.tower`, `.wing`, `.home-row` | society page: one block per tower, a row per wing |
| Group heading (v3) | `.group-h` | owner portfolio groups (city · area · society) |
| Illustrative outline (mockups only) | `.illustrative` | dashed purple outline on rows not in the seed |

### 4.5 State vocabulary

| State | Badge | Icon |
|---|---|---|
| Tenancy `PENDING_CONFIRMATION` | info "Pending" | clock |
| Tenancy `ACTIVE` | success "Active" | tenancy |
| Tenancy `ENDED` | neutral "Ended" | – |
| Tenancy `DECLINED` / `EXPIRED` / `CANCELLED` / `VOIDED` | neutral / danger for voided | x |
| `evidence_level` MUTUAL / DOCUMENT | verified "Confirmed by both" / verified "Agreement checked" | shield-check |
| Review `AWAITING_REVEAL` | warn "Awaiting reveal · <date>" | clock |
| Review `PUBLISHED` (edited) | "Published <year> · edited" in meta | – |
| Review `HIDDEN` | author sees danger "Hidden by moderator" + reason | eye-off |
| Rent record `PROPOSED` / `DISPUTED` | info "To confirm" / danger "Disputed" | rupee |
| Demo row | demo "Demo data" | – |
| Verification `DRAFT` (v2) | neutral "Not submitted" + banner | – |
| Verification `PENDING` (v2) | info "Pending approval" | clock |
| Verification `REJECTED` (v2) | danger "Not approved" + reason notice | alert |
| Verification `APPROVED` (v2) | success "Approved" | shield-check |
| ID verified, any of Aadhaar / PAN / phone (v3, public) | verified "ID verified" | shield-check |
| LinkedIn verified (v3, public, optional) | info "LinkedIn verified" | link |
| Identity method `PENDING` / `VERIFIED` / `REJECTED` (v3, owner + admin) | info "Being checked" / success "Verified" / danger "Not verified" + reason | clock / check / alert |
| Location row `PENDING` (v3, proposer + admin) | info "Pending review" | clock |
| Listing `PENDING_REVIEW` / `REJECTED` (v2) | info "In review" / danger "Not approved" + reason | clock / alert |
| Flag `DUPLICATE_ID` (admin only, v2) | warn "Duplicate ID" | alert |
| City inactive (admin only, v2) | switch off, "Inactive" | city |

---

## 5. Icon set

62 icons in [`icons/`](icons/), previewed in [`icons.html`](icons.html) and the self-contained [`icons/preview.html`](icons/preview.html) (24 px, 48 px and on dark); [`icons/style-spec.json`](icons/style-spec.json) lists the rules and every name.

- 24 × 24 grid, 2 px safe padding; one stroke 1.75 px, round caps and joins; no fills; `currentColor` only. The star is the one icon whose "on" state is filled, and only through CSS.
- Sizes 16 / 20 (default) / 24. Do not scale stroke width.
- Decorative next to a label (`aria-hidden="true"`); icon-only buttons need `aria-label`.
- Product metaphors: **landlord** = person under a roof, **tenant** = person in a doorway, **tenancy** = house with a check (also the brand mark), **shield-check** = verified, **eye-off** = double-blind / hidden, **lock** = private field, **flag** = report, **trend-up** = rent history, **rupee** = money.
- Doorway set (v2, captain 2026-11-25): **rent-receipt**, **society-gate**, **owner-key** (house with a key), **review-card**, **agreement** (signed document), **verified-tenant** (person with a check), **city-bangalore** and **city-pune** (city marks), drawn on the same grid as the 49 v1 icons.
- Source of truth is `icons/icons.js` (path data). `tools/build-icons.mjs` writes `icons/<name>.svg`, `icons/sprite.svg`, `icons/style-spec.json` and `icons/preview.html`; `rentrix-web` copies the path data with `npm run sync:design`.
- No third-party icon *set* is installed. A missing common UI glyph may be ported from [Lucide](https://lucide.dev) and redrawn on this grid at stroke 1.75; `PORTED` in `icons.js` lists each one (now `document-x`, `badge-check`, `map-pin-check`, `phone` from Lucide `smartphone`, and `id-card`). No Google logo is redrawn: the Google button in the mockup is a neutral placeholder; production uses Google's own mark.
- Licences: the v1 and Doorway icons are original to this project (project licence). Lucide ports are under the ISC licence (Lucide contributors; Feather portions MIT), kept in [`icons/LICENSE-lucide`](icons/LICENSE-lucide); ISC needs no credit in the UI.

---

## 6. Accessibility

- Contrast: body text ≥ 7:1 on paper; muted text ≥ 4.5:1; badge text uses 600-on-100 pairs.
- Stars always have a text equivalent (`aria-label="4 out of 5 stars"`) and the numeric average next to them.
- Colour is never the only signal: role badges carry a word ("Tenant", "Landlord"), states carry a label.
- Focus ring: 2 px `--rx-focus` outline with 2 px offset on every interactive element.
- Touch targets ≥ 40 px; the city control and primary buttons are 44–48 px on mobile.
- `prefers-reduced-motion` zeroes the motion tokens.

---

## 7. How to view and regenerate

View the mockups directly (they work from `file://`):
```
open rentrix-docs/design/mockups/index.html
```
or serve the folder: `python3 -m http.server 8090 -d rentrix-docs/design` → http://localhost:8090/mockups/

Regenerate icons and screenshots (local headless Chromium only; starts and stops its own static server):
```
cd rentrix-docs/design/tools
npm install            # playwright 1.49.1
npm run browsers       # once, if Chromium is not cached yet
npm run render         # build icons, then write screenshots/*.png
npm run render:v2      # same, and copy the design-v2 renders to rentrix-docs/screenshots/design-v2/
npm run render:v3      # render only the design-v3 new and changed screens, copy them to rentrix-docs/screenshots/design-v3/
npm run render:v4      # render only the design-v4 screens, copy them to rentrix-docs/screenshots/design-v4/
```

Screenshots of implemented features (from the real app with dev login) go to `rentrix-docs/screenshots/` in the implementation PRs; this folder holds design renders only.

---

## 8. UX choices made here (within SYSTEM_DESIGN)

- **Last confirmed rent on search cards** is shown only to signed-in users, because full rent history is signed-in only (D-56). Anonymous cards show facts and ratings.
- **Ratings with n < 3** show a "New · n reviews" pill everywhere (cards, sidebars, dashboard), never a partial star row (D-52). Landlord aggregates in the seed reach 3, property aggregates do not, so both states appear in the mockups.
- **Property page tabs** offer "Property reviews" and a shortcut to the landlord's reviews; landlord review text still opens on the landlord profile, which requires sign-in (D-55).
- **Enquiry consent** ("Share my email with the landlord") is a visible checkbox on the enquiry card, default on, and the landlord sees "shared with you" next to the contact (D-31).
- **Double-blind is explained in place**: the author sees "Awaiting reveal · <date>" and the date it will publish at the latest (D-46).
- **Brand mark** = the tenancy icon in a teal tile: the product is about confirmed tenancies.

### 8.1 Design v2 choices
- **City picker is a combobox, not a select.** A native select does not search aliases ("Bengaluru") or states, and a toggle does not scale. One component serves Home, Search, onboarding, the property form and admin filters (D-85).
- **City cards switch layout by count**: two wide cards for 2 cities, a 4-column grid beyond; a new city shows "No properties yet", never a placeholder number (D-03).
- **Launch copy names up to 3 cities, then counts** ("Live in 9 cities, more coming"), so the line never grows unbounded.
- **Verification is a 4-step stepper with drafts**, so a user can stop after any step. Rental agreement is optional for tenants; landlords prove each property when listing instead (D-89, D-92).
- **"I don't have a LinkedIn profile" is a visible option**, because many tenants have none; the admin then relies on the ID document (D-93).
- **The ID number is shown masked as soon as it is accepted**; the typed value is never echoed back (D-94).
- **Pending users see a calm banner, not errors.** Gated pages show what is done, what is next and a link to the status; reading stays open (D-91).
- **Admin approvals use a queue + detail layout** with approve and reject side by side; reject cannot be sent without a reason code and a note (D-87).
- **Admin is visually distinct**: an ink "Admin" badge in the top bar and admin-only nav, so staff always know they are acting as admin.

### 8.2 Design v3 choices
- **"One is enough" is the headline of the ID step.** Three equal method cards (phone, PAN, masked Aadhaar) instead of a type picker, so users see at once that any one works. Phone comes first because it verifies instantly (D-99, D-101).
- **LinkedIn is its own optional step with a "Skip" button**, placed after the ID step, and it never appears in reject reasons. Its only effect is the second badge (D-100).
- **Aadhaar is masked-copy only.** The screen says how to download the masked copy from UIDAI, warns before the upload, and the input takes 4 digits only; the full number is never typed (D-103).
- **ID files are deleted at decision.** The PAN and Aadhaar screens say the file is kept only until an admin decides and is then deleted; only the result and the last 4 stay (D-96, D-106).
- **Badges, not details.** Public views show "ID verified" and "LinkedIn verified" only; the method, last 4 and phone stay with the owner and admins (D-100, D-106).
- **Browse is three tabs of one search** (Homes, Areas, Societies) so the city picker and filters stay in one place (D-112).
- **Society page is organised by building**: one block per tower, a row per wing, homes inside. Empty wings say "No homes listed yet" instead of hiding, so the structure is clear. Flat numbers never appear (D-57, D-107).
- **Owner profile shows two ratings side by side**: "Rating as landlord" (the person) and "Homes rating" (all their homes). A roll-up often crosses the 3-review threshold before any single property does (D-52, D-111).
- **No new icons**: phone uses `message`, PAN `document`, Aadhaar `user`, LinkedIn `link`, society and tower `building`, area `map-pin`. No third-party logos.

### 8.3 Design v4 choices
- **The welcome is a moment, not a redirect.** A green tick, "your account is created", the facts the user gave (name, email, role) and one primary next step. "Not now" is always there, so the user is never forced into the stepper (D-118).
- **A toast, not a page, for returning users.** "Signed in as <name>" confirms which Google account was used and gets out of the way; the user lands where they were (D-119).
- **Phone first, badges second.** The stepper marks Phone "Required" and ID "Optional"; the ID step opens with "Phone verified: that is all you need to post", so optional cards never feel like a blocker (D-117).
- **Open by default, tick for trust.** Nothing waits for an admin: posts show at once with a neutral "Not verified yet" badge, and approval adds the green tick "Verified by Doorway Living". A rejection never hides content; only abuse reports and admin hide do (D-132, D-134).
- **Two ratings, never mixed.** The headline average counts green-tick reviews only; unverified reviews show next to it with their own count and average, labelled "Not verified yet" (D-135).
- **Approvals fit a thumb.** The admin queue on a phone is one card per item with Approve and Reject as large side-by-side buttons; approve is one tap, reject is two (reason sheet) (D-136).
- **Preview SMS through the admin.** With `SMS_BACKEND=admin-relay` the pending code shows on the user's queue card next to the masked phone, so the preview works without an SMS provider (D-137).
- **Progress lives where the name is.** The account menu row with a thin bar and the next step, plus a dot on the avatar, replaces hunting for a status page (D-121).
- **Checks are explained, not scored silently.** Each rental-agreement check shows a one-line reason; "Later" marks checks that need the other party or an admin, so a user never reads a pending check as a failure (D-125).
- **Aadhaar warning before the scan.** The upload card warns to cover Aadhaar numbers before scanning, because the manual extractor cannot redact a file (D-131).
- **The government lookup is a button for the admin, never for the app.** The admin pane links to the portal and records a tick with a note (D-130).
- **No new icons**: agreement tiers reuse `shield-check` (verified) and `document` (matched); the menu row uses `shield-check`, `clock` and `alert`.
