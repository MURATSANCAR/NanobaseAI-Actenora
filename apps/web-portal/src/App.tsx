import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { ApiProvider } from "./api/ApiProvider";
import type { ApiClient } from "./api/types";
import { AuthProvider } from "./auth/AuthProvider";
import { AppShell } from "./components/layout/AppShell";
import { DashboardPage } from "./pages/DashboardPage";
import {
  ActionCenterPage,
  CommitmentTrackerPage,
  DecisionLedgerPage,
} from "./pages/LedgerPages";
import { MeetingDetailPage } from "./pages/meeting/MeetingDetailPage";
import { MeetingListPage } from "./pages/MeetingListPage";
import {
  AiJobTimelinePage,
  AuditViewerPage,
  ModelManagementPage,
  OperationsCenterPage,
  TeamsSettingsPage,
  TemplateStudioPage,
} from "./pages/OpsPages";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export function App({ apiClient }: { apiClient?: ApiClient }) {
  return (
    <QueryClientProvider client={queryClient}>
      <ApiProvider client={apiClient}>
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              <Route element={<AppShell />}>
                <Route index element={<DashboardPage />} />
                <Route path="meetings" element={<MeetingListPage />} />
                <Route path="meetings/:meetingId" element={<MeetingDetailPage />} />
                <Route path="decisions" element={<DecisionLedgerPage />} />
                <Route path="actions" element={<ActionCenterPage />} />
                <Route path="commitments" element={<CommitmentTrackerPage />} />
                <Route path="templates" element={<TemplateStudioPage />} />
                <Route path="teams" element={<TeamsSettingsPage />} />
                <Route path="models" element={<ModelManagementPage />} />
                <Route path="jobs" element={<AiJobTimelinePage />} />
                <Route path="operations" element={<OperationsCenterPage />} />
                <Route path="audit" element={<AuditViewerPage />} />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </ApiProvider>
    </QueryClientProvider>
  );
}
