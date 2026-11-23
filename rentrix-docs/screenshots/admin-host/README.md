# Admin console on its own host (W9 PR 1)

SYSTEM_DESIGN §13.3, D-144, D-145. Taken 2026-11-23 at 390 × 844 (2×) in headless Chromium against
local dev servers: the API in the `dev` profile with the demo seed and `SMS_BACKEND=admin-relay`, the
web build served by `vite preview` on `localhost` and `admin.localhost`.

| File | What it shows |
|---|---|
| `admin-sign-in-390.png` | `http://admin.localhost/login`: the admin sign-in. Google is not configured locally, so the dev login offers only the seed's Demo Admin. |
| `admin-otp-relay-390.png` | `/sms-relay` after signing in: one code held for Demo Tenant BLR-1 (masked phone, code, expiry, Copy), reloaded every 10 s; the page bar sits at the bottom, in thumb reach. |
| `main-site-tenant-no-admin-390.png` | The main site for a signed-in tenant: no admin link, badge or route anywhere. |

The approvals queue (W9 PR 2) adds its own screenshots here.
