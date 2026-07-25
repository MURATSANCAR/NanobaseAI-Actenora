import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../../auth/AuthProvider";

const links: Array<{ to: string; label: string; gate?: "models" | "operations" | "audit" | "teams" | "templates" }> = [
  { to: "/", label: "Dashboard" },
  { to: "/meetings", label: "Meetings" },
  { to: "/decisions", label: "Decision Ledger" },
  { to: "/actions", label: "Action Center" },
  { to: "/commitments", label: "Commitment Tracker" },
  { to: "/templates", label: "Template Studio", gate: "templates" },
  { to: "/teams", label: "Teams Settings", gate: "teams" },
  { to: "/models", label: "Model Management", gate: "models" },
  { to: "/jobs", label: "AI Job Timeline" },
  { to: "/operations", label: "Operations Center", gate: "operations" },
  { to: "/audit", label: "Audit Viewer", gate: "audit" },
];

export function AppShell() {
  const auth = useAuth();

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <aside className="sidebar" aria-label="Primary">
        <div className="sidebar-brand">
          <p className="brand-mark">Actenora</p>
          <p className="brand-sub">Web portal</p>
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
                {l.label}
              </NavLink>
            ))}
        </nav>
        <div className="sidebar-user" aria-live="polite">
          {auth.isLoading ? (
            <span>Signing in…</span>
          ) : auth.user ? (
            <>
              <strong>{auth.user.displayName}</strong>
              <span className="muted">{auth.user.role}</span>
            </>
          ) : (
            <span role="alert">Auth unavailable</span>
          )}
        </div>
      </aside>
      <main id="main" className="main-pane" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  );
}
