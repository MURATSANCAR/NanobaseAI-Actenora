/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_MODE?: "http";
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_PORTAL_AUTH_MODE?: "mock" | "msal";
  readonly VITE_MOCK_ENTRA_OID?: string;
  readonly VITE_MOCK_ENTRA_TID?: string;
  readonly VITE_MOCK_EMAIL?: string;
  readonly VITE_MOCK_DISPLAY_NAME?: string;
  readonly VITE_MOCK_GLOBAL_ADMIN?: string;
  readonly VITE_NANOBI_QA_URL?: string;
  readonly VITE_NANOBI_BI_URL?: string;
  readonly VITE_NANOBI_ACTENORA_URL?: string;
  readonly VITE_NANOBI_CURRENT_PRODUCT?: "qa" | "bi" | "actenora";
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
