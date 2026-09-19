# Mocks — how to switch from mock to real API

## Overview

Every API call lives in `src/api/*.ts` and follows this pattern:

    USE_MOCKS ? mockXxx() : api.get('/real-endpoint')

The flag `USE_MOCKS` reads `VITE_ENABLE_MOCKS` from `.env`.

## Switching to real backend

1. Ensure Spring Boot exposes all endpoints below.
2. Enable CORS for the frontend origin (see CORS section).
3. In `.env` set:

       VITE_ENABLE_MOCKS=false

4. Restart the dev server (`npm run dev`).
5. Delete `src/api/mock/` if you want (not required — it becomes dead code).

No component or hook changes required.

## Expected endpoints (contract for Spring Boot)

### Auth

| Method | Path | Request | Response |
|--------|------|---------|----------|
| POST | /api/v1/auth/signup | { email, password, name, role } | { user, accessToken, refreshToken } |
| POST | /api/v1/auth/login | { email, password } | { user, accessToken, refreshToken } |
| POST | /api/v1/auth/refresh | { refreshToken } | { accessToken } |
| GET | /api/v1/auth/me | — | User |
| POST | /api/v1/auth/logout | — | 204 |

### Flats

| Method | Path | Query | Response |
|--------|------|-------|----------|
| GET | /api/v1/flats | city, state, minRent, maxRent, numberOfRooms, available, page, size, sort | Page<Flat> |
| GET | /api/v1/flats/:id | — | Flat |

### Reviews

| Method | Path | Request | Response |
|--------|------|---------|----------|
| GET | /api/v1/flats/:id/reviews | page, size | Page<Review> |
| POST | /api/v1/flats/:id/reviews | { title, content, rating } | Review |
| GET | /api/v1/users/me/reviews | — | Page<Review> |

## Data shapes

See `src/types/*.ts` — those interfaces are the contract.

### Flat

    { id, address, city, state, numberOfRooms, area, rent,
      description, isAvailable, averageRating?, reviewCount? }

### Review

    { id, userId, userName, flatId, title, content, rating, reviewDate }

## CORS for Spring Boot (when ready)

In your Spring config add:

    @Configuration
    public class CorsConfig implements WebMvcConfigurer {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "https://your-vercel-url.app")
                .allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
        }
    }

Or in `application.yml`:

    app:
      cors:
        allowed-origins: http://localhost:5173,https://your-vercel-url.app

## Auth flow (JWT + refresh)

`src/api/client.ts` already implements:

1. Attach `Authorization: Bearer <accessToken>` on every request.
2. On 401 → call `/auth/refresh` once.
3. Retry original request with new access token.
4. If refresh fails → clear tokens → call `onUnauthorized()` (redirect to login).

Zustand store (Step 5) will call `tokenStore.set(access, refresh)` on login and
`tokenStore.clear()` on logout, and register `onUnauthorized` to redirect.

## Mock users (login credentials in mock mode)

| Email | Role | Password (any ≥4 chars) |
|-------|------|--------------------------|
| tenant@rentio.test | TENANT | anything |
| landlord@rentio.test | LANDLORD | anything |
| admin@rentio.test | ADMIN | anything |

## Gotchas

- `maybeFail(0.02)` in `flats.mock.ts` randomly fails 2% of requests to exercise error UI. Remove when annoying.
- Mock DB is in-memory — refreshing the page resets everything.
- `mockMe()` always returns user id 1 (Aarav). Change during auth work.