# Agent Map

## Personal fork: Core Hub — mandatory first read

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

- `docs/TEAM-RULES.md` - mandatory shared team/AI development policy. Explain the problem and plan before implementation; record each task under `docs/changes/`; isolate concurrent work; get owner approval before opening a PR, merging, publishing, or deploying. A request to write code alone does not authorize these external actions.
- `docs/KNOWLEDGE-WORKFLOW.md` - durable personal-fork decisions and scoped code knowledge. Reuse verified knowledge; do not repeat whole-repo analysis by default. Understand-Anything is an external developer aid, NEVER a Studio runtime dependency. Check graph freshness and current source before relying on it.
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
- Docker upgrades must work by replacing only the image in existing stacks. Managed npm agent binaries belong under `/home/agent/.hermes/coding-agent/npm`, covered by the existing Hermes mount. Do not require Compose PATH/volume changes or redirect desktop/native homes. Preserve discovery of legacy installs.
- Register local API routes before proxy catch-all routes.
- Use structured APIs and argument arrays instead of shell string construction.
- Add user-facing strings to every locale file.
- Do not mix unrelated refactors into a bug fix.
- Every task needs a maintained change record: human owner, branch, evidence, affected files, actual checks, limitations, and next step. Read only relevant prior records, not every historical note.
- Do not claim a task is merged, published, deployed, or tested without evidence for that exact state. Never overwrite another contributor's work or approve your own high-risk changes.
- Work on task branches and submit PRs; only owner `twuijri` decides and performs merges into `main`. Assistants must not merge or enable auto-merge. Before owner review, verify the latest base/head, conflict-free integration, and passing checks; no textual conflict alone is not evidence of behavioral compatibility.
- Delivery flow for EVERY change (human or AI, any model): task branch from `origin/main` → change record → checks → push → PR to `main` → then merge the same branch into the integration branch `test` and push it directly (standing owner authorization, no PR and no per-change approval for `test`) → once CI passes on `test`, build the `test` image (`personal-image.yml` with `source_ref=test`, `publish_latest=false`, `preview_tag=test`) → tell the owner to pull/redeploy the test stack. `test` is preview-only and is never merged into `main`; `latest` and merges into `main` stay owner-only. Full procedure: `docs/TEAM-RULES.md` §3 and §9.

## Content Direction Contract

Read `docs/CONTENT-DIRECTION.md` before changing user/agent text or inputs. Use
`ContentText` and shared `contentInputProps` / `technicalInputProps`; UI locale
direction is not content direction. Keep the existing Markdown/code rendering.
New surfaces must add adoption/regression coverage; do not weaken guards or
assume they can classify every dynamic string. Owner review remains required.

## When The Agent Gets Stuck

Improve the harness instead of repeating the same prompt. Add missing docs,
tests, logs, scripts, or CI checks so the next agent can see and verify the
constraint directly.
