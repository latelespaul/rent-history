# Doorway Living

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

The response holds `accessToken` (send it as `Authorization: Bearer …`) and sets the `rentrix_refresh` cookie for `POST /api/v1/auth/refresh`. `POST /api/v1/auth/dev-login/new` (optional `{key}` to reuse one made-up account, see `SYSTEM_DESIGN.md` §12) signs in as a brand-new user with no role yet (`needsOnboarding: true`, a made-up `@example.test` email), so the first sign-in, the role pick, the account-created screen and the verification steps can be tried without Google; the web's dev panel has a "Sign in as a new user" button for it. `POST /api/v1/auth/dev-login` also accepts a user this route created (never an admin), so the same fresh account can sign out and back in. Dev login does not exist in any other profile, and the API refuses to start with `DEV_LOGIN_ENABLED=true` outside `dev` and `test`.

### Create a Google OAuth client

Google sign-in needs only an OAuth **client ID** (no client secret). Never commit it.

1. Open [Google Cloud Console](https://console.cloud.google.com/) and select or create a project (for example "rentrix-dev").
2. APIs & Services → OAuth consent screen: user type **External**, app name "Doorway Living (dev)", a support email, scopes `openid`, `email` and `profile` only. While the app is in "Testing", add your own Google account as a test user.
3. APIs & Services → Credentials → **Create credentials → OAuth client ID** → application type **Web application**.
4. Authorized JavaScript origins: `http://localhost:5173` and `http://localhost` (Google Identity Services needs both for local dev). For production add the web origins `https://doorwayliving.in` and `https://www.doorwayliving.in`. For the admin console add `https://admin.doorwayliving.in` (and `http://admin.localhost:5173` locally). No redirect URIs are needed.
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

### Admin console

The admin console has its own host and its own session (SYSTEM_DESIGN §13.3); the main site never shows, grants or uses the admin role. On the API set:

```sh
ADMIN_HOSTS=admin.doorwayliving.in                # host names of the console; empty = no console
ADMIN_CONSOLE_EMAILS=someone@example.com          # verified Google emails that may sign in there
```

Only requests to an `ADMIN_HOSTS` host may call `/admin/**` or sign in at `/auth/admin/google`. In `prod` this list is the only way in: a `user_roles` `ADMIN` row grants nothing there; it counts only under the `dev` (and `test`) profile, for the seed's Demo Admin. A listed email loses the console at its next refresh once removed from the list. `ADMIN_EMAILS` is no longer read.

Locally the `dev` profile already sets `ADMIN_HOSTS=admin.localhost`: run `npm run dev` and open `http://admin.localhost:5173`, where the dev login offers the seed's Demo Admin. The first page is the OTP relay (`/sms-relay`).

## Locations: areas and societies

Every home sits in a hierarchy City → Area → Society → Tower/Block → Wing → Unit; a standalone house or building has an area and no society (SYSTEM_DESIGN §5.19). Areas carry their PIN codes, and one PIN code can cover several areas. Start the API with demo data to browse it:

```sh
cd rentrix-server
SPRING_PROFILES_ACTIVE=dev SEED_DEMO=true ./mvnw spring-boot:run
```

The demo seed (empty database only) adds an area for every demo locality and one gated society per city with 2 towers of 2 wings: **Demo Greens** (Koramangala, Bangalore) and **Demo Meadows** (Baner, Pune). Demo Residency A is in Demo Greens Tower 1 · Wing A, Demo Towers E in Demo Meadows Tower 2 · Wing B; the other demo homes are standalone. All of it is fake (`is_demo`). Hyderabad, Gurugram, Delhi, Noida and Mumbai get 10–12 real areas each from a Flyway migration in every profile, demo data or not.

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

Every tenant and landlord verifies **any one** method before they can post (tenancies, rent records, reviews, listings): a **phone number** by a code sent to Telegram or by SMS, masked **Aadhaar** or **LinkedIn** (a method counts only once it is verified; Aadhaar and LinkedIn wait for an admin check). Without one the account is read-only and those writes answer `403 id_verification_required` with `missing: ["method"]`. Nothing else gates posting: what a user posts shows at once, and an admin review later gives it the green tick. "Request verification" (`POST /api/v1/me/verification/{role}/submit`) is optional and needs a finished profile and one method verified or waiting for its check; the admin approve then needs one verified (`422 no_verified_method`). PAN is never a posting method, only a badge. A new number is stored only by a confirmed code: `PATCH /api/v1/me` can clear the phone but answers `422 phone_needs_otp` for a new one. The web walks users through all of this at `/verify`.

A profile is **ID verified** (the public badge) when PAN or masked Aadhaar is verified; a phone alone gives no badge. LinkedIn is optional: a verified LinkedIn profile adds a second "LinkedIn verified" badge but never counts as ID. `GET /api/v1/me` carries `badges: {idVerified, linkedinVerified}`; `GET /api/v1/me/identity` lists each method with masked values only (PAN `XXXXXX234F`, Aadhaar `XXXX XXXX 1234`, phone `+91 XXXXXX3210`). Full PAN, Aadhaar numbers and OTP codes are never stored; PAN and masked Aadhaar uploads come with PLAN B12.

### Try phone OTP locally

In `dev` and `test`, `SMS_BACKEND` defaults to `log`: no SMS is sent, and the code is written to the API log instead (the only place a code is ever logged). In `dev` Telegram is the default channel (see [Phone code on Telegram](#phone-code-on-telegram)), so pass `"channel":"SMS"` to get an SMS code. With an access token from dev login or Google sign-in:

```sh
TOKEN=…   # accessToken
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"phone":"+919876543210","channel":"SMS"}' http://localhost:8080/api/v1/me/identity/phone/start
# → {"challengeId":"…","expiresAt":"…"}; the API log shows
#   SMS to +91 XXXXXX3210 (SMS_BACKEND=log, not sent): 123456 is your Doorway Living verification code.
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"challengeId":"<challengeId>","code":"123456"}' http://localhost:8080/api/v1/me/identity/phone/confirm
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/me/verification   # steps.phone: DONE
```

Codes expire after 10 minutes (`OTP_TTL_MINUTES`) and allow 5 tries; a user gets 3 codes per hour and 10 per day, and one number 5 per day across accounts (`429` with `Retry-After`).

### SMS in production

`SMS_BACKEND` is empty by default in `prod`: the API starts, logs a warning, lists phone OTP as unavailable and answers its endpoints with `503 phone_otp_unavailable`. To offer it, set `SMS_BACKEND=http` with `SMS_HTTP_URL`, `SMS_API_KEY`, `SMS_SENDER_ID` (a provider endpoint that accepts `POST {to, sender, message}` with `Authorization: Bearer`) and `ID_HASH_KEY` (at least 32 bytes, for example `openssl rand -base64 48`). Keep all of them in env only. `SMS_BACKEND=log` outside `dev` and `test` refuses to start.

### Preview without an SMS provider (`admin-relay`)

A preview deployment can run phone OTP before an SMS provider is signed up: `SMS_BACKEND=admin-relay` sends no SMS and holds each pending code in API memory until it expires. An admin reads them at `GET /api/v1/admin/sms-relay` (user, masked phone, code, expiry; admins only; a test number below shows in full) and passes the code to the user. Outside `dev` and `test` it starts only with `PREVIEW_MODE=true` and an `ID_HASH_KEY`; every start logs a `WARN` naming `admin-relay`, and only `dev` writes the codes to the log. Live mode is `PREVIEW_MODE` off with `SMS_BACKEND=http`. Codes are lost on restart: the user asks for a new one.

To get each code without opening the admin console, also set `ADMIN_RELAY_TELEGRAM_BOT_TOKEN` and `ADMIN_RELAY_TELEGRAM_CHAT_IDS` (comma list): every held code except a test number's is then pushed to those Telegram chats as `Doorway Living OTP for <name> (+91 XXXXXX3210): 123456 — expires 10 min`. Both live in env only; the token is never logged. The push runs in the background, and a Telegram failure logs one warning and never breaks the OTP. While the relay is on, the web's code step says the team will message the code (WhatsApp/SMS) instead of "We sent it by SMS".

### Test phone numbers

Numbers that start with `OTP_TEST_NUMBER_PREFIX` (default `+91999990`, i.e. `+91 99999 0xxxx`; written as `+91…`, `91…` or the bare digits, spaces and dashes allowed; anything that is not `+91` plus 1 to 9 digits refuses to start) are test numbers. Their codes are never sent by SMS, the Telegram Gateway or the relay's Telegram push, whatever the channel: with `SMS_BACKEND=admin-relay` the code waits on the admin relay page only, and with any other backend it is not sent at all. The web's code step then says the team will message the code. Real numbers still send as normal. Every e2e and release-gate run uses only test numbers (`testPhone()` in `rentrix-web/e2e/support.ts`).

### Phone code on Telegram

With `TELEGRAM_GATEWAY_TOKEN` set (a Telegram Gateway API token, env only, never logged; it also needs `ID_HASH_KEY`), Telegram is the default phone method: the code shows in the Telegram app of the account registered to the number, and the web offers "No Telegram? Get the code by SMS" as the fallback. Without the token the option is hidden. A number Telegram cannot reach answers `503 telegram_send_failed` (SYSTEM_DESIGN D-146).

In `dev`, `TELEGRAM_GATEWAY_FAKE` defaults to `true` (`test` keeps it off): nothing is sent, and the code goes to the admin relay when `SMS_BACKEND=admin-relay`, else to the API log as `Telegram code for +91 XXXXXX3210 (TELEGRAM_GATEWAY_FAKE=true, not sent): 123456`. `+919000000000` plays a number without Telegram. `TELEGRAM_GATEWAY_FAKE=true` outside `dev` and `test` refuses to start.

### Verify with Truecaller

On Android, the phone step offers "Verify with Truecaller" first; the SMS code stays as the fallback (SYSTEM_DESIGN D-138). Each deployment has its own Truecaller *Web* app: set `TRUECALLER_APP_KEY` (its key) and `TRUECALLER_APP_NAME` (the name shown in the Truecaller sheet), env only. Without a key the button is hidden. Truecaller posts the user's consent to `<API>/api/v1/auth/truecaller/callback`, which must be the callback URL registered with the app; the phone also needs `ID_HASH_KEY`.

In `dev` and `test`, `TRUECALLER_FAKE` defaults to `true`: no Truecaller call is made, and you play Truecaller's server yourself. With an access token from dev login:

```sh
curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/me/identity/truecaller/start
# {"requestId":"<id>","deepLink":"truecallersdk://truesdk/web_verify?...","expiresAt":"..."}
curl -s -X POST -H 'Content-Type: application/json' \
  -d '{"requestId":"<id>","accessToken":"fake:919876543210","endpoint":"https://profile4-noneu.truecaller.com/v1/default"}' \
  http://localhost:8080/api/v1/auth/truecaller/callback
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/me/identity/truecaller/<id>
# {"requestId":"<id>","status":"VERIFIED","masked":"+91 XXXXXX3210"}
```

`{"requestId":"<id>","status":"user_rejected"}` plays a declined sheet. The button shows only in an Android browser (Chrome DevTools device mode with an Android user agent works for the web side). `TRUECALLER_FAKE=true` outside `dev` and `test` refuses to start.

### Headline ratings (`RATINGS_HEADLINE`)

`RATINGS_HEADLINE=all` (the default) makes the headline ratings count every published review. `verified` counts only reviews an admin gave the green tick (D-135); switch to it once admin approvals (W9) ship. After a switch, the nightly rating repair rebuilds the stored aggregates.
