# Review form (design note)

Captain 2026-11-23 21:29: the review is a structured form, not a bare text box. This note extends the review model of SYSTEM_DESIGN §5.6 and §7 (D-37 to D-48); the rules there stay unless a line below says otherwise. Decisions D-157 and D-158.

**Unchanged.** The 60-day lock before the first review (`ReviewWindow.OPENS_AFTER_DAYS`, D-39), the double-blind reveal of the landlord review (D-46), the property review publishing at once (D-47), one review per direction per tenancy (D-38), the volume guard (D-45), the posting gate (D-132), "Not verified yet" until the green tick (D-134), and the author's own edits (D-156).

## Data model (D-157)

One form writes the existing two review rows of the tenant: `TENANT_TO_PROPERTY` (the home) and `TENANT_TO_LANDLORD` (the landlord). The landlord part is left out when the landlord is not on the platform (D-139) or was already reviewed.

- New table `review_criterion_ratings (id, review_id, criterion, rating 1–5, created_at, updated_at)`, unique `(review_id, criterion)`. One row per parameter the user rated; a skipped parameter has no row.
- Parameters (`ReviewCriterion`), each tied to one direction:
  - home: `WATER_SUPPLY`, `POWER_BACKUP`, `MAINTENANCE`, `CLEANLINESS`, `SECURITY`, `PARKING`, `NOISE`, `NEIGHBOURHOOD`, `VALUE_FOR_RENT`;
  - landlord: `RESPONSIVENESS`, `DEPOSIT_RETURN`, `TRANSPARENCY`, `PRIVACY`, `BEHAVIOUR`.
- New columns on `reviews`: `would_recommend` (BOOLEAN, null = not answered), `pros`, `cons` (VARCHAR 1000, null = none). `review_revisions` gains the same three plus `criteria` (text such as `WATER_SUPPLY=4,NOISE=2`), so an edit keeps the previous version (§5.7).
- `body` is the part's optional comment. The column stays NOT NULL, so no comment is stored as an empty string. This avoids a table rebuild on SQLite.
- Migration `V16__review_form.sql` (V15 is the edit-own PR's) uses only `ALTER TABLE … ADD COLUMN` and `CREATE TABLE` with table-level constraints, the portable subset of D-10. It runs unchanged on SQLite, MySQL and PostgreSQL. Existing reviews stay valid: they keep `rating_overall` and their optional `rating_a/b/c` (D-29), have no parameter rows, and show as before.

## Validation

- `ratingOverall` is required, 1–5. Every parameter rating is optional, 1–5. An unknown parameter, or one of the other direction, is an error.
- A review written with `/review-form` is a form review, marked by `reviews.form_review` (V17): its comment, pros and cons are optional on create and on every edit, at most 2000 / 1000 / 1000 characters, and each passes the D-43 personal-data and profanity rules. Any other review keeps D-43's 30-character minimum.
- Every broken rule is a field error (`422 validation_failed`) on its own path, such as `home.criteria.PARKING`, `landlord.ratingOverall` or `home.pros`. All of them come back in one answer. The web shows each error under its field and never a generic banner.

## API

- `POST /tenancies/{id}/review-form` `{home: Part, landlord?: Part}`, with `Part = {ratingOverall, criteria: {PARAM: 1–5}, wouldRecommend?, body?, pros?, cons?}`. It writes both reviews in one transaction, so both are saved or neither is. It runs every create rule per direction (D-37 to D-45), then publishes as today: the home at once; the landlord review at once only when the landlord's review already waits (D-46). The answer is `201 {home: ReviewView, landlord?: ReviewView}`.
- `POST /tenancies/{id}/reviews` and `PATCH /reviews/{id}` take no form fields. `PATCH` edits the overall rating and the text; a form review keeps its parameter ratings, `wouldRecommend`, pros and cons.
- `ReviewView` gains `formReview` (true for a form review), `criteria`, `wouldRecommend`, `pros` and `cons`. They are hidden with the text while a review awaits reveal or its text is hidden (D-46, D-50).
- `RatingDetail` (`GET /properties/{id}/rating`, `landlordRating` on `GET /users/{id}`) gains `criteria: [{criterion, ratingCount, average?}]` and `recommend: {answered, yes}`.

## Overall rating

Each part has its own overall rating, because the home and the landlord are rated apart (D-18). The form fills it with the average of the part's rated parameters, rounded half up to whole stars, and shows "Worked out from your ratings". When the user taps a star, their own value wins ("Your own rating"), and "Use the worked-out rating" goes back. The server stores only the number. The headline averages (§9) still use `rating_overall`, so old and new reviews count the same way. "Would you recommend" is asked for each part (the home, the landlord). Pros and cons are about the stay and go on the home review, which publishes at once; the landlord's text stays in the landlord comment, behind the reveal (D-46).

## Per-parameter averages (D-158)

- Computed at read time for one subject, from the same counted reviews as its headline: `PUBLISHED`, `counts_in_aggregate`, and green-tick reviews only in the `verified` headline mode (D-135). The query is a grouped sum over one subject's reviews, bounded like the tower figures of D-111. No stored roll-up needs to be kept in step (D-33).
- D-52 applies to each parameter: its average shows from 3 ratings of that parameter. Below that, the row shows "New · n ratings". "Would recommend" shows as a percentage from 3 answers.
- The home page shows the home's 9 parameters under its rating. A new landlord page `/u/:id`, linked from the home page's owner line for signed-in users (D-55), shows the landlord's rating, their 5 parameters and the reviews they received. Unverified reviews keep their "Not verified yet" label.

## Screens (mobile first, 390 px)

```
/tenancies/:id/review
 1 Your stay   period auto-filled from the tenancy (read only)
 2 The home    9 parameters, 1–5 stars, each skippable + comment
 3 Landlord    5 parameters + comment        (only when the landlord can be reviewed)
 4 Overall     overall per part (worked out / own), would you recommend (yes/no), pros, cons
 5 Check       every answer, "Edit" per section, Submit
 6 Done        success screen, links to the home page
```

- One section per screen, with "Step n of N" and a progress bar. "Back" and "Next" never lose input.
- "Save draft" keeps the form on this device (local storage, per tenancy). It is also saved after each step, and it is offered again when the user comes back. Drafts never reach the server, so nothing about an unsent review leaks, and no new review state is needed. A submit clears the draft.
- Server field errors open the step that holds the field and show the message under it.
