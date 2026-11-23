# Owners edit their own entries (D-156), 390 px

From `rentrix-web/e2e/edit-own-entries.spec.ts` (mobile project), against a local dev stack with the demo seed.

1. `01-edit-unverified.png`: Edit from the My tenancies card; not verified yet, so it changes freely.
2. `02-unverified-saved.png`: saved at once, still "Not verified yet".
3. `03-verified.png`: after the team's tick, "Verified by Doorway Living".
4. `04-edit-verified.png`: the edit page warns that saving removes the tick.
5. `05-warning.png`: the confirm dialog before saving.
6. `06-saved-not-verified.png`: saved; back to "Not verified yet" and in the admin queue (flagged `EDITED`, checked through the API).
7. `07-review-verified.png`: a published home review with the team's tick.
8. `08-review-edit.png`: "Edit my review" on the published review (D-156: editable at any time).
9. `09-review-warning.png`: the confirm dialog: saving removes the tick.
10. `10-review-edited.png`: saved; "Not verified yet" and "edited <date>".
11. `11-delete-blocked.png`: deleting a home with another tenant's record is refused, with the plain reason and "Ask our team to change this".
12. `12-ask-team.png`: the note to our team.
13. `13-request-sent.png`: request sent; it is in the admin report queue as "Change request" (checked through the API).
