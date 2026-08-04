#!/usr/bin/env bash
# Build B_LINEAGE and A_LINEAGE evaluation jars (observability patch L on each base).
# Run from repo root on a machine with JDK 21 + Maven wrapper.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-$ROOT/.tools/jdk-21}"
export PATH="$JAVA_HOME/bin:$PATH"

OUT_DIR="${OUT_DIR:-$ROOT/artifacts/easymeeting-quality/lineage-rerun/jars}"
WT_ROOT="${WT_ROOT:-$ROOT/.worktrees-lineage}"
B_COMMIT=f9c699f4753d7017d64aede84c6ee7da056a5f66
A_COMMIT=472172a035da047189b292f7f6ee677c115963ad
mkdir -p "$OUT_DIR" "$WT_ROOT"

LINEAGE_SRC=(
  "modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/domain/pipeline/lineage"
)

copy_lineage_pkg() {
  local dest="$1"
  mkdir -p "$dest/modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/domain/pipeline"
  rm -rf "$dest/modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/domain/pipeline/lineage"
  cp -R "$ROOT/modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/domain/pipeline/lineage" \
    "$dest/modules/ai-processing/src/main/java/com/nanobaseai/actenora/aiprocessing/domain/pipeline/"
}

# .gitignore has a bare "out/" rule, so application/port/out/*.java are never in git
# and clean worktrees cannot compile without copying them from the local working tree.
copy_gitignored_port_out() {
  local dest="$1"
  local rel
  for rel in \
    modules/transcript/src/main/java/com/nanobaseai/actenora/transcript/application/port/out \
    modules/template/src/main/java/com/nanobaseai/actenora/template/application/port/out
  do
    if [[ -d "$ROOT/$rel" ]]; then
      mkdir -p "$dest/$(dirname "$rel")"
      rm -rf "$dest/$rel"
      cp -R "$ROOT/$rel" "$dest/$rel"
    fi
  done
}

# Apply observability overlay from CURRENT main onto a base worktree without replacing
# base extraction logic wholesale. Uses python injector for ActionPostProcessing + CrossType.
apply_L() {
  local dest="$1" label="$2"
  copy_lineage_pkg "$dest"
  python3 "$ROOT/scripts/lineage-rerun/inject-lineage-hooks.py" --root "$dest" --label "$label"
  # Config flag (idempotent)
  if ! grep -q 'lineage-recording-enabled' "$dest/apps/platform-backend/src/main/resources/application.yml"; then
    python3 - <<PY
from pathlib import Path
p=Path("$dest/apps/platform-backend/src/main/resources/application.yml")
t=p.read_text()
needle="finalization-failure-mode:"
if "lineage-recording-enabled" not in t and needle in t:
    # insert after finalization-failure-mode line
    lines=t.splitlines(True)
    out=[]
    for line in lines:
        out.append(line)
        if line.strip().startswith("finalization-failure-mode:"):
            out.append("      lineage-recording-enabled: \${ACTENORA_MEETING_LINEAGE_ENABLED:\${ACTENORA_AI_PIPELINE_LINEAGE_RECORDING:false}}\n")
    p.write_text("".join(out))
PY
  else
    sed -i.bak -E 's|lineage-recording-enabled:.*|lineage-recording-enabled: ${ACTENORA_MEETING_LINEAGE_ENABLED:${ACTENORA_AI_PIPELINE_LINEAGE_RECORDING:false}}|' \
      "$dest/apps/platform-backend/src/main/resources/application.yml" || true
  fi
}

prepare_worktree() {
  local name="$1" commit="$2"
  local dir="$WT_ROOT/$name"
  if [[ -d "$dir/.git" || -f "$dir/.git" ]]; then
    git -C "$dir" reset --hard "$commit" >/dev/null
    git -C "$dir" clean -fd >/dev/null
  else
    git worktree add --detach "$dir" "$commit" >/dev/null
  fi
  copy_gitignored_port_out "$dir"
  apply_L "$dir" "$name" >/dev/null
  printf '%s\n' "$dir"
}

build_one() {
  local dir="$1" jar_name="$2"
  (cd "$dir" && ./mvnw -pl apps/platform-backend -am package -DskipTests -q)
  local built
  built=$(find "$dir/apps/platform-backend/target" -maxdepth 1 -name 'platform-backend-*.jar' ! -name '*original*' | head -1)
  [[ -n "$built" && -f "$built" ]] || { echo "missing jar in $dir"; exit 1; }
  cp -f "$built" "$OUT_DIR/$jar_name"
  sha256sum "$OUT_DIR/$jar_name" | tee "$OUT_DIR/$jar_name.sha256"
}

echo "Preparing B worktree..."
B_DIR=$(prepare_worktree B_LINEAGE "$B_COMMIT")
echo "Preparing A worktree..."
A_DIR=$(prepare_worktree A_LINEAGE "$A_COMMIT")

echo "Building B_LINEAGE jar (this takes a while)..."
build_one "$B_DIR" "B_LINEAGE_f9c699f.jar"
echo "Building A_LINEAGE jar..."
build_one "$A_DIR" "A_LINEAGE_472172a.jar"

# manifests
python3 - <<PY
import hashlib, json, subprocess, datetime
from pathlib import Path
root=Path("$ROOT")
out=Path("$OUT_DIR")
def sha(p):
  h=hashlib.sha256();
  with open(p,'rb') as f:
    for chunk in iter(lambda:f.read(1<<20), b''): h.update(chunk)
  return h.hexdigest()
def file_sha(rel):
  p=root/rel
  return sha(p) if p.exists() else None
java=subprocess.check_output(["java","-version"],stderr=subprocess.STDOUT,text=True).splitlines()[0]
for name, base, full in (
  ("B_LINEAGE_f9c699f","f9c699f","$B_COMMIT"),
  ("A_LINEAGE_472172a","472172a","$A_COMMIT"),
):
  jar=out/f"{name}.jar"
  man={
    "buildName": name,
    "baseCommit": base,
    "baseCommitFull": full,
    "lineagePatchSource": "worktree-overlay-from-HEAD-lineage-package+inject-hooks",
    "lineagePatchNote": "Observability-only; extraction keep/drop logic from base commit retained",
    "builtAt": datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
    "javaVersion": java,
    "jarSha256": sha(jar),
    "fixtureSha256": file_sha("modules/ai-processing/src/test/resources/aiprocessing/eval/01_15dk_daily_standup.vtt"),
    "promptHashes": {
      "systemMeetingAnalystV2": file_sha("modules/ai-processing/src/main/resources/aiprocessing/prompts/system-meeting-analyst.v2.txt"),
      "chunkExtraction": file_sha("modules/ai-processing/src/main/resources/aiprocessing/prompts/chunk-extraction.v1.txt"),
      "editorialSummary": file_sha("modules/ai-processing/src/main/resources/aiprocessing/prompts/editorial-summary.v1.txt"),
    },
    "schemaHashes": {
      "extraction": file_sha("modules/ai-processing/src/main/resources/aiprocessing/schemas/extraction-output.schema.json"),
      "editorialSummary": file_sha("modules/ai-processing/src/main/resources/aiprocessing/schemas/editorial-summary.schema.json"),
    },
    "config": {
      "finalizationMode": "editorial",
      "gateThreshold": 4.5,
      "lineageEnv": "ACTENORA_MEETING_LINEAGE_ENABLED=true",
      "productionDeploy": False,
    }
  }
  (out/f"{name}.build.json").write_text(json.dumps(man, indent=2)+"\n")
  print("wrote", out/f"{name}.build.json")
PY

echo "DONE jars in $OUT_DIR"
ls -la "$OUT_DIR"
