import type { FamilyProduct } from "../../config/familyProducts";

interface FamilyProductButtonProps {
  product: FamilyProduct;
  isCurrent: boolean;
}

/** Shared pill button used for every NanobaseAI family module in the top bar. */
export function FamilyProductButton({ product, isCurrent }: FamilyProductButtonProps) {
  const className = isCurrent ? "family-product-btn current" : "family-product-btn";

  if (isCurrent) {
    return (
      <span className={className} aria-current="page" title={product.description}>
        {product.label}
      </span>
    );
  }

  return (
    <a className={className} href={product.href} title={product.description}>
      {product.label}
    </a>
  );
}
