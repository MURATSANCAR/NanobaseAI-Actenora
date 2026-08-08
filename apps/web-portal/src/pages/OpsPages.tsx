import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import { StubBanner } from "@/components/admin/StubBanner";
import { useAuth } from "@/auth/AuthProvider";
import { MetricCard } from "@/components/qa/MetricCard";
import { PageShell } from "@/components/qa/PageShell";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { AsyncState, DataTable, PaginationBar } from "@/components/ui/AsyncState";
import { InlineFeedback, type FeedbackMessage } from "@/components/ui/InlineFeedback";
import { useI18n } from "@/i18n";
import { sanitizeProductCopy } from "@/lib/brandSanitize";
import type { EmbeddingConnectionTestResult, TeamsConnectionTestResult } from "@/api/types";
import { AlertTriangle, Layers } from "lucide-react";

const GRAPH_APP_PERMISSIONS = [
  "OnlineMeetings.Read.All",
  "OnlineMeetingTranscript.Read.All",
  "Calendars.Read",
  "Mail.Send",
  "User.Read.All",
] as const;

const ENTRA_ADMIN_URL = "https://entra.microsoft.com";

export function TemplateStudioPage() {
  const api = useApi();
  const auth = useAuth();
  const queryClient = useQueryClient();
  const { t, tb } = useI18n();
  const [name, setName] = useState("");
  const [locale, setLocale] = useState("en");
  const [savedMessage, setSavedMessage] = useState<FeedbackMessage | null>(null);
  const q = useQuery({
    queryKey: queryKeys.templates,
    queryFn: () => api.listTemplates(),
    enabled: auth.nav("templates"),
  });
  const createMutation = useMutation({
    mutationFn: () => api.createTemplate({ name: name.trim(), locale }),
    onSuccess: async () => {
      setSavedMessage({ text: t("admin.templateSaved"), tone: "success" });
      setName("");
      await queryClient.invalidateQueries({ queryKey: queryKeys.templates });
    },
    onError: () => setSavedMessage({ text: t("admin.savePendingBackend"), tone: "error" }),
  });
  const defaultMutation = useMutation({
    mutationFn: (templateId: string) => api.setDefaultTemplate(templateId),
    onSuccess: async () => {
      setSavedMessage({ text: t("templates.default.updated"), tone: "success" });
      await queryClient.invalidateQueries({ queryKey: queryKeys.templates });
    },
    onError: () => setSavedMessage({ text: t("templates.default.requiresPublished"), tone: "error" }),
  });
  const status =
    q.isLoading ? "loading" : q.isError ? "error" : !q.data?.items.length ? "empty" : "ready";

  if (!auth.isLoading && !auth.nav("templates")) {
    return <Navigate to="/" replace />;
  }

  return (
    <PageShell titleKey="templates.title" subtitleKey="templates.description" maxWidth="max-w-7xl">
      <div className="mb-4">
        <Link to="/templates/mockups" className="btn-secondary text-sm">
          {t("templates.mockups.open")}
        </Link>
      </div>
      {q.data?.compositionStub ? <StubBanner featureKey="templates" /> : null}
      {auth.nav("templates") ? (
        <div className="card-static mb-4 space-y-3 p-4">
          <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">{t("admin.createTemplate")}</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block">
              <span className="label-text">{t("table.title")}</span>
              <input className="input-field" value={name} onChange={(e) => setName(e.target.value)} />
            </label>
            <label className="block">
              <span className="label-text">{t("locale.label")}</span>
              <select className="input-field" value={locale} onChange={(e) => setLocale(e.target.value)}>
                <option value="en">{t("locale.en")}</option>
                <option value="tr">{t("locale.tr")}</option>
              </select>
            </label>
          </div>
          <button
            type="button"
            className="btn-primary"
            disabled={!name.trim() || createMutation.isPending}
            onClick={() => createMutation.mutate()}
          >
            {t("admin.saveTemplate")}
          </button>
          <InlineFeedback message={savedMessage} />
        </div>
      ) : null}
      <AsyncState status={status} error={q.error} emptyTitle={t("async.empty")} emptyDescription={t("templates.description")}>
        <p className="mb-3 text-sm text-slate-600">{t("templates.default.explainer")}</p>
        <DataTable
          headers={[t("table.title"), t("locale.label"), t("filter.status"), t("templates.default.column")]}
          rows={
            q.data?.items.map((item) => [
              <Link key={`${item.id}-name`} to={`/templates/${item.id}`} className="font-semibold text-violet-800 hover:underline">
                {item.name}
              </Link>,
              item.locale === "tr" ? t("locale.tr") : item.locale === "en" ? t("locale.en") : item.locale,
              <StatusBadge key={item.id} label={tb("artifactStatus", item.status)} status={item.status} />,
              item.isDefault ? (
                <StatusBadge key={`${item.id}-default`} label={t("templates.default.badge")} status="APPROVED" />
              ) : (
                <button
                  key={`${item.id}-default`}
                  type="button"
                  className="btn-secondary text-xs"
                  disabled={
                    !auth.nav("templates") ||
                    item.status !== "PUBLISHED" ||
                    defaultMutation.isPending
                  }
                  onClick={() => defaultMutation.mutate(item.id)}
                >
                  {t("templates.default.action")}
                </button>
              ),
            ]) ?? []
          }
        />
      </AsyncState>
    </PageShell>
  );
}

