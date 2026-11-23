// Runtime config for the static web build (D-04). Loaded before the app bundle.
// Replace this file on the host to point one build at a different API, e.g.
//   window.__RENTRIX_CONFIG__ = { apiBaseUrl: "https://api.doorwayliving.in/api/v1" }
// An empty apiBaseUrl falls back to VITE_API_BASE_URL from the build, then to /api/v1.
// adminApiBaseUrl is where the admin console (the admin.* host) calls the API; empty = same-origin
// /api/v1 on the admin host, which is the only host the API accepts admin calls from.
// Serve this file with "Cache-Control: no-cache" so a change takes effect at once.
window.__RENTRIX_CONFIG__ = { apiBaseUrl: "", adminApiBaseUrl: "" }
