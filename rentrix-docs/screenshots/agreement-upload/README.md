# Add home → rental agreement → review (390 px)

The captain's ask of 2026-11-23 14:53: after "Save my home" the flow goes on with two optional
steps, "Upload your rental agreement" and "Rate and review this home", each skippable and kept as a
prompt until done. Taken by the Playwright spec `rentrix-web/e2e/new-user.spec.ts` (the full-signup
chain) in its 390 × 844 `mobile` project, headless Chromium, against local dev servers: the API in
the `dev` profile with the demo seed, dev login and `SMS_BACKEND=admin-relay` on a fresh SQLite
file, and Vite on :5173. "Sign in as a new user" stands in for Google sign-up; the phone code is read
from the admin relay, and the team's check is the admin API (the approvals queue UI is W9).

Re-take them with `E2E_SHOTS=<this folder> E2E_BASE_URL=http://localhost:5173 npx playwright test
e2e/new-user.spec.ts --project=mobile`.

| File | Step |
|---|---|
| `01-signup-role.png` | `/welcome`: a brand-new account picks "I rent a home" |
| `02-account-created.png` | "Your account is created." |
| `03-verify-phone.png` | `/verify/phone`: the posting method |
| `04-add-home-address.png` | `/homes/new` step 1: Bangalore > Koramangala > a new society, flat, PIN |
| `05-add-home-role.png` | step 2: "I rent it, or rented it" |
| `06-add-home-tenancy.png` | step 3: moved in 90 days ago, rent |
| `07-add-home-owner.png` | step 4: the owner's phone, private |
| `08-home-added-next-steps.png` | "Home added. 2 optional steps left": Upload your rental agreement, Rate and review this home, Skip for now |
| `09-agreement-file-consent.png` | `/tenancies/:id/agreement` step 1: cover Aadhaar first, the file, the explicit consent |
| `10-agreement-details.png` | step 2: the fields typed from the agreement, a fake Aadhaar number in the tenant's name |
| `11-agreement-check-masked.png` | step 3: what was stored, the Aadhaar number masked to `XXXX XXXX 0123` |
| `12-agreement-uploaded.png` | "Agreement uploaded · awaiting team check", the review next |
| `13-write-review.png` | `/tenancies/:id/review`: 4 stars and the text |
| `14-review-submitted.png` | "You submitted this review", "Not verified yet" |
| `15-home-page-unverified.png` | `/p/:id`: "Your tenancy here" (private): not verified yet, agreement awaiting team check, "You reviewed this home"; the review with its "Not verified yet" badge |
| `16-tenancy-verified.png` | after the team verified it from the agreement: "Verified by Doorway Living" on the tenancy |
| `17-my-tenancies-verified.png` | My tenancies with the green tick |
| `18-skipped-my-tenancies-prompts.png` | a second tenant who tapped "Skip for now": both prompts on My tenancies, "2 optional steps left" |
| `19-skipped-home-page-prompts.png` | the same prompts on the home's page |
