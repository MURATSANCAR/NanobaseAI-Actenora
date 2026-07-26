/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_MODE?: "mock" | "http";
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_NANOBI_QA_URL?: string;
  readonly VITE_NANOBI_BI_URL?: string;
  readonly VITE_NANOBI_ACTENORA_URL?: string;
  readonly VITE_NANOBI_CURRENT_PRODUCT?: "qa" | "bi" | "actenora";
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
