# syntax=docker/dockerfile:1.7

FROM node:22-bookworm AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate
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
