import { createServer } from "node:http";
import { formatLog } from "@actenora/observability";

const port = Number(process.env.TEAMS_APP_PORT ?? 3978);

export function createAppServer() {
  return createServer((req, res) => {
    if (req.url === "/health") {
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ status: "UP", service: "teams-meeting-app" }));
      return;
    }
    res.writeHead(404, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "not_found" }));
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const server = createAppServer();
  server.listen(port, "127.0.0.1", () => {
    console.log(formatLog("teams-meeting-app", "INFO", "listening", { port }));
  });
}
