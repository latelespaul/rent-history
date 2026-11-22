# Landing v2: design v1 (mockup)

The signed-out landing at `/`, redesigned as a mobile-first scrolling page. Static HTML on
`../tokens.css` and `../icons/icons.js`; the build reuses the same route and landing components.

| File | What |
|---|---|
| `landing-v2.html` | The mockup (open it in a browser; fonts load from `rentrix-web/node_modules` when installed) |
| `landing-v2-390.png` | Phone, 390 px wide, full page (2x) |
| `landing-v2-1440.png` | Desktop, 1440 px wide, full page |

Sections, top to bottom: sticky header with section links and "Sign in"; hero (headline, subline,
Google call to action, "See how it works" anchor, a sample app screen marked "Sample screen");
the problem (tenants, landlords, no trusted record); how it works in 3 steps; two audience cards
(for tenants, for landlords); trust and verification strip; a carousel of home and review cards;
coverage (Bangalore and Pune with their localities); FAQ; final call to action; footer.

Trust copy follows the open-by-default model (design v4): anyone with a verified phone can post,
posts show as "Not verified yet", the green tick means "Verified by Doorway Living". *Changed in
the build (2026-11-22)*: the landing now says "Verify with any one: phone, Aadhaar or LinkedIn" and
"Green tick = approved by the Doorway Living team after an ID check"; this mockup still shows the
older "phone required" copy. Current build: `../../screenshots/verify-anyone/README.md`.

No invented numbers (D-03): in the build the carousel reads `GET /properties` (seed rows carry
"Demo data") and the seeded review text; cities come from `GET /cities`. The ratings and counts
drawn in this mockup are placeholders for that data, and so is the tick on its review cards: in
the build every example review reads "Not verified yet" with no green tick, because the seeded
reviews are not verified yet. The hero phone is a sample screen.

Re-render: `node shot.mjs file://$PWD/landing-v2.html landing-v2` with Playwright 1.49 (headless
Chromium; viewports 390x844 at 2x and 1440x900 at 1x, full page, header unpinned).
