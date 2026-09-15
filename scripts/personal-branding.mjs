// Idempotent display-name update. Internal protocol IDs, paths, agent names,
// licenses and upstream provenance deliberately retain their original values.
import { readFileSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
const files = execFileSync('git', ['ls-files', 'packages/client', 'packages/desktop/src', 'clients/android/app/src/main/res'], { encoding: 'utf8' }).trim().split('\n')
for (const file of files) {
  if (!/\.(vue|ts|html|webmanifest|xml)$/.test(file)) continue
  const before = readFileSync(file, 'utf8')
  const after = before.split('\n').map(line => {
    if (/https?:|new_\d|Copyright|copyright|LICENSE|userData|appData|legacy|Legacy/.test(line)) return line
    return line.replaceAll('Ekko Studio', 'Agent Studio')
  }).join('\n')
  if (after !== before) writeFileSync(file, after)
}
