# New-user journey screenshots (390 px)

The captain's journey (2026-11-22 21:38 / 22:22; D-139 – D-143), taken at 390 px wide with a
headless Chromium (throwaway profile) against the local dev stack: API in the `dev` profile with
the demo seed and dev login, Vite on :5173. Phone codes came from the API log (`SMS_BACKEND=log`,
dev only). "Sign in as a new user" stands in for Google sign-up.

| File | Step |
|---|---|
| `01-signup.png` | `/login`: dev login, "Sign in as a new user (first sign-in)" (Google in production) |
| `02-choose-role.png` | `/welcome`: "I rent a home", home city, terms |
| `03-account-created.png` | `/welcome/created`: account created, "Verify your phone number" (then Add your home), "Add your home" |
| `04-verify-phone.png` | `/verify/phone?next=/homes/new`: code sent and typed |
| `05-add-home-address.png` | `/homes/new` step 1: Bangalore > Koramangala > Demo Greens > tower > wing, flat, PIN |
| `06-add-home-role.png` | step 2: "I rent it, or rented it" |
| `07-add-home-tenancy.png` | step 3: moved in, "I still live here", rent, deposit |
| `08-add-home-owner.png` | step 4: the owner's name and email, private |
| `09-home-added.png` | "Your home is added", "Added by a tenant · not verified yet", "Write a review of this home" |
| `10-write-review.png` | `/tenancies/:id/review`: 4 stars and the text |
| `11-review-submitted.png` | "You submitted this review", "Not verified yet" |
| `12-property-page.png` | `/p/:id`: tenant-added badge, owner "Not on Doorway Living yet", rent "not verified yet", the review with its badge |
| `13-landlord-role.png` | a second new user: "I rent out a property", Pune |
| `14-landlord-add-property.png` | after the phone step: `/homes/new?own=1` step 1, Pune > Baner > Demo Meadows > tower, flat, PIN |
| `15-landlord-i-own-it.png` | step 2: "I own it" picked |
| `16-landlord-property-page.png` | `/p/:id`: published at once, "Edit listing", "Invite a tenant" |
