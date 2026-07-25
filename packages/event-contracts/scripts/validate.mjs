import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const root = dirname(fileURLToPath(import.meta.url));
const schemasDir = join(root, "..", "schemas");
const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);

const files = readdirSync(schemasDir).filter((f) => f.endsWith(".json"));
if (files.length === 0) {
  console.error("No event schemas found");
  process.exit(1);
}

for (const file of files) {
  const schema = JSON.parse(readFileSync(join(schemasDir, file), "utf8"));
  const validate = ajv.compile(schema);
  const sample = {
    eventType: "meeting.recorded.v1",
    eventId: "11111111-1111-4111-8111-111111111111",
    occurredAt: "2026-07-25T17:00:00.000Z",
    tenantId: "tenant-1",
    meetingId: "meeting-1",
  };
  if (file === "meeting.recorded.v1.json" && !validate(sample)) {
    console.error(file, validate.errors);
    process.exit(1);
  }
  console.log(`ok ${file}`);
}
