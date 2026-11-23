# Owners edit their own entries (D-156), 390 px

From `rentrix-web/e2e/edit-own-entries.spec.ts` (mobile project), against a local dev stack with the demo seed.

1. `01-edit-unverified.png`: Edit from the My tenancies card; not verified yet, so it changes freely.
2. `02-unverified-saved.png`: saved at once, still "Not verified yet".
3. `03-verified.png`: after the team's tick, "Verified by Doorway Living".
4. `04-edit-verified.png`: the edit page warns that saving removes the tick.
5. `05-warning.png`: the confirm dialog before saving.
6. `06-saved-not-verified.png`: saved; back to "Not verified yet" and in the admin queue (flagged `EDITED`, checked through the API).
