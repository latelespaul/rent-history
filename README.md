# Rentrix

Two-sided rental trust platform: tenants rate landlords and properties, landlords rate tenants, all tied to confirmed tenancies.

- `rentrix-server/`: API (Spring Boot, Java 21). Notes: [`rentrix-server/README.md`](rentrix-server/README.md).
- `rentrix-web/`: web app (React + Vite).
- `rentrix-docs/`: design ([`architecture/SYSTEM_DESIGN.md`](rentrix-docs/architecture/SYSTEM_DESIGN.md)) and the PR plan ([`PLAN.md`](rentrix-docs/PLAN.md)).

## Run the API locally

Needs only Java 21 (SQLite file, no Google account, no Docker):

```sh
cd rentrix-server
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The API listens on `http://localhost:8080/api/v1`. `SPRING_PROFILES_ACTIVE` is required (`dev`, `test` or `prod`); every setting is in SYSTEM_DESIGN §17.

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
4. Authorized JavaScript origins: `http://localhost:5173` and `http://localhost` (Google Identity Services needs both for local dev). Add the production web origin later (for example `https://rentrix.in`). No redirect URIs are needed.
5. Copy the client ID (`…apps.googleusercontent.com`). Do **not** download or commit the JSON file.
6. Give it to the API only, from your shell or an untracked `.env` file (`.env` is git-ignored):

   ```sh
   export GOOGLE_CLIENT_ID=…apps.googleusercontent.com
   ```

   The web reads it from `GET /api/v1/auth/config`, so it needs no Google setting of its own.
7. Start the API and the web; "Sign in with Google" appears on the login page.

Without `GOOGLE_CLIENT_ID` the Google button is hidden. In `prod` the API still starts, logs a warning and answers `POST /auth/google` with `503 google_sign_in_disabled`.

### Admins

`ADMIN` is never self-service. Set `ADMIN_EMAILS` to a comma list of verified Google emails; those users get `ADMIN` on their next sign-in or refresh, and lose it when their email is removed from the list.
