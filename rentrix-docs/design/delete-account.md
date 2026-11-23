# Delete my account: design note

Status: **build**. Date: 2026-11-24. Decisions: SYSTEM_DESIGN.md D-58 (revised), D-157 to D-160.
Fits the India DPDP Act 2023: section 12 (right to erasure) and section 8(7) (erase when the purpose ends).

## Flow

```
Settings ─▶ "Delete my account" ─▶ /settings/delete-account
   warning: what is removed, what stays as "Deleted user"
   type DELETE ─▶ DELETE /me {confirm: "DELETE"}
        │ signed in more than 10 min ago? ─▶ 403 reauth_required
        │      ─▶ "Sign in again" (Google, or dev login) ─▶ back to the page ─▶ type DELETE again
        ▼
   one transaction: erase + anonymise + revoke every session + audit row
        ▼
   204, refresh cookie cleared ─▶ web signs out ─▶ landing page, "Your account is deleted"
```

## Re-confirm (D-157)

- The user types `DELETE` (exact, upper case). Anything else is `422 confirm_mismatch`.
- A fresh sign-in check: the access token carries `auth_time`, the time of the Google (or dev) sign-in that
  started the session; refreshes keep it. `DELETE /me` needs `auth_time` within the last 10 minutes,
  else `403 reauth_required`. The web then sends the user through sign-in again and back to the page.
  This works for Google and dev login alike, and needs no phone.

## Grace period (D-158)

None. Deletion is immediate and cannot be undone. A grace period needs a pending state that every read,
write, job and sign-in must respect; that is not simple here. The warning says it is final.

## What happens to each record (D-58, D-159)

**Erased** (cleared or deleted at once):

| Data | How |
|---|---|
| `users`: email, `google_sub`, `google_name`, `avatar_url` (a Google URL, never copied to our storage), phone, home city, last sign-in | set to null; `display_name` becomes "Deleted user"; roles removed; `status = DELETED`, `deleted_at` set |
| Identity checks (`identity_verifications`): phone, Aadhaar last 4, PAN last 4 and HMAC, LinkedIn URL, Truecaller | rows deleted |
| Verification applications and their events | rows deleted |
| Sessions: every `refresh_tokens` row | deleted, so every device is signed out; live access tokens are refused at once (D-160) |
| Share links, enquiries the user sent, stored idempotent replies (they can echo the profile) | rows deleted |
| Owner contact (name, email, phone) the user typed for homes they added | cleared |
| Invites the user sent that are still waiting (`PENDING_CONFIRMATION`) | tenancy `CANCELLED`, invite email cleared, invite tokens deleted |
| Pending tenancy requests where the user is the other party | `CANCELLED` (nobody can confirm them any more) |
| Documents and photos | v1 stores none yet: ID proof and agreement uploads are not merged. The erasure publishes an `AccountErased` event inside the transaction; the upload feature (PR #62) must listen to it and delete the user's files and rows |

**Kept, anonymised** (other people's records stay intact):

| Data | Shown as | Why kept |
|---|---|---|
| Reviews and responses the user wrote | author "Deleted user", no profile link, no badges; they keep counting | the other party's tenancy and the home's rating depend on them; the person is no longer named. The warning tells users to withdraw a review first if they want its text gone |
| Reviews others wrote about the user | subject "Deleted user" | they are the other author's record |
| Tenancies and rent records (both sides) | party "Deleted user" | rent history describes the home; the other party's tenancy stays |
| Homes the user owns (`landlord_id`) | owner detached: moved to the unclaimed account, "Not on Doorway Living yet" | the home, its tenancies, rent history and reviews stay |
| Homes the user added as a tenant (`added_by`) | still "Added by a tenant" | `added_by` points at the anonymised row |
| Edit history, reports filed, areas and societies they proposed | editor or reporter "Deleted user" | admin audit trail |

**Kept for the law and security, no personal data:**

| Data | Why | Removed |
|---|---|---|
| One audit row `ACCOUNT_DELETED`: the user's id (`public_id`) and the time, no name, email, phone or reason | proof the request was carried out (DPDP s.8(5), s.12) | never |
| The user's phone code rows of the last 24 h: phone number blanked, only the keyed hash and send time stay, codes used up | the per-number daily send limit, so delete-and-sign-up cannot flood a number with codes (security safeguard, s.8(5)) | the nightly cleanup, within 48 h |
| Admin audit rows about the user (D-87) | admin accountability | never; they name the anonymised user |

Nothing in the logs names the user: the deletion logs only the user id.

## Sessions (D-160)

- All refresh tokens are deleted, so `POST /auth/refresh` on any device answers `401`.
- The JWT filter refuses an access token whose user is `DELETED` (one indexed read per request).
  The old token gets `401 token_invalid` at once, not after its 15 minutes.

## Signing up again starts fresh

- `google_sub` and email are cleared, so the same Google account (same email) creates a new, empty user.
- The phone is cleared and the identity rows are gone, so the same number verifies again with no
  "phone already on another account" flag. The kept code rows hold no number and only count sends.

## Admin console

- `GET /admin/audit-log?action=` lists audit rows, newest first. The console's new **Audit log** page
  shows them, filtered to account deletions by default: "Account deleted", user id, time.

## Tests

- API: erase and keep rules per table, re-confirm (`confirm_mismatch`, `reauth_required`), old access and
  refresh tokens refused, re-sign-up with the same Google sub and phone gets a new user, audit row.
- e2e at 390 px with screenshots (`rentrix-docs/screenshots/delete-account/`): delete, old session
  refused, public pages show "Deleted user", sign up again with the same email and phone is fresh.
