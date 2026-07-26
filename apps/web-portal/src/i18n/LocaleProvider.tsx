import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import en from "./en.json";
import tr from "./tr.json";
import type { Locale, MessageKey } from "./types";
import { translateBackend } from "./translateBackend";

const STORAGE_KEY = "actenora-portal-locale";

const catalogs: Record<Locale, Record<string, string>> = { en, tr };

type I18nContextValue = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: MessageKey, vars?: Record<string, string | number>) => string;
  tb: (category: string, value: string | null | undefined) => string;
};

const I18nContext = createContext<I18nContextValue | null>(null);

function detectInitialLocale(): Locale {
  if (typeof window === "undefined") return "en";
  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (stored === "tr" || stored === "en") return stored;
  const lang = window.navigator.language.toLowerCase();
  return lang.startsWith("tr") ? "tr" : "en";
}

function interpolate(template: string, vars?: Record<string, string | number>): string {
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (_, name: string) => String(vars[name] ?? `{${name}}`));
}

export function LocaleProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(detectInitialLocale);

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next);
    window.localStorage.setItem(STORAGE_KEY, next);
    document.documentElement.lang = next;
  }, []);

  const value = useMemo<I18nContextValue>(() => {
    const catalog = catalogs[locale];
    return {
      locale,
      setLocale,
      t(key, vars) {
        const raw = catalog[key] ?? catalogs.en[key] ?? key;
        return interpolate(raw, vars);
      },
      tb(category, value) {
        return translateBackend(locale, category, value);
      },
    };
  }, [locale, setLocale]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error("useI18n must be used within LocaleProvider");
  return ctx;
}
