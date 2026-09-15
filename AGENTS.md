# Agent Map

## Personal fork: Agent Studio — mandatory first read

This is twuijri's personal, non-commercial fork. Read `docs/PERSONAL-FORK.md`.
NEVER delete, rename, replace, or strip the root `LICENSE`, third-party licenses,
or EKKOLearnAI attribution. Rebranding does not change ownership or licensing.
NEVER disable the license guard to make a build or update pass. If upstream changes
the license, stop that update for human review. Keep personal work private unless
the owner explicitly asks to publish. Upstream PR instructions below do not
authorize publishing this fork. All upstream updates must use a review branch;
never replace this fork with the upstream npm package or desktop auto-updater.

This file is a short map for coding agents. Keep detailed guidance in `docs/`
and keep this file small enough to fit into every task context.

## First Reads

- `DEVELOPMENT.md` - project commands, coding rules, test rules, and PR shape.
- `ARCHITECTURE.md` - package boundaries, data ownership, and runtime flow.
- `docs/harness/README.md` - how this repository is prepared for agent work.
- `docs/harness/validation.md` - which checks to run for each change type.
- `docs/harness/worktree-runbook.md` - isolated local dev and test setup.
- `docs/harness/pr-review.md` - self-review checklist before pushing.
- `docs/harness/server-module-boundaries.md` - target backend modules, ownership, and dependency rules.

## Common Commands

```bash
npm ci --ignore-scripts
npm run harness:check
npm run test
npm run test:e2e
npm run build
```

Use the smallest relevant check while iterating. Before a broad PR, run
`npm run harness:check`, `npm run test:coverage`, `npm run test:e2e`, and
`npm run build`.

## Code Ownership Map

- `packages/client/src` - Vue 3 client, stores, routes, i18n, API helpers.
- `packages/server/src` - Koa API, Socket.IO, persistence, Hermes integration.
- `packages/ekko-agent` - canonical Ekko runtime, profiles, providers, tools, memory, skills, and package docs.
- `packages/desktop` - Electron wrapper, bundled Python/Hermes runtime, release artifacts.
- `tests/client`, `tests/server`, `tests/shared` - Vitest coverage.
- `tests/e2e` - Playwright browser coverage with mocked backend services.
- `.github/workflows` - CI, release, Docker, and desktop packaging automation.

## Hard Rules

- Keep routes thin: put request handling in controllers and reusable behavior in services.
- Put new server code under `modules/studio`, `modules/hermes`, `modules/ekko`, or `modules/coding-agents`; compose modules only from `bootstrap`.
- Keep Web UI state under `HERMES_WEB_UI_HOME` or `HERMES_WEBUI_STATE_DIR`.
- Keep Hermes Agent state separate from Web UI state.
- Register local API routes before proxy catch-all routes.
- Use structured APIs and argument arrays instead of shell string construction.
- Add user-facing strings to every locale file.
- Do not mix unrelated refactors into a bug fix.

## When The Agent Gets Stuck

Improve the harness instead of repeating the same prompt. Add missing docs,
tests, logs, scripts, or CI checks so the next agent can see and verify the
constraint directly.
