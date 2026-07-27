import { PRODUCT_BRAND } from "@/config/brand";
import type { MessageKey } from "@/i18n";

export interface FamilyProduct {
  key: "qa" | "bi" | "actenora";
  label: string;
  href: string;
  descriptionKey: MessageKey;
}

const DEFAULT_QA_URL = "https://portal.nanobase.ai";
const DEFAULT_BI_URL = "https://bi.nanobase.ai";
const DEFAULT_ACTENORA_URL = "https://portal.nanobase.ai/easymeeting/";

function env(): Partial<ImportMetaEnv> | undefined {
  try {
    return typeof import.meta !== "undefined" ? import.meta.env : undefined;
  } catch {
    return undefined;
  }
}

/** EasyMeeting product family links shown in the global top bar. */
export function familyProducts(): FamilyProduct[] {
  const meta = env();
  return [
    {
      key: "actenora",
      label: PRODUCT_BRAND,
      href: meta?.VITE_NANOBI_ACTENORA_URL ?? DEFAULT_ACTENORA_URL,
      descriptionKey: "family.actenora.description",
    },
    {
      key: "qa",
      label: "NanobaseAI-QA",
      href: meta?.VITE_NANOBI_QA_URL ?? DEFAULT_QA_URL,
      descriptionKey: "family.qa.description",
    },
    {
      key: "bi",
      label: "NanobaseAI-BI",
      href: meta?.VITE_NANOBI_BI_URL ?? DEFAULT_BI_URL,
      descriptionKey: "family.bi.description",
    },
  ];
}

export function currentFamilyProductKey(): FamilyProduct["key"] {
  const configured = env()?.VITE_NANOBI_CURRENT_PRODUCT as FamilyProduct["key"] | undefined;
  return configured ?? "actenora";
}
