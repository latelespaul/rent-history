# Rentrix — UX design (v1)

Status: **current**. Date: 2026-11-21.
Scope: user flows, screen inventory, visual style, design tokens, icon set and static mockups for the v1 rebuild.
Source of truth for roles, entities, permissions, API, auth and trust rules: [`../architecture/SYSTEM_DESIGN.md`](../architecture/SYSTEM_DESIGN.md).
This document cites its decisions as `D-xx`. It introduces no deviation from them; UX-only choices are listed in §8.

```
rentrix-docs/design/
├── DESIGN.md            this file
├── tokens.css           design tokens (CSS custom properties), imported by the web later
├── icons.html           icon sheet
├── icons/               icons.js (source), <name>.svg (49 files), sprite.svg
├── mockups/             one static HTML page per screen + mock.css, mock.js, index.html
├── screenshots/         PNG renders: <screen>-desktop.png (1440 px), home/property also -mobile.png (390 px)
└── tools/               build-icons.mjs, render.mjs (headless Chromium via Playwright)
```

---

## 1. Design principles

1. **Proof before opinion.** Every rating sits next to its proof: "Verified tenancy · Apr 2022 – Mar 2024". The verified badge (shield-check icon, teal) is the most repeated element in the product.
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
[Home] ─ pick city (Bangalore | Pune) + locality ─▶ [Search results]
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
Admin moderation (reports queue, hide/restore, void, suspend, audit) is a later screen set. It uses the same components (tables, badges, review block). Not mocked in v1.

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
| 8 | Landlord dashboard | `/me/dashboard` | `LANDLORD` | `GET /me/properties`, `/me/tenancies?role=LANDLORD`, `/me/enquiries?as=LANDLORD` | D-21, D-34, D-42 | `mockups/landlord-dashboard.html` | `screenshots/landlord-dashboard-desktop.png` |
| 9 | Sign in / sign up (+ dev login) | `/login` | anonymous | `GET /auth/config`, `POST /auth/google`, dev: `/auth/dev-users`, `/auth/dev-login` | D-70–D-77 | `mockups/login.html` | `screenshots/login-desktop.png` |
| 10 | First sign-in role pick | `/welcome` | signed in, not onboarded | `POST /me/onboarding` | D-16, D-73 | `mockups/onboarding.html` | `screenshots/onboarding-desktop.png` |
| – | Icon sheet | – | design | – | §5 | `icons.html` | `screenshots/icons-desktop.png` |

Screens named in flows but not mocked in v1 (they reuse the same components): property create/edit form, record-a-tenancy form, tenancy detail (confirm/decline view), settings (profile, add role, share links, delete account), report dialog, admin screens.

### 3.1 Data in the mockups
All mockups use the dev seed of SYSTEM_DESIGN §16 (D-68, D-69): 4 Bangalore + 4 Pune fictional properties, "Demo Landlord BLR-1", "Demo Tenant PNQ-2" etc., `@example.test` emails, no phones. Public short names follow the D-28/D-55 examples ("Demo L.", "Demo T."). Every seeded row carries the "Demo data" badge (D-68).

| Mockup | Viewed as | Seed rows shown |
|---|---|---|
| Home | anonymous | per-city counts: BLR 4 properties / 6 tenancies, PNQ 4 / 7 |
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
| Tenant | `--rx-tenant-600` / `100` | `#3559b0` / `#e2e9fa` | tenant badges, avatars, role cards |
| Landlord | `--rx-landlord-600` / `100` | `#b0532d` / `#f8e4da` | landlord badges, avatars, response accent |
| Success / Warn / Danger / Info | `--rx-*-600` / `100` | see tokens | status badges and notices |
| Dev only | `--rx-dev-600` / `100` | `#7a3bd1` / `#efe5fc` | dev-login panel and mockup spec tags; never in prod UI |

Rules: marigold is reserved for ratings, so a yellow element always means "rating". Teal means "trust / action". Red appears only for errors and destructive actions. Text on tinted badges uses the `600` shade on the `100` tint (contrast ≥ 4.5:1). A dark theme exists as token overrides under `[data-theme="dark"]`; v1 ships light only.

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
| Segmented / chips / tabs | `.segmented`, `.chip`, `.tabs` | city switch is always a 2-option segmented control |
| Stat tile | `.stat` | only real numbers from the API |

### 4.5 State vocabulary

| State | Badge | Icon |
|---|---|---|
| Tenancy `PENDING_CONFIRMATION` | info "Pending" | clock |
| Tenancy `ACTIVE` | success "Active" | tenancy |
| Tenancy `ENDED` | neutral "Ended" | – |
| Tenancy `DECLINED` / `EXPIRED` / `CANCELLED` / `VOIDED` | neutral / danger for voided | x |
| `evidence_level` MUTUAL / DOCUMENT | verified "Confirmed by both" / verified "Agreement checked" | shield-check |
| Review `AWAITING_REVEAL` | warn "Awaiting reveal · <date>" | clock |
| Review `PUBLISHED` (edited) | "Published <month> · edited" in meta | – |
| Review `HIDDEN` | author sees danger "Hidden by moderator" + reason | eye-off |
| Rent record `PROPOSED` / `DISPUTED` | info "To confirm" / danger "Disputed" | rupee |
| Demo row | demo "Demo data" | – |

---

## 5. Icon set

49 original icons in [`icons/`](icons/), previewed in [`icons.html`](icons.html).

- 24 × 24 grid, 2 px safe padding; one stroke 1.75 px, round caps and joins; no fills; `currentColor` only. The star is the one icon whose "on" state is filled, and only through CSS.
- Sizes 16 / 20 (default) / 24. Do not scale stroke width.
- Decorative next to a label (`aria-hidden="true"`); icon-only buttons need `aria-label`.
- Product metaphors: **landlord** = person under a roof, **tenant** = person in a doorway, **tenancy** = house with a check (also the brand mark), **shield-check** = verified, **eye-off** = double-blind / hidden, **lock** = private field, **flag** = report, **trend-up** = rent history, **rupee** = money.
- Source of truth is `icons/icons.js` (path data). `tools/build-icons.mjs` writes `icons/<name>.svg` and `icons/sprite.svg`; the React app can import the SVG files (e.g. via `vite-plugin-svgr`) or use the sprite.
- No third-party icon sets and no Google logo is redrawn: the Google button in the mockup is a neutral placeholder; production uses Google's own GIS button.

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
