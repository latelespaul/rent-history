# W2 · Design system + shell

| File | Shows |
|---|---|
| `shell-desktop.png` | Top bar (brand, nav, city picker, Sign in), Home content, footer launch line · 1440 px |
| `shell-desktop-picker-open.png` | Top bar city picker open: only the cities `GET /cities` returned |
| `shell-mobile.png` | Same at 390 px: nav and picker folded behind the menu button |
| `shell-mobile-menu.png` | Mobile menu sheet: city picker, nav, Sign in |
| `empty-state-desktop.png`, `empty-state-mobile.png` | Empty state (unknown page) |
| `error-state-desktop.png`, `error-state-mobile.png` | Error state: `GET /cities` answers 500; picker disabled, city cards show the error with "Try again" |

Taken with headless Chromium (Playwright 1.49.1) against `npm run dev` in `rentrix-web`, with a mock API
on `API_PROXY_TARGET` serving the 2 launch cities and the seed counts (and a 500 for the error shots).
