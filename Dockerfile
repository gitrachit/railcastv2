# Production image for the BFF (packages/server). The server runs TypeScript
# directly via tsx because @railcast/shared is consumed as source — there is
# deliberately no emit step to drift from the contracts doc.
FROM node:22-alpine

WORKDIR /app
ENV NODE_ENV=production

# Pinned to the workspace's packageManager version (root package.json).
RUN npm install -g pnpm@10.33.0

# Manifests first so the dependency layer caches across source-only changes.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY packages/server/package.json packages/server/package.json
COPY packages/shared/package.json packages/shared/package.json
COPY packages/directory/package.json packages/directory/package.json
RUN pnpm install --frozen-lockfile --prod --filter server...

COPY packages/shared packages/shared
COPY packages/server packages/server

# Migrations run on boot (src/index.ts); listens on $PORT (default 3000).
EXPOSE 3000
CMD ["pnpm", "--filter", "server", "start"]
