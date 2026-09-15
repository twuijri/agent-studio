import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, copyFileSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawnSync } from 'node:child_process'

test('license guard rejects a missing or modified license', () => {
  const root = mkdtempSync(join(tmpdir(), 'agent-studio-license-test-'))
  try {
    mkdirSync(join(root, 'scripts'))
    copyFileSync('scripts/check-personal-license.mjs', join(root, 'scripts/check-personal-license.mjs'))
    const run = () => spawnSync(process.execPath, ['scripts/check-personal-license.mjs'], { cwd: root }).status
    assert.notEqual(run(), 0)
    copyFileSync('LICENSE', join(root, 'LICENSE'))
    assert.equal(run(), 0)
    writeFileSync(join(root, 'LICENSE'), 'MIT')
    assert.notEqual(run(), 0)
  } finally { rmSync(root, { recursive: true, force: true }) }
})
