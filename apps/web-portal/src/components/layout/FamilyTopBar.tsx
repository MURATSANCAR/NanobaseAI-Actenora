import { currentFamilyProductKey, familyProducts } from "../../config/familyProducts";
import { useI18n, type MessageKey } from "../../i18n";
import { FamilyProductButton } from "./FamilyProductButton";

const productLabelKeys: Record<string, MessageKey> = {
  actenora: "family.actenora",
  qa: "family.qa",
  bi: "family.bi",
};

export function FamilyTopBar() {
  const activeKey = currentFamilyProductKey();
  const { t } = useI18n();

  return (
    <header className="family-top-bar" aria-label="NanobaseAI product family">
      <div className="family-top-bar-inner">
        <p className="family-top-bar-brand">
          <span className="family-top-bar-mark">{t("family.brand")}</span>
          <span className="family-top-bar-sub">{t("family.subtitle")}</span>
        </p>
        <nav className="family-top-bar-nav" aria-label="Switch product">
          {familyProducts().map((product) => (
            <FamilyProductButton
              key={product.key}
              product={{
                ...product,
                label: t(productLabelKeys[product.key] ?? "family.actenora"),
              }}
              isCurrent={product.key === activeKey}
            />
          ))}
        </nav>
      </div>
    </header>
  );
}
