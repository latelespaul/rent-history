# Invite friends (design note)

Status: **built** (slices 1–3; SYSTEM_DESIGN D-173). Date: 2026-11-26. Captain's ask: 2026-11-26 19:34 IST, "i want to invite people on platform".
Each slice of §8 ships as its own small PR through no-mistakes, DEV first; a healthy DEV goes LIVE.

This note adds a general "Invite friends" feature: any signed-in user shares a link to Doorway Living, and sees how many people joined from it. It extends SYSTEM_DESIGN §5.12, §10 and §13.1. Rules there stay unless a line below says otherwise.

Decisions proposed here are labelled **IF-1 … IF-9**. Each build PR records the decisions it builds in SYSTEM_DESIGN.md at the next free D-number.

---

## 0. v1 in short

1. A signed-in user taps **Invite friends**: in the account menu, in the phone menu sheet, on Settings, and on a "Help the next tenant" card after they submit a review.
2. On a phone the tap opens the native share sheet (`navigator.share`). Where that is missing or fails, a small sheet offers **WhatsApp** (`wa.me`) and **Copy link**.
3. The shared text is short and honest, with the link `https://doorwayliving.in/?ref=<code>`.
4. `<code>` is a random 10-character code per user. It is not made from the user's id, name, email or phone.
5. A visitor who opens the link keeps the code in the browser. When they sign in for the first time and so create an account, the API records one referral for the code's owner.
6. The owner sees "N friends joined" on Settings. Nobody else ever sees a code, a count or who joined.
7. No rewards in v1.

---

## 1. What already exists

| Piece | Where | What it does today | Changed here? |
|---|---|---|---|
| Tenancy invite | `pages/TenancyInvitePage.tsx` (`/invite/:token`), `POST /tenancy-invites/claim`, `invite_tokens`, `TenancyNotifier.invite` | A landlord's emailed, single-use link binds one tenancy to the tenant's account with the same verified email | **No.** It stays as it is. "Invite friends" is a separate feature with its own tables and copy |
| Share links | `share_links`, `/me/share-links` | A tenant shares their own reputation for 30 days (D-54) | No |
| Account creation | `AuthService.googleUser` → `newGoogleUser` (Google, D-72); `DevLoginController` `POST /auth/dev-login/new` (dev only) | The first Google sign-in creates the `users` row | Yes: both accept an optional `ref` (§4) |
| Google sign-in in the web | `GoogleButton.tsx` (GIS, `ux_mode: "popup"`), `LoginPage.tsx`, `api/auth.ts signInWithGoogle` | The page never leaves the site: GIS returns the ID token to a callback, the web posts it to `POST /auth/google` | Yes: the web sends the kept code with it (§3) |
| Account menu | `components/layout/TopBar.tsx` `AccountMenu`, and the phone `Sheet` | Verification row, Settings, Contact us, Sign out | Yes: one "Invite friends" item |
| Review submitted | `pages/WriteReviewPage.tsx` `Submitted` | "You submitted this review", the home link and what was published | Yes: one "Help the next tenant" card at the end |
| Settings | `pages/SettingsPage.tsx` | Profile, Roles, Account | Yes: one "Invite friends" section with the count |
| Account deletion | `AccountDeletionService`, `AccountErased` (D-58) | Erases personal data in one transaction | Yes: also deletes the user's code and referral rows (IF-8) |
| Icons | `rentrix-docs/design/icons/` (Lucide-based set), `components/ui/icons.gen.ts` | `users`, `link`, `message`, … | Yes: add one `share` icon in the same style |

---

## 2. Data model (IF-1, IF-2)

One Flyway migration, at the next free `V` number when the PR is made (`V21` today). Same portable SQL subset as V1 (D-10).

```
invite_codes                              referrals
───────────────────────────               ─────────────────────────────────────
id          BIGINT PK                     id          BIGINT PK
user_id     BIGINT FK users, UNIQUE       inviter_id  BIGINT FK users
code        VARCHAR(16) UNIQUE            invitee_id  BIGINT FK users, UNIQUE
created_at  TIMESTAMP                     created_at  TIMESTAMP
                                          index (inviter_id)
```

- **IF-1** The code lives in its own table, not on `users`. Then `User`, `UserView` and every mapper that reads a user cannot carry it by accident. Neither table has a `public_id`: no row is ever addressed from outside.
- **IF-2** `referrals.invitee_id` is unique, so a person counts once, for one inviter, forever. The row holds no code, so a later code change keeps the count.

---

## 3. The code and the link (IF-3, IF-4)

```
 inviter                    friend's browser                          API
 ───────                    ────────────────                          ───
 GET /me/invite ─────────────────────────────────────────────▶ code made on first call
 ◀──────────────── {code: "K7QM2XWR4D", joined: 0}
 share "…https://doorwayliving.in/?ref=K7QM2XWR4D"
                    ─▶ opens any page with ?ref=K7QM2XWR4D
                       keep code in localStorage "rx-ref" (30 days)
                       remove ?ref from the address bar
                       … browses, taps Sign in
                       GIS popup ─▶ ID token (page stays loaded)
                       POST /auth/google {idToken, ref} ────────────▶ user created in this call?
                                                                        code valid, not their own?
                                                                        ─▶ insert referral
                       ◀──── same session reply either way ─────────── (never says what happened)
                       delete "rx-ref"
```

