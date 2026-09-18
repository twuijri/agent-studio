# Contributing to Core Hub

Thank you for helping. Core Hub is a personal, non-commercial fork of
[Ekko Studio](https://github.com/EKKOLearnAI/hermes-studio) by EKKOLearnAI, distributed under
the upstream Business Source License 1.1. By contributing you agree that your changes are
licensed under the same terms and that upstream attribution stays intact.

The authoritative, detailed policy is [docs/TEAM-RULES.md](docs/TEAM-RULES.md) (Arabic). This page
is the short English version for public contributors. Where they differ, the team rules win.

## Ground rules

- Never delete, replace, or weaken `LICENSE`, `NOTICE.personal.md`, third-party licenses, the
  EKKOLearnAI attribution, or the license guard (`scripts/check-personal-license.mjs`).
- Keep storage paths, protocol identifiers, API routes, MCP tool names, and agent names
  compatible with upstream; only display text is rebranded.
- Do not re-enable the official updater, hosted services, or paid upstream features.
- Never commit secrets, real server hostnames, customer conversations, or local memory files.
  Use `test.example.com` / `studio.example.com` style placeholders in docs and tests.
- One topic per pull request. No unrelated refactors mixed into a fix.
- Every user-facing string goes into every locale file, and Arabic/RTL rendering is verified,
  not only translated. Read [docs/CONTENT-DIRECTION.md](docs/CONTENT-DIRECTION.md) before touching
  user or agent text.

## Workflow

Nobody has write access to this repository except the owner (`twuijri`); everyone else works from a
fork. `main` is protected: pull requests only, required checks, up-to-date branch, no force pushes.

1. **Fork** the repository and create a task branch from `main` with a clear name
   (`feat/...`, `fix/...`, `docs/...`).
2. **Explain first.** For anything beyond a trivial fix, open an issue or describe the problem
   and plan in the pull request before writing much code, so scope is agreed.
3. **Write a change record** under `docs/changes/` following
   [docs/changes/README.md](docs/changes/README.md) (owner, branch, decision and approvals, files,
   actual checks, risks and rollback, next step). CI rejects pull requests without one.
4. **Implement with tests.** Use Node.js 24:

   ```bash
   npm ci --ignore-scripts
   npm rebuild node-pty
   npm run harness:check
   npm run test:personal
   npm run test          # or the smallest relevant Vitest / Playwright subset
   ```

   Pick the checks for your change type from [docs/harness/validation.md](docs/harness/validation.md).
5. **Open a pull request to `main`** using the template, **written in English** (title and
   description) so every contributor can follow it. State what you verified and how, what you did
   not test and why, and any migration, compatibility, or RTL impact.
6. **Preview on the `test` stack.** The maintainer (or their assistant, which has standing
   authorization) merges accepted pull-request branches into the integration branch `test`,
   builds the `test` image once CI is green there, and tries it on the test server. `test` is
   preview-only and is never merged into `main`; if your branch conflicts there, the conflict is
   resolved in `test` and noted on the pull request.
7. **Review and merge.** Only the owner merges into `main`, after checks pass and the branch is
   up to date. Do not enable auto-merge. Merges to `main` are then merged back into `test`.

Coding agents (Codex, Claude Code, and others) follow the same rules; read
[AGENTS.md](AGENTS.md) first. The human who submits the change is responsible for reviewing it.

## Reporting problems

Open an issue with steps to reproduce, expected and actual behaviour, and your environment
(server image or desktop build, OS, locale). For security issues do not open a public issue;
contact the maintainer privately through GitHub.
