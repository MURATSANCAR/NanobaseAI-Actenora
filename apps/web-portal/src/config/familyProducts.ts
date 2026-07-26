export interface FamilyProduct {
  key: "qa" | "bi" | "actenora";
  label: string;
  href: string;
  description: string;
}

const DEFAULT_QA_URL = "https://qa.nanobasea.ai";
const DEFAULT_BI_URL = "https://bi.nanobasea.ai";
const DEFAULT_ACTENORA_URL = "https://portal.nanobasea.ai";

/** NanobaseAI product family links shown in the global top bar. */
export function familyProducts(): FamilyProduct[] {
  return [
    {
      key: "qa",
      label: "NanobaseAI-QA",
      href: import.meta.env.VITE_NANOBI_QA_URL ?? DEFAULT_QA_URL,
      description: "Test automation and conformance",
    },
    {
      key: "bi",
      label: "NanobaseAI-BI",
      href: import.meta.env.VITE_NANOBI_BI_URL ?? DEFAULT_BI_URL,
      description: "Governed analytics and NL2SQL",
    },
    {
      key: "actenora",
      label: "Actenora",
      href: import.meta.env.VITE_NANOBI_ACTENORA_URL ?? DEFAULT_ACTENORA_URL,
      description: "Teams meeting intelligence",
    },
  ];
}

export function currentFamilyProductKey(): FamilyProduct["key"] {
  const configured = import.meta.env.VITE_NANOBI_CURRENT_PRODUCT as FamilyProduct["key"] | undefined;
  return configured ?? "actenora";
}
