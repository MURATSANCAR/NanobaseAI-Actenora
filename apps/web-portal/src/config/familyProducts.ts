export interface FamilyProduct {
  key: "qa" | "bi" | "actenora";
  label: string;
  href: string;
  description: string;
}

const DEFAULT_QA_URL = "https://portal.nanobase.ai";
const DEFAULT_BI_URL = "https://bi.nanobase.ai";
const DEFAULT_ACTENORA_URL = "https://portal.nanobase.ai/actenora/";

function env(): Partial<ImportMetaEnv> | undefined {
  try {
    return typeof import.meta !== "undefined" ? import.meta.env : undefined;
  } catch {
    return undefined;
  }
}

/** NanobaseAI product family links shown in the global top bar (Actenora first). */
export function familyProducts(): FamilyProduct[] {
  const meta = env();
  return [
    {
      key: "actenora",
      label: "Actenora",
      href: meta?.VITE_NANOBI_ACTENORA_URL ?? DEFAULT_ACTENORA_URL,
      description: "Teams meeting intelligence",
    },
    {
      key: "qa",
      label: "NanobaseAI-QA",
      href: meta?.VITE_NANOBI_QA_URL ?? DEFAULT_QA_URL,
      description: "Test automation and conformance",
    },
    {
      key: "bi",
      label: "NanobaseAI-BI",
      href: meta?.VITE_NANOBI_BI_URL ?? DEFAULT_BI_URL,
      description: "Governed analytics and NL2SQL",
    },
  ];
}

export function currentFamilyProductKey(): FamilyProduct["key"] {
  const configured = env()?.VITE_NANOBI_CURRENT_PRODUCT as FamilyProduct["key"] | undefined;
  return configured ?? "actenora";
}
