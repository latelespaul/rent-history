// Runtime config for the static web build (D-04). Loaded before the app bundle.
// Replace this file on the host to point one build at a different API, e.g.
//   window.__RENTRIX_CONFIG__ = { apiBaseUrl: "https://api.doorwayliving.in/api/v1" }
// An empty apiBaseUrl falls back to VITE_API_BASE_URL from the build, then to /api/v1.
// Serve this file with "Cache-Control: no-cache" so a change takes effect at once.
window.__RENTRIX_CONFIG__ = { apiBaseUrl: "" }
