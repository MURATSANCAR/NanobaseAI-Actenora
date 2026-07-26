import type { LucideIcon } from "lucide-react";
import {
  Activity,
  CheckSquare,
  ClipboardCheck,
  ClipboardList,
  FileText,
  Handshake,
  LayoutDashboard,
  Mic,
  Scale,
  Server,
  Settings,
  Shield,
  Users,
} from "lucide-react";
import type { MessageKey } from "@/i18n";

export type NavGate = "models" | "operations" | "audit" | "teams" | "templates" | "approvals";

export type ActenoraNavLink = {
  to: string;
  icon: LucideIcon;
  labelKey: MessageKey;
  gate?: NavGate;
};

export type ActenoraNavGroup = {
  titleKey: MessageKey;
  links: ActenoraNavLink[];
};

export const ACTENORA_NAV_GROUPS: ActenoraNavGroup[] = [
  {
    titleKey: "actenora.nav.workspace",
    links: [
      { to: "/", icon: LayoutDashboard, labelKey: "nav.dashboard" },
      { to: "/approvals", icon: ClipboardCheck, labelKey: "nav.approvals", gate: "approvals" },
      { to: "/meetings", icon: Mic, labelKey: "nav.meetings" },
      { to: "/decisions", icon: Scale, labelKey: "nav.decisions" },
      { to: "/actions", icon: CheckSquare, labelKey: "nav.actions" },
      { to: "/commitments", icon: Handshake, labelKey: "nav.commitments" },
      { to: "/jobs", icon: Activity, labelKey: "nav.jobs" },
    ],
  },
  {
    titleKey: "actenora.nav.administration",
    links: [
      { to: "/templates", icon: FileText, labelKey: "nav.templates", gate: "templates" },
      { to: "/teams", icon: Users, labelKey: "nav.teams", gate: "teams" },
      { to: "/models", icon: Server, labelKey: "nav.models", gate: "models" },
      { to: "/operations", icon: Settings, labelKey: "nav.operations", gate: "operations" },
      { to: "/audit", icon: Shield, labelKey: "nav.audit", gate: "audit" },
    ],
  },
];

/** @deprecated use ACTENORA_NAV_GROUPS */
export const ACTENORA_NAV_LINKS: ActenoraNavLink[] = ACTENORA_NAV_GROUPS.flatMap((g) => g.links);

export const ACTENORA_LEDGER_LINKS: ActenoraNavLink[] = [
  { to: "/decisions", icon: Scale, labelKey: "nav.decisions" },
  { to: "/actions", icon: CheckSquare, labelKey: "nav.actions" },
  { to: "/commitments", icon: ClipboardList, labelKey: "nav.commitments" },
];
