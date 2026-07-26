import { currentFamilyProductKey, familyProducts } from "../../config/familyProducts";
import { FamilyProductButton } from "./FamilyProductButton";

export function FamilyTopBar() {
  const activeKey = currentFamilyProductKey();

  return (
    <header className="family-top-bar" aria-label="NanobaseAI product family">
      <div className="family-top-bar-inner">
        <p className="family-top-bar-brand">
          <span className="family-top-bar-mark">NanobaseAI</span>
          <span className="family-top-bar-sub">Product family</span>
        </p>
        <nav className="family-top-bar-nav" aria-label="Switch product">
          {familyProducts().map((product) => (
            <FamilyProductButton
              key={product.key}
              product={product}
              isCurrent={product.key === activeKey}
            />
          ))}
        </nav>
      </div>
    </header>
  );
}
