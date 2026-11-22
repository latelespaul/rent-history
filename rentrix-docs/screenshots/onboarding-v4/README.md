# Onboarding v4 screenshots: phone required, open by default, verification stepper

Taken with a headless Chromium (throwaway profile, 1280 × 900) against the local dev stack with the demo seed.
Phone codes come from the API log (`SMS_BACKEND=log`, dev only). The round icon at the bottom right is the Vite dev
server's overlay, not part of the app.

| File | Page | Signed in as |
|---|---|---|
| `welcome-created.png` | `/welcome/created`: "Welcome to Doorway Living, your account is created", name, email, role, "Verify your phone number" | a new dev user (Sign in as a new user), right after the role pick |
| `verify-phone.png` | `/verify/phone`: step 2, code sent, the 6-digit code field and the resend timer | the same new user |
| `verify-id.png` | `/verify/id`: step 3, optional: phone verified, PAN and masked Aadhaar "Coming soon", LinkedIn | the same new user |
| `verify-rental.png` | `/verify/rental`: step 4, optional: rental details "Coming soon" (PLAN B18) | the same new user |
| `verify-submitted.png` | `/verify` after "Request verification": "Waiting for review", posting already open | the same new user |
| `account-menu-progress.png` | the account menu on step 4: the role badge, "Get verified: 2 of 4 steps" with its bar and the next step, and the dot on the chip | the same new user (profile and phone done) |
| `returning-sign-in-toast.png` | "Signed in as Demo Tenant BLR-1", back on the page the user came from | Demo Tenant BLR-1 |

## Run it locally

```sh
# API (Java 21): SQLite file, demo seed, dev login, phone codes in the log, port 8080
cd rentrix-server
SPRING_PROFILES_ACTIVE=dev SEED_DEMO=true DEV_LOGIN_ENABLED=true ./mvnw spring-boot:run

# Web (Node 20+), second terminal: Vite on port 5173 proxies /api to localhost:8080
cd rentrix-web
npm ci
npm run dev
```

Open http://localhost:5173/login . In the DEV ONLY panel press **Sign in as a new user** for the first
sign-in (role pick, account created, stepper), or pick a demo seed user for a returning sign-in. The
phone step's code is in the API log: `SMS to +91 XXXXXX…: 123456 is your Doorway Living verification code.`
