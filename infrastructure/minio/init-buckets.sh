#!/bin/sh
# MinIO bucket + tenant-prefix bootstrap (FAZ 2)
# Standard: docs/operations/TENANT-BUCKET-PREFIX.md
set -eu

MC_ALIAS="${MC_ALIAS:-local}"
ENDPOINT="${OBJECT_STORAGE_ENDPOINT:-http://minio:9000}"
ACCESS_KEY="${OBJECT_STORAGE_ACCESS_KEY:?OBJECT_STORAGE_ACCESS_KEY required}"
SECRET_KEY="${OBJECT_STORAGE_SECRET_KEY:?OBJECT_STORAGE_SECRET_KEY required}"
PRIMARY_BUCKET="${OBJECT_STORAGE_BUCKET:-actenora}"
TENANT_PREFIX_ROOT="${OBJECT_STORAGE_TENANT_PREFIX_ROOT:-tenants}"
TEST_TENANT_ID="${OBJECT_STORAGE_TEST_TENANT_ID:-tenant-local-test}"

echo "Waiting for MinIO at ${ENDPOINT}..."
i=0
until mc alias set "${MC_ALIAS}" "${ENDPOINT}" "${ACCESS_KEY}" "${SECRET_KEY}" >/dev/null 2>&1 \
  && mc ready "${MC_ALIAS}"; do
  i=$((i + 1))
  if [ "${i}" -ge 60 ]; then
    echo "MinIO not ready after 60s" >&2
    exit 1
  fi
  sleep 1
done

create_bucket() {
  bucket="$1"
  if mc ls "${MC_ALIAS}/${bucket}" >/dev/null 2>&1; then
    echo "Bucket exists: ${bucket}"
  else
    mc mb --ignore-existing "${MC_ALIAS}/${bucket}"
    echo "Created bucket: ${bucket}"
  fi
}

create_bucket "${PRIMARY_BUCKET}"
create_bucket "actenora-artifacts"
create_bucket "actenora-evidence"
create_bucket "actenora-transcripts"

# Seed tenant prefix markers (empty objects) for local test tenant
seed_prefix() {
  bucket="$1"
  prefix="$2"
  echo "tenant-prefix-seed" | mc pipe "${MC_ALIAS}/${bucket}/${prefix}.keep" >/dev/null
  echo "Seeded prefix: s3://${bucket}/${prefix}"
}

TEST_PREFIX="${TENANT_PREFIX_ROOT}/${TEST_TENANT_ID}/"
seed_prefix "${PRIMARY_BUCKET}" "${TEST_PREFIX}"
seed_prefix "actenora-artifacts" "${TEST_PREFIX}"
seed_prefix "actenora-evidence" "${TEST_PREFIX}"
seed_prefix "actenora-transcripts" "${TEST_PREFIX}"

echo "MinIO initialization complete."
mc ls "${MC_ALIAS}"
