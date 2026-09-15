import { execFileSync, spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// Prepare a reviewable merge in another worktree; never deploy or rewrite history.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim()
execFileSync(process.execPath, ['scripts/check-personal-license.mjs'], { cwd: root, stdio: 'inherit' })
if (git('status', '--porcelain')) throw new Error('Commit your changes before preparing an upstream update.')
git('fetch', 'upstream', 'main')
const target = git('rev-parse', 'FETCH_HEAD')
const license = execFileSync('git', ['show', `${target}:LICENSE`], { cwd: root })
if (createHash('sha256').update(license).digest('hex') !== '34817b1cbee88f09a2307e761b32aa237392db74385835770cd0032e23e33a69') {
  throw new Error('Upstream LICENSE changed. Human review is required; no merge was started.')
}
if (spawnSync('git', ['merge-base', '--is-ancestor', target, 'HEAD'], { cwd: root }).status === 0) {
  console.log('This upstream revision is already included.')
  process.exit(0)
}
const stamp = new Date().toISOString().replace(/[:.]/g, '-')
const branch = `updates/${stamp}`
const path = join(dirname(root), `agent-studio-update-${stamp}`)
git('tag', `backups/${stamp}`, 'HEAD')
git('worktree', 'add', '-b', branch, path, 'HEAD')
const merge = spawnSync('git', ['merge', '--no-ff', '--no-commit', target], { cwd: path, stdio: 'inherit' })
console.log(`Review branch: ${branch}\nReview checkout: ${path}\nUpstream: ${target}`)
console.log('Follow docs/PERSONAL-FORK.md. Resolve conflicts individually, preserve LICENSE, run checks, then commit. Nothing was deployed.')
process.exitCode = merge.status === 0 ? 0 : 1
