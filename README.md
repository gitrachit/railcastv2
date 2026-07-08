# Railcast

Ad-free, multilingual Indian railways companion. **Start here:**

1. Read docs/PRD.md (what we're building — FR-IDs are law) and docs/build-plan.md (how).
2. docs/api-contracts.md is the server↔app contract. docs/backlog.md is the work order.
3. Prereqs: Node 22 + pnpm, Docker, Android Studio (JDK 17), a Claude Code install (see https://code.claude.com/docs for setup).

## Working with Claude Code
- Open a terminal at the repo root and start Claude Code; the root CLAUDE.md + the relevant package CLAUDE.md load automatically.
- Take the next unchecked item in docs/backlog.md — one item per session, `/clear` between items.
- Items marked ⚠ are structural: use plan mode and review the plan before letting it execute.
- Prompt template is at the top of docs/backlog.md.

## Repo
packages/server (BFF+Watcher, TS) · packages/directory (dataset) · packages/shared (contract types) · android/ (Kotlin+Compose) · infra/ (docker-compose, deploy) · docs/ (PRD, contracts, backlog, fixtures, prototype)
