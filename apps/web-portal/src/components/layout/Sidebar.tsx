import { NavLink, useLocation } from "react-router-dom";
import clsx from "clsx";
import { Sparkles, X } from "lucide-react";
import { AiOrb } from "@/components/qa/AiOrb";
import { useAuth } from "@/auth/AuthProvider";
import { ACTENORA_NAV_GROUPS, type ActenoraNavLink } from "@/config/actenoraNav";
import { useI18n, type Locale } from "@/i18n";

type SidebarProps = {
  open: boolean;
  onClose: () => void;
};

const locales: Locale[] = ["en", "tr"];

function isLinkActive(pathname: string, to: string): boolean {
  if (to === "/") return pathname === "/";
  return pathname === to || pathname.startsWith(`${to}/`);
}

function NavItem({
  link,
  active,
  onClose,
  label,
}: {
  link: ActenoraNavLink;
  active: boolean;
  onClose: () => void;
  label: string;
}) {
  const Icon = link.icon;
  return (
    <NavLink
      to={link.to}
      end={link.to === "/"}
      onClick={onClose}
      className={clsx(
        "flex items-center gap-3 rounded-2xl px-3.5 py-3 text-[15px] font-semibold leading-tight transition-all duration-200",
        active
          ? "bg-violet-600 text-white shadow-lg shadow-violet-300/40"
          : "text-slate-700 hover:bg-white/80 hover:text-violet-900",
      )}
    >
      <Icon className={clsx("h-5 w-5 shrink-0", active ? "text-white" : "text-violet-600")} aria-hidden />
      <span className="min-w-0 flex-1 truncate">{label}</span>
    </NavLink>
  );
}

export function Sidebar({ open, onClose }: SidebarProps) {
  const { locale, setLocale, t, tb } = useI18n();
  const auth = useAuth();
  const { pathname } = useLocation();

  const groups = ACTENORA_NAV_GROUPS.map((group) => ({
    ...group,
    links: group.links.filter((l) => !l.gate || auth.nav(l.gate)),
  })).filter((group) => group.links.length > 0);

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
          "fixed bottom-[max(0.5rem,env(safe-area-inset-bottom))] left-[max(0.5rem,env(safe-area-inset-left))] top-[max(0.5rem,env(safe-area-inset-top))] z-50 flex w-[min(100vw-1.25rem,19.5rem)] flex-col rounded-3xl border border-white/70 bg-white/55 shadow-xl backdrop-blur-2xl transition-transform duration-300 lg:static lg:inset-auto lg:z-auto lg:mr-3 lg:w-72 lg:shrink-0 lg:translate-x-0 xl:mr-5",
          open ? "translate-x-0" : "-translate-x-[calc(100%+1.5rem)] lg:translate-x-0",
        )}
        aria-label={t("nav.menu")}
      >
        <div className="border-b border-white/60 px-4 py-4">
          <div className="flex items-center justify-between gap-2">
            <NavLink
              to="/"
              className="flex min-w-0 flex-1 items-center gap-3 transition hover:opacity-90"
              onClick={onClose}
            >
              <AiOrb size="lg" />
              <div className="min-w-0">
                <h1 className="flex items-center gap-1.5 truncate text-base font-bold leading-tight text-violet-900">
                  Actenora
                  <Sparkles className="h-4 w-4 text-violet-500" />
                </h1>
                <p className="truncate text-xs text-slate-500">{t("nav.webPortal")}</p>
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

        <nav className="flex-1 space-y-4 overflow-y-auto px-3 py-3">
          {groups.map((group) => (
            <div key={group.titleKey} className="space-y-1.5">
              <p className="px-2 text-[11px] font-bold uppercase tracking-wider text-violet-700/90">
                {t(group.titleKey)}
              </p>
              <div className="space-y-1">
                {group.links.map((link) => (
                  <NavItem
                    key={link.to}
                    link={link}
                    active={isLinkActive(pathname, link.to)}
                    onClose={onClose}
                    label={t(link.labelKey)}
                  />
                ))}
              </div>
            </div>
          ))}
        </nav>

        <div className="space-y-2 border-t border-white/60 p-3">
          <div className="rounded-2xl bg-white/50 px-3 py-2.5">
            {auth.isLoading ? (
              <p className="text-xs text-slate-500">{t("nav.signingIn")}</p>
            ) : auth.user ? (
              <>
                <p className="truncate text-sm font-semibold text-slate-800">{auth.user.displayName}</p>
                <p className="truncate text-xs text-slate-500">{tb("portalRole", auth.user.role)}</p>
              </>
            ) : (
              <p className="text-xs text-red-600" role="alert">
                {t("nav.authUnavailable")}
              </p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-1.5 rounded-2xl bg-white/40 p-1.5">
            {locales.map((loc) => (
              <button
                key={loc}
                type="button"
                aria-label={loc === "en" ? "English" : "Türkçe"}
                aria-pressed={locale === loc}
                onClick={() => setLocale(loc)}
                className={clsx(
                  "rounded-xl py-2 text-xs font-bold uppercase tracking-wide transition duration-200",
                  locale === loc
                    ? "bg-violet-600 text-white shadow-md shadow-violet-200"
                    : "text-slate-500 hover:bg-white/70 hover:text-violet-700",
                )}
              >
                {loc === "en" ? "English" : "Türkçe"}
              </button>
            ))}
          </div>
        </div>
      </aside>
    </>
  );
}
