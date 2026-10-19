/// <reference types="vite/client" />

/** Build-time settings Vite inlines into `import.meta.env` (see `apiBaseUrl`). */
interface ImportMetaEnv {
  /** The API's absolute URL when the site is hosted on another origin; unset in development. */
  readonly VITE_API_BASE_URL?: string;
}
