import en from "./en.json";
import tr from "./tr.json";
import type { Locale } from "./types";

const catalogs: Record<Locale, Record<string, string>> = { en, tr };

/** Backend sends English enum/status strings; map them via i18n catalogs. */
export function translateBackend(
  locale: Locale,
  category: string,
  value: string | null | undefined,
): string {
  if (!value) return "—";
  const key = `backend.${category}.${value}`;
  return catalogs[locale][key] ?? catalogs.en[key] ?? value;
}
