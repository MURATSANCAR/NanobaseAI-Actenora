import type { FamilyProduct } from "../../config/familyProducts";
import { useI18n } from "@/i18n";

interface FamilyProductButtonProps {
  product: FamilyProduct;
  isCurrent: boolean;
}

/** Shared pill button used for every product-family module in the top bar. */
export function FamilyProductButton({ product, isCurrent }: FamilyProductButtonProps) {
  const { t } = useI18n();
  const className = isCurrent ? "family-product-btn current" : "family-product-btn";
  const description = t(product.descriptionKey);

  if (isCurrent) {
    return (
      <span className={className} aria-current="page" title={description}>
        {product.label}
      </span>
    );
  }

  return (
    <a className={className} href={product.href} title={description}>
      {product.label}
    </a>
  );
}