- **IF-3 Code.** 10 characters from the Crockford base32 alphabet (`0-9A-Z` without `I L O U`), from `SecureRandom`: about 2^50 codes, not derived from any user field. It is made on the user's first `GET /me/invite` and stays the same. On a unique-key clash the API draws again. The API accepts a code only if it matches `^[0-9A-HJKMNP-TV-Z]{10}$` after upper-casing, else it ignores it.
- **IF-4 Link.** The link always points at the site root: `<web origin>/?ref=<code>` (on LIVE `https://doorwayliving.in/?ref=…`). Never a home, review or profile page: a link to the home a user just reviewed would tie the code's owner to that home and so to the review. The web builds the link from `window.location.origin`; the API sends only the code.

**How the code survives sign-in.** Google sign-in is a GIS popup (`ux_mode: "popup"`), so the page is never unloaded. The web stores the code at the first page load (`localStorage`, key `rx-ref`, with the time it was kept; 30 days), so it also survives the visitor closing the tab and coming back, and navigation to `/login`. A newer `?ref` replaces an older one. After any successful sign-in the web deletes `rx-ref`, whatever the API did with it. If `localStorage` is blocked, the code is kept in memory for the open tab only.

---

## 4. API shape (IF-5, IF-6)

| Call | Change |
|---|---|
| `POST /auth/google` | body `{idToken, ref?}`; `ref` is optional, at most 32 characters |
| `POST /auth/dev-login/new` (dev only) | body `{key?, ref?}`, so the flow can be tried without Google |
| `GET /me/invite` (new, signed in) | `200 {code, joined}`: the caller's own code (made on first call) and how many referrals name them as inviter. Nothing about who joined |

- **IF-5 Attribution, once, on sign-up only.** In the sign-in transaction, only when that call **created** the `users` row: find the code's owner; skip if there is none, if the owner is not `ACTIVE`, or if the owner is the new user; else insert `referrals(inviter, invitee)`. A `ref` on the sign-in of an existing user is ignored. The unique `invitee_id` stops a second row. The sign-in reply is the same in every case, so `/auth/google` is no oracle for valid codes. Admin console sign-ins (`/auth/admin/google`) ignore `ref`.
- **IF-6 No public route takes a code.** There is no `GET /invite/{code}` or "invited by X" page: the code resolves to a person only inside the sign-in transaction.

---

## 5. Privacy rules (IF-7, IF-8)

- **IF-7** The code and the count go only to their owner, through `GET /me/invite`, and to the owner's own share link. They are never in `GET /me` (`UserView`), reviews, review authors, profiles (`/users/{id}`), property pages, search, tenancy views, emails, admin lists or any other response. The inviter sees a number, never who joined. The invitee sees nothing about who invited them. A landlord who gets the link sees only a random code that no route turns into a person. Logs never print a code.
  - The server proves it: a test signs up a friend through a code, then calls the public and other-user routes (property detail and reviews, `/users/{id}`, `/users/{id}/reviews`, search, `GET /me` of the friend and of a third user, the friend's tenancy and review views) and asserts that no body holds the code, the word `ref` as a field, or a referral count.
  - One known edge: if an inviter shares the link with one person only, the count going from 0 to 1 tells them that person joined. That is what "N friends joined" means, and it says nothing about reviews.
- **IF-8** Account deletion (D-58) deletes the user's `invite_codes` row and every `referrals` row where they are the inviter or the invitee. An inviter's count drops when a friend deletes their account.

---

## 6. Web (IF-9)

- **Entry points**: "Invite friends" (`users` icon) in the account menu and in the phone menu sheet; an "Invite friends" section on Settings with the count; a "Help the next tenant" card at the end of `Submitted` on the review page. Signed-in users only, because only they have a code.
- **One action**: `invite()` reads `GET /me/invite`, builds the message and calls `navigator.share({text})` (link inside the text, so WhatsApp shows it once). If `navigator.share` is missing, or rejects with anything but `AbortError`, the web opens a small **Invite friends** sheet: the message preview, **WhatsApp** (`https://wa.me/?text=<encoded message>`, new tab) and **Copy link** (`navigator.clipboard.writeText`, toast "Link copied"). The sheet also has the count.
- **IF-9 Message** (honest claims only, two short sentences plus the link):

  > Renting in Bangalore or Pune? Check the landlord on Doorway Living before you pay a deposit. Tenants review anonymously. https://doorwayliving.in/?ref=K7QM2XWR4D

  "Tenants review anonymously" holds under D-168 / D-169: no reviewer is shown by name, and the reviewed party cannot work out which tenant wrote a review.
- **Look**: icon-led and small, as the home-page redesign: one outline button with an icon, the card a single bordered row like the home link above it. No change to `features/mode/*` or the home-page sections.

---

## 7. Not in v1

Rewards; a list of who joined; invite codes for owners as a separate kind; email invites from the platform; QR codes; changing or resetting a code.

---

## 8. Slices

| # | PR | What |
|---|---|---|
| 1 | this note | `rentrix-docs/design/invite-friends.md` |
| 2 | share UI | `share` icon; `features/invite/` (`invite()`, the fallback sheet, the message); account menu + phone sheet item; "Help the next tenant" card; Settings section. It shares the plain site link (no `?ref` yet) and shows no count, so it needs no API change |
| 3 | ref tracking | migration (`invite_codes`, `referrals`); `GET /me/invite`; `ref` on `POST /auth/google` and `/auth/dev-login/new`; attribution (IF-5); deletion (IF-8); the privacy test (IF-7); the web's `rx-ref` keep-and-send; the link gains `?ref`; "N friends joined" on Settings and in the sheet; SYSTEM_DESIGN D-numbers |

Each UI PR says how to run it locally and which pages to screenshot.
