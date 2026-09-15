import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { execFileSync } from 'node:child_process'
import { checkChangeRecords, validateRecord } from '../../scripts/check-change-record.mjs'

const fixture = `# Example task
المسؤول: developer
الفرع: docs/example
الحالة: review
## المشكلة والهدف
A verified documentation gap.
## القرار والموافقات
Scope approved; merge approval pending.
## الملفات والتأثير
Only documentation.
## الفحوص
Not run: no runtime change; reviewer to verify links.
## المخاطر والرجوع
No production data changes.
## التسليم والخطوة التالية
Ready for review, not published.
`

test('Codex and Claude share the policy entry point, and the task/PR templates remain present', () => {
  const agents = readFileSync('AGENTS.md', 'utf8')
  assert.ok(agents.includes('docs/TEAM-RULES.md'))
  assert.ok(agents.trimEnd().split('\n').length <= 120)
  const claude = readFileSync('CLAUDE.md', 'utf8')
  assert.match(claude, /^@AGENTS\.md$/m)
  assert.ok(claude.trimEnd().split('\n').length <= 20)
  for (const file of ['CONTRIBUTING.md', 'DEVELOPMENT.md']) {
    assert.ok(readFileSync(file, 'utf8').includes('docs/TEAM-RULES.md'))
  }
  for (const file of ['docs/TEAM-RULES.md', 'docs/changes/README.md', '.github/PULL_REQUEST_TEMPLATE.md']) {
    assert.ok(readFileSync(file, 'utf8').trim().length > 100)
  }
  // CLAUDE.local.md stays private, but the shared root entry must be tracked.
  const ignored = execFileSync('git', ['check-ignore', '--no-index', '--non-matching', '--verbose', 'CLAUDE.md'], { encoding: 'utf8' })
  assert.ok(ignored.includes('!/CLAUDE.md'))
})

test('record validation permits explicit limitations but rejects placeholders, empty sections and invalid status', () => {
  assert.deepEqual(validateRecord(fixture), [])
  assert.ok(validateRecord(fixture.replace('developer', '<human-owner>')).length > 0)
  assert.ok(validateRecord(fixture.replace('المسؤول: developer', 'المسؤول:')).length > 0)
  assert.ok(validateRecord(fixture.replace('الحالة: review', 'الحالة: maybe')).length > 0)
  assert.ok(validateRecord(fixture.replace('Only documentation.', '<!-- fill this -->')).length > 0)
  assert.ok(validateRecord(fixture.replace('## الفحوص', '## Planned tests')).length > 0)
})

test('PR validation reads committed records, not local files, and accepts records added or updated in the diff', () => {
  const cwd = mkdtempSync(join(tmpdir(), 'agent-studio-record-test-'))
  const git = (...args) => execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
  const commit = message => { git('add', '.'); git('commit', '-qm', message) }
  try {
    git('init', '-q')
    git('config', 'user.name', 'Fixture')
    git('config', 'user.email', 'fixture@example.invalid')
    git('config', 'commit.gpgsign', 'false')
    writeFileSync(join(cwd, 'source.txt'), 'before')
    commit('base')
    const base = git('rev-parse', 'HEAD')
    writeFileSync(join(cwd, 'source.txt'), 'after')
    commit('code without record')
    assert.ok(checkChangeRecords({ cwd, base }).length > 0)
    mkdirSync(join(cwd, 'docs/changes'), { recursive: true })
    const file = join(cwd, 'docs/changes/2026-09-15-developer-example.md')
    writeFileSync(file, fixture)
    assert.ok(checkChangeRecords({ cwd, base }).length > 0, 'uncommitted record is not PR evidence')
    commit('record')
    assert.deepEqual(checkChangeRecords({ cwd, base }), [])
    const previous = git('rev-parse', 'HEAD')
    writeFileSync(file, fixture.replace('Only documentation.', 'Documentation and a focused test.'))
    commit('updated record')
    assert.deepEqual(checkChangeRecords({ cwd, base: previous }), [])
    writeFileSync(file, 'local damage does not change committed PR content')
    assert.deepEqual(checkChangeRecords({ cwd, base }), [])
    commit('invalid committed record')
    assert.ok(checkChangeRecords({ cwd, base }).length > 0)
    assert.throws(() => checkChangeRecords({ cwd, base: '--help' }), /not an option/)
  } finally {
    rmSync(cwd, { recursive: true, force: true })
  }
})
