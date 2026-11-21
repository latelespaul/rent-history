/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Build-time API base URL (D-04); `public/config.js` overrides it at runtime. */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
