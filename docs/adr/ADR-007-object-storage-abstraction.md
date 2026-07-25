# ADR-007: Object storage abstraction

- **Status:** Accepted
- **Date:** 2026-07-25

## Context

Evidence blobs and large prompt/artifacts must not live in PostgreSQL. Vendors (MinIO, S3, GCS) differ; hard-coding MinIO SDK in domain would freeze infrastructure choices.

## Decision

Introduce an **object storage port** in application/domain terms (`store`, `fetch`, `sign`, `delete`). Infrastructure provides a MinIO-compatible (S3 API) adapter first. DB stores only metadata and content hashes (Artifact context).

## Consequences

- **Positive:** Swap backends without domain changes.
- **Positive:** Keeps DB lean; enables lifecycle policies on store.
- **Negative:** Eventual consistency between DB meta and blob (mitigate with checksum + compensating delete).
- **Negative:** Local dev depends on MinIO or compatible mock.