function asyncStatusFromQuery(loading: boolean, error: boolean, empty: boolean) {
  return loading ? "loading" : error ? "error" : empty ? "empty" : "ready";
}

export function TeamsSettingsPage() {
  const auth = useAuth();
  const api = useApi();
  const queryClient = useQueryClient();
  const { t, tb } = useI18n();
  const isAdmin = auth.user?.role === "ADMIN";

  const q = useQuery({
    queryKey: queryKeys.teams,
    queryFn: () => api.getTeamsSettings(),
    enabled: auth.nav("teams"),
  });
  const conn = useQuery({
    queryKey: queryKeys.teamsConnection,
    queryFn: () => api.getTeamsConnection(),
    enabled: auth.nav("teams") && isAdmin,
  });

  const [form, setForm] = useState({
    authMode: "CERTIFICATE",
    tenantId: "",
    clientId: "",
    clientSecret: "",
    certificatePemPath: "",
    privateKeyPemPath: "",
    graphBaseUrl: "https://graph.microsoft.com",
    authorityHost: "https://login.microsoftonline.com",
    scope: "https://graph.microsoft.com/.default",
    defaultMailboxUserId: "",
    enabled: true,
  });
  const [message, setMessage] = useState<FeedbackMessage | null>(null);
  const [testResult, setTestResult] = useState<TeamsConnectionTestResult | null>(null);

  useEffect(() => {
    const d = conn.data;
    if (!d) return;
    setForm({
      authMode: d.authMode || "CERTIFICATE",
      tenantId: d.tenantId ?? "",
      clientId: d.clientId ?? "",
      clientSecret: "",
      certificatePemPath: d.certificatePemPath ?? "",
      privateKeyPemPath: d.privateKeyPemPath ?? "",
      graphBaseUrl: d.graphBaseUrl || "https://graph.microsoft.com",
      authorityHost: d.authorityHost || "https://login.microsoftonline.com",
      scope: d.scope || "https://graph.microsoft.com/.default",
      defaultMailboxUserId: d.defaultMailboxUserId ?? "",
      enabled: d.enabled,
    });
  }, [conn.data]);

  const autoJoinMutation = useMutation({
    mutationFn: (autoJoinEnabled: boolean) => api.updateTeamsSettings({ autoJoinEnabled }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.teams });
    },
  });
  const saveMutation = useMutation({
    mutationFn: () =>
      api.updateTeamsConnection({
        enabled: form.enabled,
        graphBaseUrl: form.graphBaseUrl.trim(),
        authorityHost: form.authorityHost.trim(),
        tenantId: form.tenantId.trim(),
        clientId: form.clientId.trim(),
        scope: form.scope.trim(),
        authMode: form.authMode,
        clientSecret: form.clientSecret, // blank keeps the stored secret
        certificatePemPath: form.certificatePemPath.trim(),
        privateKeyPemPath: form.privateKeyPemPath.trim(),
        defaultMailboxUserId: form.defaultMailboxUserId.trim(),
      }),
    onSuccess: async () => {
      setMessage({ text: t("teams.saved"), tone: "success" });
      setForm((f) => ({ ...f, clientSecret: "" }));
      await queryClient.invalidateQueries({ queryKey: queryKeys.teamsConnection });
    },
    onError: (err: Error) => setMessage({ text: err.message || t("teams.saveFailed"), tone: "error" }),
  });
  const testMutation = useMutation({
    mutationFn: () => api.testTeamsConnection(),
    onSuccess: (data) => {
      setTestResult(data);
      setMessage({ text: data.healthy ? t("teams.test.ok") : t("teams.test.failed"), tone: data.healthy ? "success" : "error" });
    },
    onError: (err: Error) => {
      setTestResult(null);
      setMessage({ text: err.message || t("teams.test.failed"), tone: "error" });
    },
  });

  if (!auth.isLoading && !auth.nav("teams")) {
    return <Navigate to="/" replace />;
  }

  const status = q.isLoading ? "loading" : q.isError ? "error" : !q.data ? "empty" : "ready";
  const upd = (key: keyof typeof form, value: string | boolean) =>
    setForm((f) => ({ ...f, [key]: value }) as typeof f);
  const isCertificate = form.authMode === "CERTIFICATE";
  const moduleAvailable = conn.data ? conn.data.available : true;
  const canEdit = isAdmin && moduleAvailable;

  return (
    <PageShell titleKey="teams.title" subtitleKey="teams.description" maxWidth="max-w-4xl">
      {!q.data?.tenantConnected && q.data?.compositionStub ? <StubBanner featureKey="teams" /> : null}
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
          <div className="card-static grid gap-4 p-5 sm:grid-cols-2">
            <div>
              <p className="label-text">{t("teams.tenantConnected")}</p>
              <p className="text-sm font-semibold text-slate-800">
                {q.data.tenantConnected ? t("common.yes") : t("common.no")}
              </p>
            </div>
            <div>
              <p className="label-text">{t("teams.graphApp")}</p>
              <p className="break-all text-sm font-semibold text-slate-800">{q.data.graphAppId}</p>
            </div>
            <div>
              <p className="label-text">{t("teams.webhook")}</p>
              <StatusBadge label={tb("webhookStatus", q.data.webhookStatus)} status={q.data.webhookStatus} />
            </div>
            <div>
              <p className="label-text">{t("teams.autoJoin")}</p>
              <p className="text-sm font-semibold text-slate-800">
                {q.data.autoJoinEnabled ? t("common.enabled") : t("common.disabled")}
              </p>
            </div>
          </div>

          {isAdmin ? (
            <div className="card-static mt-4 space-y-4 p-5" data-testid="teams-connection">
              <div>
                <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">{t("teams.connection.section")}</h2>
                <p className="mt-1 text-sm text-slate-600">{t("teams.connection.description")}</p>
              </div>
              {!moduleAvailable ? (
                <p className="rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-800">
                  {t("teams.connection.disabledNote")}
                </p>
              ) : null}

              <div className="grid gap-3 sm:grid-cols-2">
                <label className="block">
                  <span className="label-text">{t("teams.field.authMode")}</span>
                  <select
                    className="input-field"
                    value={form.authMode}
                    disabled={!canEdit}
                    onChange={(e) => upd("authMode", e.target.value)}
                  >
                    <option value="CERTIFICATE">{t("teams.field.authModeCertificate")}</option>
                    <option value="CLIENT_SECRET">{t("teams.field.authModeSecret")}</option>
                  </select>
                </label>
                <label className="flex items-center gap-2 self-end text-sm text-slate-700">
                  <input
                    type="checkbox"
                    checked={form.enabled}
                    disabled={!canEdit}
                    onChange={(e) => upd("enabled", e.target.checked)}
                  />
                  {t("teams.field.enabled")}
                </label>
                <label className="block">
                  <span className="label-text">{t("teams.field.tenantId")}</span>
                  <input
                    className="input-field font-mono text-sm"
                    value={form.tenantId}
                    disabled={!canEdit}
                    autoComplete="off"
                    placeholder="00000000-0000-0000-0000-000000000000"
                    onChange={(e) => upd("tenantId", e.target.value)}
                  />
                </label>
                <label className="block">
                  <span className="label-text">{t("teams.field.clientId")}</span>
                  <input
                    className="input-field font-mono text-sm"
                    value={form.clientId}
                    disabled={!canEdit}
                    autoComplete="off"
                    placeholder="00000000-0000-0000-0000-000000000000"
                    onChange={(e) => upd("clientId", e.target.value)}
                  />
                </label>

                {isCertificate ? (
                  <>
                    <label className="block">
                      <span className="label-text">{t("teams.field.certificatePath")}</span>
                      <input
                        className="input-field font-mono text-sm"
                        value={form.certificatePemPath}
                        disabled={!canEdit}
                        autoComplete="off"
                        placeholder="/etc/actenora/graph-cert.pem"
                        onChange={(e) => upd("certificatePemPath", e.target.value)}
                      />
                    </label>
                    <label className="block">
                      <span className="label-text">{t("teams.field.keyPath")}</span>
                      <input
                        className="input-field font-mono text-sm"
                        value={form.privateKeyPemPath}
                        disabled={!canEdit}
                        autoComplete="off"
                        placeholder="/etc/actenora/graph-key.pem"
                        onChange={(e) => upd("privateKeyPemPath", e.target.value)}
                      />
                    </label>
                  </>
                ) : (
                  <label className="block sm:col-span-2">
                    <span className="label-text">{t("teams.field.clientSecret")}</span>
                    <input
                      className="input-field font-mono text-sm"
                      type="password"
                      value={form.clientSecret}
                      disabled={!canEdit}
                      autoComplete="new-password"
                      placeholder={t("teams.field.clientSecretKeep")}
                      onChange={(e) => upd("clientSecret", e.target.value)}
                    />
                    <span className="mt-1 block text-xs text-slate-500">
                      {conn.data?.secretConfigured ? t("teams.field.clientSecretSet") : t("teams.field.clientSecretNone")}
                    </span>
                  </label>
                )}

                <label className="block">
                  <span className="label-text">{t("teams.field.mailbox")}</span>
                  <input
                    className="input-field font-mono text-sm"
                    value={form.defaultMailboxUserId}
                    disabled={!canEdit}
                    autoComplete="off"
                    placeholder="meetings@contoso.com"
                    onChange={(e) => upd("defaultMailboxUserId", e.target.value)}
                  />
                </label>
                <label className="block">
                  <span className="label-text">{t("teams.field.scope")}</span>
                  <input
                    className="input-field font-mono text-sm"
                    value={form.scope}
                    disabled={!canEdit}
                    autoComplete="off"
                    onChange={(e) => upd("scope", e.target.value)}
                  />
                </label>
                <label className="block">
                  <span className="label-text">{t("teams.field.graphBaseUrl")}</span>
                  <input
                    className="input-field font-mono text-sm"
                    value={form.graphBaseUrl}
                    disabled={!canEdit}
                    autoComplete="off"
                    onChange={(e) => upd("graphBaseUrl", e.target.value)}
                  />
                </label>
                <label className="block">
                  <span className="label-text">{t("teams.field.authorityHost")}</span>
                  <input
                    className="input-field font-mono text-sm"
                    value={form.authorityHost}
                    disabled={!canEdit}
                    autoComplete="off"
                    onChange={(e) => upd("authorityHost", e.target.value)}
                  />
                </label>
              </div>

              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  className="btn-primary"
                  disabled={!canEdit || saveMutation.isPending}
                  onClick={() => saveMutation.mutate()}
                >
                  {t("teams.action.save")}
                </button>
                <button
                  type="button"
                  className="btn-secondary"
                  disabled={!canEdit || testMutation.isPending}
                  onClick={() => testMutation.mutate()}
                >
                  {t("teams.action.test")}
                </button>
              </div>
              <InlineFeedback message={message} />
              {testResult ? (
                <div className="flex flex-col gap-1 rounded-md border border-slate-200 bg-slate-50 p-3">
                  <div className="flex items-center gap-2">
                    <StatusBadge
                      label={testResult.healthy ? t("teams.test.ok") : t("teams.test.failed")}
                      status={testResult.healthy ? "healthy" : "failed"}
                    />
                    <span className="text-xs text-slate-500">
                      {t("teams.test.latency")}: {testResult.latencyMs} ms
                    </span>
                  </div>
                  <p className="text-xs text-slate-600">{testResult.detail}</p>
                </div>
              ) : null}
            </div>
          ) : null}

          {isAdmin ? <TeamsSetupGuide /> : null}

          <div className="card-static mt-4 space-y-3 p-4">
            <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">{t("admin.teamsPreferences")}</h2>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={q.data.autoJoinEnabled}
                disabled={autoJoinMutation.isPending || !isAdmin}
                onChange={(e) => autoJoinMutation.mutate(e.target.checked)}
              />
              {t("teams.autoJoin")}
            </label>
            {autoJoinMutation.isError ? (
              <p className="text-xs text-amber-800">{t("admin.savePendingBackend")}</p>
            ) : null}
          </div>
          </>
        ) : null}
      </AsyncState>
    </PageShell>
  );
}

