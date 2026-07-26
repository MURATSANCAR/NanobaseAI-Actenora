import { useI18n, type Locale } from "../../i18n";

const options: { value: Locale; labelKey: "locale.name" }[] = [
  { value: "en", labelKey: "locale.name" },
  { value: "tr", labelKey: "locale.name" },
];

export function LocaleSwitcher() {
  const { locale, setLocale, t } = useI18n();

  return (
    <label className="locale-switcher">
      <span className="sr-only">{t("locale.switch")}</span>
      <select
        value={locale}
        onChange={(e) => setLocale(e.target.value as Locale)}
        aria-label={t("locale.switch")}
      >
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.value === "en" ? "English" : "Türkçe"}
          </option>
        ))}
      </select>
    </label>
  );
}
