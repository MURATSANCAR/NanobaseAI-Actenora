import { currentFamilyProductKey, familyProducts } from "../../config/familyProducts";

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
          {familyProducts().map((product) => {
            const isCurrent = product.key === activeKey;
            return isCurrent ? (
              <span
                key={product.key}
                className="family-product-link current"
                aria-current="page"
                title={product.description}
              >
                {product.label}
              </span>
            ) : (
              <a
                key={product.key}
                className="family-product-link"
                href={product.href}
                title={product.description}
              >
                {product.label}
              </a>
            );
          })}
        </nav>
      </div>
    </header>
  );
}