function TeamsSetupGuide() {
  const { t } = useI18n();
  const steps: Array<{ title: string; body: string; withPermissions?: boolean }> = [
    { title: t("teams.guide.step1Title"), body: t("teams.guide.step1Body") },
    { title: t("teams.guide.step2Title"), body: t("teams.guide.step2Body") },
    { title: t("teams.guide.step3Title"), body: t("teams.guide.step3Body"), withPermissions: true },
    { title: t("teams.guide.step4Title"), body: t("teams.guide.step4Body") },
  ];
  return (
    <details className="card-static mt-4 p-5">
      <summary className="cursor-pointer text-sm font-bold uppercase tracking-wide text-slate-500">
        {t("teams.guide.title")}
      </summary>
      <div className="mt-3 space-y-4">
        <p className="text-sm text-slate-600">{t("teams.guide.intro")}</p>
        <a
          href={ENTRA_ADMIN_URL}
          target="_blank"
          rel="noreferrer"
          className="btn-secondary inline-block text-sm"
        >
          {t("teams.guide.openEntra")}
        </a>
        <ol className="space-y-3">
          {steps.map((step) => (
            <li key={step.title} className="border-l-2 border-violet-200 pl-3">
              <p className="text-sm font-semibold text-slate-800">{step.title}</p>
              <p className="mt-1 text-sm text-slate-600">{step.body}</p>
              {step.withPermissions ? (
                <div className="mt-2">
                  <p className="text-xs font-semibold text-slate-500">{t("teams.guide.permissionsLabel")}</p>
                  <div className="mt-1 flex flex-wrap gap-1.5">
                    {GRAPH_APP_PERMISSIONS.map((perm) => (
                      <code
                        key={perm}
                        className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-700"
                      >
                        {perm}
                      </code>
                    ))}
                  </div>
                </div>
              ) : null}
            </li>
          ))}
        </ol>
      </div>
    </details>
  );
}

