# Rentrix

Two-sided rental trust platform: tenants rate landlords and properties, landlords rate tenants, all tied to confirmed tenancies.

- `rentrix-server/`: API (Spring Boot, Java 21). Notes: [`rentrix-server/README.md`](rentrix-server/README.md).
- `rentrix-web/`: web app (React + Vite).
- `rentrix-docs/`: design ([`architecture/SYSTEM_DESIGN.md`](rentrix-docs/architecture/SYSTEM_DESIGN.md)) and the PR plan ([`PLAN.md`](rentrix-docs/PLAN.md)).

## Run the API locally

Needs only Java 21 (SQLite file, no Google account, no Docker):

```sh
cd rentrix-server
SPRING_PROFILES_ACTIVE=dev SEED_DEMO=true ./mvnw spring-boot:run
```

`SEED_DEMO=true` adds the demo users and homes on an empty database (without it the dev-login picker is empty). The API listens on `http://localhost:8080/api/v1`. `SPRING_PROFILES_ACTIVE` is required (`dev`, `test` or `prod`); every setting is in SYSTEM_DESIGN §17.

## Signing in

"Sign in with Google" is the only production login, for tenants and landlords alike. The web gets a Google ID token in the browser and posts it to `POST /api/v1/auth/google`; the API verifies it and issues its own short-lived access token plus a refresh cookie. There are no passwords.

### Dev login (no Google needed)

In the `dev` profile `DEV_LOGIN_ENABLED` defaults to `true`, and the API offers a picker of the demo seed users (`is_demo`, emails `@example.test`):

```sh
curl -s http://localhost:8080/api/v1/auth/dev-users
curl -s -c cookies.txt -H 'Content-Type: application/json' \
  -d '{"userId":"<id from dev-users>"}' http://localhost:8080/api/v1/auth/dev-login
```

The response holds `accessToken` (send it as `Authorization: Bearer …`) and sets the `rentrix_refresh` cookie for `POST /api/v1/auth/refresh`. Dev login does not exist in any other profile, and the API refuses to start with `DEV_LOGIN_ENABLED=true` outside `dev` and `test`.

### Create a Google OAuth client

Google sign-in needs only an OAuth **client ID** (no client secret). Never commit it.

