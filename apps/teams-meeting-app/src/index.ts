import { createServer } from "node:http";
import { pathToFileURL } from "node:url";
import { formatLog } from "@actenora/observability";
import { buildSurfaceViewModel, renderSurfaceHtml } from "./surfaces/surfaces.js";
import type { TeamsSurface } from "./domain/types.js";

const port = Number(process.env.TEAMS_APP_PORT ?? 3978);

const SURFACE_ROUTES: Record<string, TeamsSurface> = {
  "/surfaces/details-tab": "details-tab",
  "/surfaces/side-panel": "side-panel",
  "/surfaces/chat-tab": "chat-tab",
};

export function createAppServer() {
  return createServer((req, res) => {
    const url = new URL(req.url ?? "/", "http://127.0.0.1");

    if (url.pathname === "/health") {
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ status: "UP", service: "teams-meeting-app" }));
      return;
    }

    const surface = SURFACE_ROUTES[url.pathname];
    if (surface) {
      const meetingId = url.searchParams.get("meetingId") ?? "unknown";
      const html = renderSurfaceHtml(buildSurfaceViewModel(surface, meetingId));
      res.writeHead(200, { "content-type": "text/html; charset=utf-8" });
      res.end(html);
      return;
    }

    if (url.pathname === "/api/surfaces") {
      res.writeHead(200, { "content-type": "application/json" });
      res.end(
        JSON.stringify({
          surfaces: ["details-tab", "side-panel", "chat-tab"],
          features: [
            "agenda",
            "open-tasks",
            "decision-marker",
            "action-marker",
            "risk-marker",
            "question-marker",
            "important-marker",
            "shared-note",
            "private-note",
          ],
          security: {
            trustsTeamsContextAlone: false,
            requiresBackendToken: true,
            privateNotesOwnerOnly: true,
            aiRequiresExplicitConsent: true,
          },
        }),
      );
      return;
    }

    res.writeHead(404, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "not_found" }));
  });
}

const isMain =
  process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href;

if (isMain) {
  const server = createAppServer();
  server.listen(port, "127.0.0.1", () => {
    console.log(formatLog("teams-meeting-app", "INFO", "listening", { port }));
  });
}
