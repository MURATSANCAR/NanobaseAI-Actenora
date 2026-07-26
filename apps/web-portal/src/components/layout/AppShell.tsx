import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../../auth/AuthProvider";
import { useI18n, type MessageKey } from "../../i18n";
import { FamilyTopBar } from "./FamilyTopBar";
import { LocaleSwitcher } from "./LocaleSwitcher";

const links: Array<{
  to: string;
  labelKey: MessageKey;
  gate?: "models" | "operations" | "audit" | "teams" | "templates";
}> = [
  { to: "/", labelKey: "nav.dashboard" },
  { to: "/meetings", labelKey: "nav.meetings" },
  { to: "/decisions", labelKey: "nav.decisions" },
  { to: "/actions", labelKey: "nav.actions" },
  { to: "/commitments", labelKey: "nav.commitments" },
  { to: "/templates", labelKey: "nav.templates", gate: "templates" },
  { to: "/teams", labelKey: "nav.teams", gate: "teams" },
  { to: "/models", labelKey: "nav.models", gate: "models" },
  { to: "/jobs", labelKey: "nav.jobs" },
  { to: "/operations", labelKey: "nav.operations", gate: "operations" },
  { to: "/audit", labelKey: "nav.audit", gate: "audit" },
];

export function AppShell() {
  const auth = useAuth();
  const { t, tb } = useI18n();

  return (
    <div className="app-frame">
      <FamilyTopBar />
      <div className="app-shell">
        <a className="skip-link" href="#main">
          {t("nav.skip")}
        </a>
        <aside className="sidebar" aria-label="Primary">
          <div className="sidebar-brand">
            <p className="brand-mark">Actenora</p>
            <p className="brand-sub">{t("nav.webPortal")}</p>
          </div>
          <nav className="side-nav">
            {links
              .filter((l) => !l.gate || auth.nav(l.gate))
              .map((l) => (
                <NavLink
                  key={l.to}
                  to={l.to}
                  end={l.to === "/"}
                  className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}
                >
                  {t(l.labelKey)}
                </NavLink>
              ))}
          </nav>
          <div className="sidebar-footer">
            <LocaleSwitcher />
            <div className="sidebar-user" aria-live="polite">
              {auth.isLoading ? (
                <span>{t("nav.signingIn")}</span>
              ) : auth.user ? (
                <>
                  <strong>{auth.user.displayName}</strong>
                  <span className="muted">{tb("portalRole", auth.user.role)}</span>
                </>
              ) : (
                <span role="alert">{t("nav.authUnavailable")}</span>
              )}
            </div>
          </div>
        </aside>
        <main id="main" className="main-pane" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