1. Open [Google Cloud Console](https://console.cloud.google.com/) and select or create a project (for example "rentrix-dev").
2. APIs & Services → OAuth consent screen: user type **External**, app name "Rentrix (dev)", a support email, scopes `openid`, `email` and `profile` only. While the app is in "Testing", add your own Google account as a test user.
3. APIs & Services → Credentials → **Create credentials → OAuth client ID** → application type **Web application**.
4. Authorized JavaScript origins: `http://localhost:5173` and `http://localhost` (Google Identity Services needs both for local dev). For production add the web origins `https://doorwayliving.in` and `https://www.doorwayliving.in`. No redirect URIs are needed.
5. Copy the client ID (`…apps.googleusercontent.com`). Do **not** download or commit the JSON file.
6. Give it to the API only, from your shell or an untracked `.env` file (`.env` is git-ignored):

   ```sh
   export GOOGLE_CLIENT_ID=…apps.googleusercontent.com
   ```

   The web reads it from `GET /api/v1/auth/config`, so it needs no Google setting of its own.
7. Start the API and the web; "Sign in with Google" appears on the login page.

Without `GOOGLE_CLIENT_ID` the Google button is hidden. In `prod` the API still starts, logs a warning and answers `POST /auth/google` with `503 google_sign_in_disabled`.

### Production

Web `https://doorwayliving.in`, API `https://api.doorwayliving.in`. They are same-site, so the `SameSite=Strict` refresh cookie works across them. Set on the API:

```sh
SPRING_PROFILES_ACTIVE=prod
GOOGLE_CLIENT_ID=…apps.googleusercontent.com
CORS_ALLOWED_ORIGINS=https://doorwayliving.in,https://www.doorwayliving.in
```

Behind a reverse proxy (Caddy on the VM, Render) the API honours `X-Forwarded-Proto` / `-Host` / `-Port` only from `TRUSTED_PROXIES` (default: loopback and private ranges), so a web served from the API's own origin needs no CORS entry. Set `TRUSTED_PROXIES` to the proxy's IP when it is neither. See SYSTEM_DESIGN §17.

### Admins

`ADMIN` is never self-service. Set `ADMIN_EMAILS` to a comma list of verified Google emails; those users get `ADMIN` on their next sign-in or refresh, and lose it when their email is removed from the list.

## Locations: areas and societies

Every home sits in a hierarchy City → Area → Society → Tower/Block → Wing → Unit; a standalone house or building has an area and no society (SYSTEM_DESIGN §5.19). Areas carry their PIN codes, and one PIN code can cover several areas. Start the API with demo data to browse it:

```sh
cd rentrix-server
SPRING_PROFILES_ACTIVE=dev SEED_DEMO=true ./mvnw spring-boot:run
```

The demo seed (empty database only) adds an area for every demo locality and one gated society per city with 2 towers of 2 wings: **Demo Greens** (Koramangala, Bangalore) and **Demo Meadows** (Baner, Pune). Demo Residency A is in Demo Greens Tower 1 · Wing A, Demo Towers E in Demo Meadows Tower 2 · Wing B; the other demo homes are standalone. All of it is fake (`is_demo`).

```sh
API=http://localhost:8080/api/v1
curl -s "$API/areas?pincode=560034"                  # Koramangala
curl -s "$API/areas?city=BLR&q=kor"                  # name or alias prefix
curl -s "$API/societies?city=PNQ"                    # Demo Meadows card
curl -s "$API/societies/<societyId>"                 # towers → wings with published-home counts
curl -s "$API/properties?society=<societyId>&tower=<towerId>"
curl -s "$API/properties?area=<areaId>&standalone=true"
```

Lists show only `ACTIVE` rows, plus the signed-in landlord's own `PENDING` proposals. Ratings per area and society and the rating sorts come with PLAN B16.

## Identity verification

A tenant or landlord profile is **ID verified** when at least one of Aadhaar, PAN or phone (SMS code) is verified. LinkedIn is optional: a verified LinkedIn profile adds a second "LinkedIn verified" badge but never counts as ID. `GET /api/v1/me` carries `badges: {idVerified, linkedinVerified}`; `GET /api/v1/me/identity` lists each method with masked values only (PAN `XXXXXX234F`, Aadhaar `XXXX XXXX 1234`, phone `+91 XXXXXX3210`). Full PAN, Aadhaar numbers and OTP codes are never stored; PAN and masked Aadhaar uploads come with PLAN B12.

### Try phone OTP locally

In `dev` and `test`, `SMS_BACKEND` defaults to `log`: no SMS is sent, and the code is written to the API log instead (the only place a code is ever logged). With an access token from dev login or Google sign-in:

```sh
TOKEN=…   # accessToken
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"phone":"+919876543210"}' http://localhost:8080/api/v1/me/identity/phone/start
# → {"challengeId":"…","expiresAt":"…"}; the API log shows
#   SMS to +91 XXXXXX3210 (SMS_BACKEND=log, not sent): 123456 is your Rentrix verification code.
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"challengeId":"<challengeId>","code":"123456"}' http://localhost:8080/api/v1/me/identity/phone/confirm
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/me   # badges.idVerified: true
```

Codes expire after 10 minutes (`OTP_TTL_MINUTES`) and allow 5 tries; a user gets 3 codes per hour and 10 per day, and one number 5 per day across accounts (`429` with `Retry-After`).

### SMS in production

`SMS_BACKEND` is empty by default in `prod`: the API starts, logs a warning, lists phone OTP as unavailable and answers its endpoints with `503 phone_otp_unavailable`. To offer it, set `SMS_BACKEND=http` with `SMS_HTTP_URL`, `SMS_API_KEY`, `SMS_SENDER_ID` (a provider endpoint that accepts `POST {to, sender, message}` with `Authorization: Bearer`) and `ID_HASH_KEY` (at least 32 bytes, for example `openssl rand -base64 48`). Keep all of them in env only. `SMS_BACKEND=log` outside `dev` and `test` refuses to start.
