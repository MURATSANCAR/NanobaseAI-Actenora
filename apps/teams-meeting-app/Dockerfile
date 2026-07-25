# syntax=docker/dockerfile:1.7

FROM node:22-bookworm AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml tsconfig.base.json ./
COPY packages/observability/typescript packages/observability/typescript
COPY packages/test-support/typescript packages/test-support/typescript
COPY packages/event-contracts/package.json packages/event-contracts/package.json
COPY packages/api-contracts/package.json packages/api-contracts/package.json
COPY apps/teams-meeting-app apps/teams-meeting-app
COPY apps/web-portal/package.json apps/web-portal/package.json
RUN pnpm install --frozen-lockfile --filter @actenora/teams-meeting-app...
RUN pnpm --filter @actenora/teams-meeting-app run build \
    && pnpm --filter @actenora/teams-meeting-app deploy --prod /out

FROM node:22-bookworm-slim AS runtime
RUN groupadd --system actenora && useradd --system --gid actenora --create-home actenora
WORKDIR /app
COPY --from=build /out/ ./
USER actenora
EXPOSE 3978
ENV TEAMS_APP_PORT=3978
CMD ["node", "dist/index.js"]
