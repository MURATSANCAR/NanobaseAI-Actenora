# syntax=docker/dockerfile:1.7

FROM node:22-bookworm AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

# Portal auth: headers (operator Entra identity) or msal (SPA Bearer). Never bake fictional personas.
ARG VITE_API_MODE=http
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_PORTAL_AUTH_MODE=headers
ARG VITE_ENTRA_CLIENT_ID=
ARG VITE_ENTRA_TENANT_ID=
ARG VITE_ENTRA_API_SCOPE=
ARG VITE_IDENTITY_ENTRA_OID=
ARG VITE_IDENTITY_ENTRA_TID=
ARG VITE_IDENTITY_EMAIL=
ARG VITE_IDENTITY_DISPLAY_NAME=
ARG VITE_IDENTITY_GLOBAL_ADMIN=false

ENV VITE_API_MODE=${VITE_API_MODE} \
    VITE_API_BASE_URL=${VITE_API_BASE_URL} \
    VITE_PORTAL_AUTH_MODE=${VITE_PORTAL_AUTH_MODE} \
    VITE_ENTRA_CLIENT_ID=${VITE_ENTRA_CLIENT_ID} \
    VITE_ENTRA_TENANT_ID=${VITE_ENTRA_TENANT_ID} \
    VITE_ENTRA_API_SCOPE=${VITE_ENTRA_API_SCOPE} \
    VITE_IDENTITY_ENTRA_OID=${VITE_IDENTITY_ENTRA_OID} \
    VITE_IDENTITY_ENTRA_TID=${VITE_IDENTITY_ENTRA_TID} \
    VITE_IDENTITY_EMAIL=${VITE_IDENTITY_EMAIL} \
    VITE_IDENTITY_DISPLAY_NAME=${VITE_IDENTITY_DISPLAY_NAME} \
    VITE_IDENTITY_GLOBAL_ADMIN=${VITE_IDENTITY_GLOBAL_ADMIN}

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml tsconfig.base.json ./
COPY packages/observability/typescript packages/observability/typescript
COPY packages/test-support/typescript packages/test-support/typescript
COPY packages/event-contracts/package.json packages/event-contracts/package.json
COPY packages/api-contracts/package.json packages/api-contracts/package.json
COPY apps/web-portal apps/web-portal
COPY apps/teams-meeting-app/package.json apps/teams-meeting-app/package.json
RUN pnpm install --frozen-lockfile --filter @actenora/web-portal...
RUN if [ "$VITE_PORTAL_AUTH_MODE" = "msal" ] && { [ -z "$VITE_ENTRA_CLIENT_ID" ] || [ -z "$VITE_ENTRA_API_SCOPE" ]; }; then \
      echo "FATAL: VITE_ENTRA_CLIENT_ID and VITE_ENTRA_API_SCOPE required when VITE_PORTAL_AUTH_MODE=msal" >&2; \
      exit 1; \
    fi
RUN pnpm --filter @actenora/web-portal run build

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY --from=build /workspace/apps/web-portal/dist /usr/share/nginx/html
EXPOSE 8080
USER 101