export function ModelManagementPage() {
  const auth = useAuth();
  const api = useApi();
  const queryClient = useQueryClient();
  const { t, tb, locale } = useI18n();
  const [baseUrl, setBaseUrl] = useState("http://127.0.0.1:8000");
  const [enabled, setEnabled] = useState(true);
  const [message, setMessage] = useState<FeedbackMessage | null>(null);
  const [embBaseUrl, setEmbBaseUrl] = useState("");
  const [embModelId, setEmbModelId] = useState("");
  const [embApiKey, setEmbApiKey] = useState("");
  const [embMessage, setEmbMessage] = useState<FeedbackMessage | null>(null);
  const [embResult, setEmbResult] = useState<EmbeddingConnectionTestResult | null>(null);
  const [jobsCursor, setJobsCursor] = useState<string | undefined>();
  const jobsParams = useMemo(() => ({ cursor: jobsCursor, limit: 25 }), [jobsCursor]);

  const connection = useQuery({
    queryKey: queryKeys.intelligence,
    queryFn: () => api.getNanobaseAiConnection(),
    enabled: auth.nav("models"),
  });
  const embedding = useQuery({
    queryKey: queryKeys.embeddingConnection,
    queryFn: () => api.getEmbeddingConnection(),
    enabled: auth.nav("models"),
  });
  const q = useQuery({
    queryKey: queryKeys.models,
    queryFn: () => api.getModelHealth(),
    enabled: auth.nav("models"),
  });
  const jobsQuery = useQuery({
    queryKey: queryKeys.jobs(jobsParams),
    queryFn: () => api.listAiJobs(jobsParams),
    enabled: auth.nav("models"),
  });
  const jobsStatus = asyncStatusFromQuery(
    jobsQuery.isLoading,
    jobsQuery.isError,
    !jobsQuery.data?.items.length,
  );

  useEffect(() => {
    if (connection.data) {
      setBaseUrl(connection.data.baseUrl);
      setEnabled(connection.data.enabled);
    }
  }, [connection.data]);
  useEffect(() => {
    if (embedding.data) {
      setEmbBaseUrl(embedding.data.baseUrl);
      setEmbModelId(embedding.data.modelId);
    }
  }, [embedding.data]);

  const saveMutation = useMutation({
    mutationFn: () =>
      api.updateNanobaseAiConnection({
        baseUrl: baseUrl.trim(),
        enabled,
      }),
    onSuccess: async () => {
      setMessage({ text: t("intelligence.saved"), tone: "success" });
      await queryClient.invalidateQueries({ queryKey: queryKeys.intelligence });
    },
    onError: (err: Error) => setMessage({ text: err.message || t("intelligence.saveFailed"), tone: "error" }),
  });
  const testMutation = useMutation({
    mutationFn: () => api.testNanobaseAiConnection(),
    onSuccess: async (data) => {
      setMessage(
        data.healthy
          ? { text: t("intelligence.testOk"), tone: "success" }
          : { text: t("intelligence.testFailed"), tone: "error" },
      );
      await queryClient.invalidateQueries({ queryKey: queryKeys.intelligence });
    },
    onError: (err: Error) => setMessage({ text: err.message || t("intelligence.testFailed"), tone: "error" }),
  });
  const embTestMutation = useMutation({
    mutationFn: () =>
      api.testEmbeddingConnection({
        baseUrl: embBaseUrl.trim() || undefined,
        modelId: embModelId.trim() || undefined,
        apiKey: embApiKey.trim() || undefined,
      }),
    onSuccess: (data) => {
      setEmbResult(data);
      setEmbMessage(
        data.healthy
          ? { text: t("embedding.testOk"), tone: "success" }
          : { text: t("embedding.testFailed"), tone: "error" },
      );
    },
    onError: (err: Error) =>
      setEmbMessage({ text: err.message || t("embedding.testFailed"), tone: "error" }),
  });

  if (!auth.isLoading && !auth.nav("models")) {
    return <Navigate to="/" replace />;
  }

  const status = q.isLoading ? "loading" : q.isError ? "error" : !q.data ? "empty" : "ready";

  return (
    <PageShell titleKey="models.title" subtitleKey="models.description" maxWidth="max-w-7xl">
      <div className="card-static mb-6 space-y-4 p-5" data-testid="nanobaseai-connection">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">
              {t("intelligence.section")}
            </h2>
            <p className="mt-1 text-sm text-slate-600">{t("intelligence.description")}</p>
          </div>
          {connection.data ? (
            <StatusBadge
              label={
                connection.data.healthy
                  ? t("intelligence.healthy")
                  : t("intelligence.unreachable")
              }
              status={connection.data.healthy ? "healthy" : "failed"}
            />
          ) : null}
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block sm:col-span-2">
            <span className="label-text">{t("intelligence.endpoint")}</span>
            <input
              className="input-field font-mono text-sm"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              placeholder="http://127.0.0.1:8000"
              autoComplete="off"
            />
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
            />
            {t("intelligence.enabled")}
          </label>
          <div className="text-sm text-slate-600">
            <span className="label-text">{t("intelligence.host")}</span>
            <p className="font-mono text-xs">{connection.data?.endpointHost ?? "—"}</p>
            {connection.data ? (
              <p className="mt-1 text-xs text-slate-500">
                {t("intelligence.latency")}: {connection.data.latencyMs} ms · {connection.data.mode}
              </p>
            ) : null}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="btn-primary"
            disabled={saveMutation.isPending || !baseUrl.trim()}
            onClick={() => saveMutation.mutate()}
          >
            {t("intelligence.save")}
          </button>
          <button
            type="button"
            className="btn-secondary"
            disabled={testMutation.isPending}
            onClick={() => testMutation.mutate()}
          >
            {t("intelligence.test")}
          </button>
        </div>
        <InlineFeedback message={message} />
        {connection.data?.statusDetail ? (
          <p className="text-xs text-slate-500">{connection.data.statusDetail}</p>
        ) : null}
      </div>

      <div className="card-static mb-6 space-y-4 p-5" data-testid="embedding-connection">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">
              {t("embedding.section")}
            </h2>
            <p className="mt-1 text-sm text-slate-600">{t("embedding.description")}</p>
          </div>
          {embResult ? (
            <StatusBadge
              label={embResult.healthy ? t("embedding.healthy") : t("embedding.unreachable")}
              status={embResult.healthy ? "healthy" : "failed"}
            />
          ) : embedding.data ? (
            <StatusBadge
              label={
                embedding.data.configured
                  ? t("embedding.configured")
                  : t("embedding.notConfigured")
              }
              status={embedding.data.configured ? "healthy" : "pending"}
            />
          ) : null}
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block sm:col-span-2">
            <span className="label-text">{t("embedding.endpoint")}</span>
            <input
              className="input-field font-mono text-sm"
              value={embBaseUrl}
              onChange={(e) => setEmbBaseUrl(e.target.value)}
              placeholder="http://127.0.0.1:8000"
              autoComplete="off"
            />
          </label>
          <label className="block">
            <span className="label-text">{t("embedding.model")}</span>
            <input
              className="input-field font-mono text-sm"
              value={embModelId}
              onChange={(e) => setEmbModelId(e.target.value)}
              placeholder="bge-m3"
              autoComplete="off"
            />
          </label>
          <label className="block">
            <span className="label-text">{t("embedding.apiKey")}</span>
            <input
              type="password"
              className="input-field font-mono text-sm"
              value={embApiKey}
              onChange={(e) => setEmbApiKey(e.target.value)}
              placeholder={embedding.data?.apiKeyConfigured ? "••••••" : ""}
              autoComplete="off"
            />
          </label>
          <div className="text-sm text-slate-600 sm:col-span-2">
            <span className="label-text">{t("embedding.dimensions")}</span>
            <p className="font-mono text-xs">
              {embedding.data?.dimensions ?? "—"}
              {embResult ? ` · ${t("embedding.latency")}: ${embResult.latencyMs} ms` : ""}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="btn-primary"
            disabled={embTestMutation.isPending || !embBaseUrl.trim()}
            onClick={() => embTestMutation.mutate()}
          >
            {t("embedding.test")}
          </button>
        </div>
        <InlineFeedback message={embMessage} />
        {embResult?.detail ? (
          <p className="text-xs text-slate-500">{embResult.detail}</p>
        ) : null}
        <p className="text-xs text-slate-400">{t("embedding.applyHint")}</p>
      </div>

      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
            {!q.data.models.length ? (
              q.data.compositionStub ? (
                <StubBanner featureKey="models" />
              ) : connection.data?.healthy ? (
                <div className="card-static border-slate-200 bg-slate-50 p-4 text-sm text-slate-700">
                  <p className="font-semibold">{t("models.registryEmptyTitle")}</p>
                  <p className="mt-1 text-xs text-slate-600">{t("models.registryEmptyBody")}</p>
                </div>
              ) : null
            ) : null}
            <div className="space-y-3">
              <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">{t("models.sectionModels")}</h2>
              <DataTable
                headers={[t("table.name"), t("table.key"), t("filter.status")]}
                rows={q.data.models.map((m) => [
                  sanitizeProductCopy(m.displayName),
                  <span key={m.modelKey} className="text-xs text-slate-500">
                    {sanitizeProductCopy(m.modelKey)}
                  </span>,
                  <StatusBadge
                    key={`${m.modelKey}-s`}
                    label={`${m.enabled ? t("models.enabled") : t("models.disabled")} · ${tb("artifactStatus", m.status)}`}
                    status={m.status}
                  />,
                ])}
              />
            </div>
            <div className="space-y-3">
              <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">{t("models.sectionDeployments")}</h2>
              <DataTable
                headers={[t("table.deployment"), t("table.node"), t("table.health")]}
                rows={q.data.deployments.map((d) => [
                  sanitizeProductCopy(d.deploymentKey),
                  sanitizeProductCopy(d.nodeName),
                  <StatusBadge
                    key={d.deploymentKey}
                    label={d.healthy ? t("common.healthy") : t("common.unhealthy")}
                    status={d.healthy ? "healthy" : "failed"}
                  />,
                ])}
              />
            </div>
            {auth.canSeeRouting ? (
              <div className="card-static space-y-3 p-5" data-testid="routing-detail">
                <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">{t("models.sectionRouting")}</h2>
                <p className="text-sm text-slate-600">
                  {t("models.strategy")}: <strong>{q.data.routing.strategy}</strong>
                </p>
                <DataTable
                  headers={[t("table.role"), t("table.primary"), t("models.fallback")]}
                  rows={q.data.routing.roles.map((r) => [r.role, r.primaryModel, r.fallbackModel])}
                />
              </div>
            ) : (
              <p className="card-static p-4 text-sm text-slate-500" data-testid="routing-hidden">
                {t("models.routingHidden")}
              </p>
            )}
          </>
        ) : null}
      </AsyncState>

      <div className="mt-8 space-y-3" data-testid="models-recent-processing">
        <div>
          <h2 className="font-display text-lg font-semibold tracking-tight text-slate-900">
            {t("models.sectionJobs")}
          </h2>
          <p className="mt-0.5 text-sm font-medium text-teal-800">{t("jobs.description")}</p>
        </div>
        {jobsQuery.data?.compositionStub ? <StubBanner featureKey="jobs" /> : null}
        <AsyncState
          status={jobsStatus}
          error={jobsQuery.error}
          emptyTitle={t("jobs.emptyTitle")}
          emptyDescription={t("jobs.emptyBody")}
        >
          <div className="card-static divide-y divide-white/60">
            {jobsQuery.data?.items.map((j) => {
              const meetingLabel = j.meetingTitle?.trim() || t("jobs.meetingUnknown");
              return (
                <div key={j.id} className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:gap-3">
                  <div className="min-w-0 flex-1">
                    <Link
                      to={`/meetings/${j.meetingId}`}
                      className="block truncate font-semibold text-slate-900 hover:text-teal-900 hover:underline"
                    >
                      {meetingLabel}
                    </Link>
                    <div className="mt-1.5 flex flex-wrap items-center gap-2 text-sm">
                      <span className="rounded-full bg-violet-50 px-2.5 py-0.5 text-xs font-semibold text-violet-800 ring-1 ring-violet-200/80">
                        {tb("aiJobStage", j.stage)}
                      </span>
                      <StatusBadge label={tb("aiJobStatus", j.status)} status={j.status} />
                      <span className="text-xs text-slate-500">
                        {t("jobs.started")}{" "}
                        {new Date(j.startedAt).toLocaleString(locale === "tr" ? "tr-TR" : "en-US")}
                      </span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
          <PaginationBar
            nextCursor={jobsQuery.data?.nextCursor}
            onNext={() => setJobsCursor(jobsQuery.data?.nextCursor ?? undefined)}
            onReset={() => setJobsCursor(undefined)}
          />
        </AsyncState>
      </div>
    </PageShell>
  );
}

export function OperationsCenterPage() {
  const auth = useAuth();
  const api = useApi();
  const { t, tb } = useI18n();
  const q = useQuery({
    queryKey: queryKeys.operations,
    queryFn: () => api.getOperationsOverview(),
    enabled: auth.nav("operations"),
  });

  if (!auth.isLoading && !auth.nav("operations")) {
    return <Navigate to="/" replace />;
  }

  const status = q.isLoading ? "loading" : q.isError ? "error" : !q.data ? "empty" : "ready";

  return (
    <PageShell titleKey="operations.title" subtitleKey="operations.description" maxWidth="max-w-7xl">
      <AsyncState status={status} error={q.error}>
        {q.data ? (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <MetricCard label={t("operations.queueDepth")} value={q.data.queueDepth} icon={Layers} />
              <MetricCard
                label={t("operations.failedJobs")}
                value={q.data.failedJobs}
                icon={AlertTriangle}
                tone="text-status-fail"
              />
            </div>
            <div className="card-static p-5">
              <h2 className="mb-3 text-sm font-bold uppercase tracking-wide text-slate-500">
                {t("operations.circuitBreakers")}
              </h2>
              <ul className="space-y-2 text-sm">
                {q.data.circuitBreakers.map((c) => (
                  <li key={c.name} className="rounded-xl bg-white/50 px-3 py-2">
                    {c.name}: <span className="font-medium">{tb("circuitBreakerState", c.state)}</span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="card-static p-5">
              <h2 className="mb-3 text-sm font-bold uppercase tracking-wide text-slate-500">
                {t("operations.workers")}
              </h2>
              <ul className="space-y-2 text-sm">
                {q.data.workers.length ? q.data.workers.map((w) => (
                  <li key={w.name} className="rounded-xl bg-white/50 px-3 py-2">
                    {w.name}: <span className="font-medium">{tb("workerStatus", w.status)}</span>
                  </li>
                )) : (
                  <li className="rounded-xl bg-white/50 px-3 py-2 text-slate-500">{t("operations.workersEmpty")}</li>
                )}
              </ul>
            </div>
          </>
        ) : null}
      </AsyncState>
    </PageShell>
  );
}

export function AuditViewerPage() {
  const auth = useAuth();
  const api = useApi();
  const { t, tb } = useI18n();
  const [cursor, setCursor] = useState<string | undefined>();
  const params = useMemo(() => ({ cursor, limit: 25 }), [cursor]);
  const q = useQuery({
    queryKey: queryKeys.audit(params),
    queryFn: () => api.listAuditEvents(params),
    enabled: auth.nav("audit"),
  });

  if (!auth.isLoading && !auth.nav("audit")) {
    return <Navigate to="/" replace />;
  }

  const status = asyncStatusFromQuery(q.isLoading, q.isError, !q.data?.items.length);

  return (
    <PageShell titleKey="audit.title" subtitleKey="audit.description" maxWidth="max-w-7xl">
      {q.data?.compositionStub ? <StubBanner featureKey="audit" /> : null}
      <AsyncState status={status} error={q.error} emptyTitle={t("async.empty")} emptyDescription={t("audit.description")}>
        <DataTable
          headers={[t("table.action"), t("table.actor"), t("table.subject"), t("table.at")]}
          rows={
            q.data?.items.map((e) => [
              tb("auditAction", e.action),
              e.actorName,
              e.resourceLabel,
              <span key={`${e.id}-a`} className="text-slate-500">
                {new Date(e.at).toLocaleString()}
              </span>,
            ]) ?? []
          }
        />
        <PaginationBar
          nextCursor={q.data?.nextCursor}
          onNext={() => setCursor(q.data?.nextCursor ?? undefined)}
          onReset={() => setCursor(undefined)}
        />
      </AsyncState>
    </PageShell>
  );
}
