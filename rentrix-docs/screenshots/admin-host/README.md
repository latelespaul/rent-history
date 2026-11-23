# Admin console on its own host (W9 PR 1 and PR 2)

SYSTEM_DESIGN §13.3, D-154, D-155. Taken 2026-11-23 at 390 × 844 (2×) in headless Chromium against
local dev servers: the API in the `dev` profile with the demo seed and `SMS_BACKEND=admin-relay`, the
web build served by `vite preview` on `localhost` and `admin.localhost`.

| File | What it shows |
|---|---|
| `admin-sign-in-390.png` | `http://admin.localhost/login`: the admin sign-in. Google is not configured locally, so the dev login offers only the seed's Demo Admin. |
| `admin-otp-relay-390.png` | `/sms-relay` after signing in: one code held for Demo Tenant BLR-1 (masked phone, code, expiry, Copy), reloaded every 10 s; the page bar sits at the bottom, in thumb reach. |
| `main-site-tenant-no-admin-390.png` | The main site for a signed-in tenant: no admin link, badge or route anywhere. |

The approvals queue and reports page (W9 PR 2, D-134, D-136), same setup; a demo tenant first adds a LinkedIn link,
so the queue holds a sole pending ID check:

| File | What it shows |
|---|---|
| `admin-approvals-390.png` | `/approvals`, the console's home page: newest first, tabs with counts, cards with "Not verified yet", key facts and the Approve / Reject bar at the bottom of each card. |
| `admin-approvals-reject-390.png` | The second tap of a reject on the sole LinkedIn check: optional note, then one reason chip sends it. |
| `admin-approvals-id-checks-390.png` | The "ID checks" tab: PAN, masked Aadhaar and LinkedIn checks decided on their own, with no application. |
| `admin-reports-390.png` | `/reports`: open reports (none in the demo seed), answered with Dismiss or, for a review, Hide. |
