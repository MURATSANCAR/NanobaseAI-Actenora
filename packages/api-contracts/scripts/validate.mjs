import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const specPath = join(root, "..", "openapi", "platform-api.yaml");
const text = readFileSync(specPath, "utf8");

for (const required of ["openapi:", "paths:", "/api/health:", "HealthResponse:"]) {
  if (!text.includes(required)) {
    console.error(`Missing marker in OpenAPI: ${required}`);
    process.exit(1);
  }
}
console.log("ok platform-api.yaml");
