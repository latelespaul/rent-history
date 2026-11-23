# End-to-end tests (Playwright)

Playwright drives the real web app against a seeded API in the `dev` profile with dev login on.
CI runs them on every PR (`.github/workflows/e2e.yml`) against the docker compose e2e stack and
uploads the HTML report, screenshots and traces as the `playwright-report` artifact.

## Run locally

```sh
export JWT_SECRET=e2e-only-jwt-secret-not-for-production-0123456789
docker compose -p rentrix-e2e -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --wait
cd rentrix-web
npx playwright install chromium   # once
npm run e2e                       # both projects; add --project=desktop or a spec path to narrow
npm run e2e:report                # the HTML report with screenshots and traces
cd .. && docker compose -p rentrix-e2e -f docker-compose.yml -f docker-compose.e2e.yml down -v
```

Without Docker, start the API (`SPRING_PROFILES_ACTIVE=dev SEED_DEMO=true` on an empty database)
and `npm run dev`, then `E2E_BASE_URL=http://localhost:5173 npm run e2e`. `E2E_SLOW=4` stretches
every wait on a busy machine.

Against a deployed stack (DEV), also set:

- `ADMIN_BASE_URL`: the admin host that serves `/api/v1` (e.g. `https://admin.<dev host>`). The
  phone-verify helper reads relayed SMS codes there. Without it the helper sends
  `X-Forwarded-Host: admin.<api host>`, and only to a local API host (`localhost`, `*.localhost`,
  `127.0.0.1`, `::1` or a Docker service name); for any other host it fails. This differs from
  `E2E_ADMIN_BASE_URL`, the admin web origin that `admin-host.spec.ts` opens.
- `E2E_NEW_USERS_LOG`: a file path. Each run appends the email of every fresh account it creates
  (`POST /auth/dev-login/new`), one per line, so the test users can be cleaned up afterwards.

## Rules

- The specs change data (tenancies, new accounts), so they need a freshly seeded database and run
  one at a time with no retries. Recreate the stack (`down -v`, then `up`) before a second run.
- Two projects: `desktop` and `mobile` (390 px wide). Specs that change seed data use a different
  seed landlord, home and tenant per project.
- Sign in with `devLogin(page, "Demo Tenant BLR-1")` (a seed user) or `freshLogin(page)` (a new
  account that still has to pick a role; `POST /auth/dev-login/new`, dev and test only).
- Verify a phone only with `verifyPhone(page, testPhone())`: test numbers (`+91 99999 0xxxx`) are
  never sent by SMS or Telegram (root README "Test phone numbers"), and `verifyPhone` refuses any
  other number.
- A bug found by a click-test becomes a spec. While another PR is fixing it, mark the spec
  `test.fixme` with the bug reference in its title, and flip it to `test` when the fix merges.
