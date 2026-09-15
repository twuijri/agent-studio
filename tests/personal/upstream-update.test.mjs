import { test } from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../', import.meta.url))
const git = (cwd, ...args) => execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()

for (const scenario of ['new-upstream', 'already-included', 'changed-license', 'dirty-checkout']) {
  test(`upstream update guard: ${scenario}`, () => {
    const fixture = mkdtempSync(join(tmpdir(), 'agent-studio-update-test-'))
    try {
      const upstream = join(fixture, 'upstream')
      const personal = join(fixture, 'personal')
      mkdirSync(upstream)
      git(upstream, 'init', '--initial-branch=main')
      git(upstream, 'config', 'user.name', 'Fixture')
      git(upstream, 'config', 'user.email', 'fixture@example.invalid')
      copyFileSync(join(root, 'LICENSE'), join(upstream, 'LICENSE'))
      git(upstream, 'add', 'LICENSE')
      git(upstream, 'commit', '-m', 'base')
      git(fixture, 'clone', '--origin', 'upstream', upstream, personal)
      git(personal, 'config', 'user.name', 'Fixture')
      git(personal, 'config', 'user.email', 'fixture@example.invalid')
      mkdirSync(join(personal, 'scripts'))
      for (const file of ['prepare-upstream-update.mjs', 'check-personal-license.mjs']) {
        copyFileSync(join(root, 'scripts', file), join(personal, 'scripts', file))
      }
      git(personal, 'add', 'scripts')
      git(personal, 'commit', '-m', 'personal policy')
      const before = git(personal, 'rev-parse', 'HEAD')
      if (scenario === 'dirty-checkout') writeFileSync(join(personal, 'draft.txt'), 'unfinished')
      if (scenario === 'new-upstream' || scenario === 'changed-license') {
        writeFileSync(join(upstream, scenario === 'changed-license' ? 'LICENSE' : 'feature.txt'), 'new upstream content')
        git(upstream, 'add', '.')
        git(upstream, 'commit', '-m', scenario)
      }
      const result = spawnSync(process.execPath, ['scripts/prepare-upstream-update.mjs'], { cwd: personal, encoding: 'utf8' })
      assert.equal(git(personal, 'rev-parse', 'HEAD'), before, 'working branch must never be replaced')
      if (scenario === 'new-upstream') {
        assert.equal(result.status, 0, result.stderr)
        assert.match(git(personal, 'tag'), /^backups\//)
        const paths = git(personal, 'worktree', 'list', '--porcelain').split('\n').filter(line => line.startsWith('worktree '))
        assert.equal(paths.length, 2)
        const review = paths[1].slice('worktree '.length)
        assert.equal(git(review, 'rev-parse', 'HEAD'), before)
        assert.equal(readFileSync(join(review, 'feature.txt'), 'utf8'), 'new upstream content')
        assert.equal(git(review, 'rev-parse', 'MERGE_HEAD'), git(upstream, 'rev-parse', 'HEAD'))
      } else {
        assert.equal(result.status, scenario === 'already-included' ? 0 : 1)
        assert.equal(git(personal, 'tag'), '')
        assert.equal(git(personal, 'worktree', 'list', '--porcelain').match(/^worktree /gm).length, 1)
      }
    } finally {
      rmSync(fixture, { recursive: true, force: true })
    }
  })
}
