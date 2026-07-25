# FAZ 19 — Template Studio ve Document Rendering

**Status:** Implemented in `modules/template`  
**Date:** 2026-07-25

## Delivered

- Models: `MeetingTemplate`, `TemplateVersion`, `DesignSchema`, `ContentSchema`, `RenderJob`, `RenderedDocument`, `NoteTemplateLock`
- Drag/drop design schema (ordered allow-listed components; arbitrary JS forbidden)
- HTML sanitizer (scripts/handlers/`javascript:` stripped)
- Publish workflow; published versions immutable
- Notes lock to a published `TemplateVersionId`
- HTML + PDF renderer (DejaVu Sans, Turkish chars, long tables + CSS page-break rules)
- MinIO-compatible storage via `MinioObjectStorage` / `ObjectStorage` port; content hash + render idempotency
- `DocumentRenderWorker` extractable to `services/document-renderer`
- Flyway: `V202__template_studio_and_rendering.sql`

## Tests (11 passing)

- template versioning + publish immutability
- note lock
- sanitize / no arbitrary JS
- PDF Turkish chars + long tables
- duplicate render idempotency
- worker retry after storage failure
- storage timeout surfaces as `OBJECT_STORAGE_TIMEOUT`
