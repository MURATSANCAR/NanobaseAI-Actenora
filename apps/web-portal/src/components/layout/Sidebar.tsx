import { NavLink, useLocation } from "react-router-dom";
import clsx from "clsx";
import { Sparkles, X } from "lucide-react";
import { AiOrb } from "@/components/qa/AiOrb";
import { useAuth } from "@/auth/AuthProvider";
import { ACTENORA_NAV_LINKS } from "@/config/actenoraNav";
import { currentFamilyProductKey, familyProducts } from "@/config/familyProducts";
import { useI18n, type Locale, type MessageKey } from "@/i18n";

type SidebarProps = {
  open: boolean;
  onClose: () => void;
};

const productLabelKeys: Record<string, MessageKey> = {
  actenora: "family.actenora",
  qa: "family.qa",
  bi: "family.bi",
};

const locales: Locale[] = ["en", "tr"];

function isLinkActive(pathname: string, to: string): boolean {
  if (to === "/") return pathname === "/";
  return pathname === to || pathname.startsWith(`${to}/`);
}

export function Sidebar({ open, onClose }: SidebarProps) {
  const { locale, setLocale, t, tb } = useI18n();
  const auth = useAuth();
  const { pathname } = useLocation();
  const activeProduct = currentFamilyProductKey();
  const visibleLinks = ACTENORA_NAV_LINKS.filter((l) => !l.gate || auth.nav(l.gate));

  return (
    <>
      <div
        className={clsx(
          "fixed inset-0 z-40 bg-slate-900/20 backdrop-blur-sm transition-opacity lg:hidden",
          open ? "opacity-100" : "pointer-events-none opacity-0",
        )}
        onClick={onClose}
        aria-hidden={!open}
      />

      <aside
        className={clsx(
          "fixed bottom-[max(0.5rem,env(safe-area-inset-bottom))] left-[max(0.5rem,env(safe-area-inset-left))] top-[max(0.5rem,env(safe-area-inset-top))] z-50 flex w-[min(100vw-1.5rem,17rem)] flex-col rounded-3xl border border-white/70 bg-white/50 shadow-xl backdrop-blur-2xl transition-transform duration-300 lg:static lg:inset-auto lg:z-auto lg:mr-3 lg:w-64 lg:shrink-0 lg:translate-x-0 xl:mr-6",
          open ? "translate-x-0" : "-translate-x-[calc(100%+1.5rem)] lg:translate-x-0",
        )}
        aria-label={t("nav.menu")}
      >
        <div className="border-b border-white/60 p-4">
          <div className="flex items-center justify-between">
            <NavLink
              to="/"
              className="flex min-w-0 items-center gap-3 transition hover:opacity-90"
              onClick={onClose}
            >
              <AiOrb size="md" />
              <div className="min-w-0">
                <h1 className="flex items-center gap-1 truncate text-sm font-bold leading-tight text-violet-900">
                  Actenora
                  <Sparkles className="h-3.5 w-3.5 text-violet-500" />
                </h1>
                <p className="truncate text-[11px] text-slate-500">{t("nav.webPortal")}</p>
                {auth.user ? (
                  <p className="truncate text-[10px] text-slate-400">{auth.user.displayName}</p>
                ) : null}
              </div>
            </NavLink>
            <button
              type="button"
              className="rounded-xl p-2 text-slate-500 transition hover:bg-white/60 lg:hidden"
              onClick={onClose}
              aria-label={t("nav.closeMenu")}
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto p-2">
          <div className="nav-section-title">
            <span className="text-sm">🎙️</span>
            {t("actenora.nav.section")}
          </div>
          {visibleLinks.map((link) => {
            const Icon = link.icon;
            const active = isLinkActive(pathname, link.to);
            return (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.to === "/"}
                onClick={onClose}
                className={clsx(
                  "nav-item group flex items-center gap-2.5 rounded-xl px-3 py-2.5 text-sm font-medium transition-all duration-200",
                  active ? "nav-flat-item--active" : "nav-flat-item--idle",
                )}
              >
                <Icon className="h-4 w-4 shrink-0 opacity-80" aria-hidden />
                <span className="truncate">{t(link.labelKey)}</span>
              </NavLink>
            );
          })}
        </nav>

        <div className="space-y-2 border-t border-white/60 p-2">
          <div className="nav-section-title">{t("family.subtitle")}</div>
          <div className="grid grid-cols-1 gap-1">
            {familyProducts().map((product) => {
              const label = t(productLabelKeys[product.key] ?? "family.actenora");
              const isCurrent = product.key === activeProduct;
              const className = clsx(
                "rounded-xl px-2.5 py-2 text-left text-[11px] font-semibold transition",
                isCurrent
                  ? "bg-violet-600 text-white shadow-md shadow-violet-200/80"
                  : "text-slate-600 hover:bg-white/70 hover:text-violet-900",
              );
              if (isCurrent) {
                return (
                  <span key={product.key} className={className} aria-current="page">
                    {label}
                  </span>
                );
              }
              return (
                <a key={product.key} href={product.href} className={className}>
                  {label}
                </a>
              );
            })}
          </div>

          <div className="grid grid-cols-2 gap-1 rounded-xl bg-white/40 p-1">
            {locales.map((loc) => (
              <button
                key={loc}
                type="button"
                aria-label={loc === "en" ? "English" : "Türkçe"}
                aria-pressed={locale === loc}
                onClick={() => setLocale(loc)}
                className={clsx(
                  "rounded-lg py-1.5 text-[10px] font-semibold uppercase transition duration-200",
                  locale === loc
                    ? "bg-violet-600 text-white shadow-md shadow-violet-200"
                    : "text-slate-500 hover:text-violet-700",
                )}
              >
                {loc === "en" ? "EN" : "TR"}
              </button>
            ))}
          </div>

          <div className="rounded-xl bg-white/40 px-3 py-2 text-[11px] text-slate-500">
            {auth.isLoading ? (
              <span>{t("nav.signingIn")}</span>
            ) : auth.user ? (
              <span className="font-medium text-slate-700">{tb("portalRole", auth.user.role)}</span>
            ) : (
              <span role="alert">{t("nav.authUnavailable")}</span>
            )}
          </div>
        </div>
      </aside>
    </>
  );
}
