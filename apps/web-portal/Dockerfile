# syntax=docker/dockerfile:1.7

FROM node:22-bookworm AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate
ARG VITE_API_MODE=http
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_PORTAL_AUTH_MODE=mock
ARG VITE_MOCK_ENTRA_OID=local-oid-admin
ARG VITE_MOCK_ENTRA_TID=local-dev-tid
ARG VITE_MOCK_EMAIL=ada@actenora.local
ARG VITE_MOCK_DISPLAY_NAME=Ada Admin
ARG VITE_MOCK_GLOBAL_ADMIN=true
ENV VITE_API_MODE=${VITE_API_MODE} \
    VITE_API_BASE_URL=${VITE_API_BASE_URL} \
    VITE_PORTAL_AUTH_MODE=${VITE_PORTAL_AUTH_MODE} \
    VITE_MOCK_ENTRA_OID=${VITE_MOCK_ENTRA_OID} \
    VITE_MOCK_ENTRA_TID=${VITE_MOCK_ENTRA_TID} \
    VITE_MOCK_EMAIL=${VITE_MOCK_EMAIL} \
    VITE_MOCK_DISPLAY_NAME=${VITE_MOCK_DISPLAY_NAME} \
    VITE_MOCK_GLOBAL_ADMIN=${VITE_MOCK_GLOBAL_ADMIN}
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml tsconfig.base.json ./
COPY packages/observability/typescript packages/observability/typescript
COPY packages/test-support/typescript packages/test-support/typescript
COPY packages/event-contracts/package.json packages/event-contracts/package.json
COPY packages/api-contracts/package.json packages/api-contracts/package.json
COPY apps/web-portal apps/web-portal
COPY apps/teams-meeting-app/package.json apps/teams-meeting-app/package.json
RUN pnpm install --frozen-lockfile --filter @actenora/web-portal...
RUN pnpm --filter @actenora/web-portal run build

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY --from=build /workspace/apps/web-portal/dist /usr/share/nginx/html
EXPOSE 8080
USER 101
