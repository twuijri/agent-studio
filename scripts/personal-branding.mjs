// Idempotent display-name update. Internal protocol IDs, paths, agent names,
// licenses and upstream provenance deliberately retain their original values.
import { readFileSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
const files = execFileSync('git', ['ls-files', 'packages/client', 'packages/desktop/src', 'clients/android/app/src/main/res'], { encoding: 'utf8' }).trim().split('\n')
for (const file of files) {
  if (!/\.(vue|ts|html|webmanifest|xml)$/.test(file)) continue
  if (file.endsWith('/login-item-migration.ts')) continue
  const before = readFileSync(file, 'utf8')
  const after = before.split('\n').map(line => {
    if (/https?:|new_\d|Copyright|copyright|LICENSE|userData|appData/.test(line)) return line
    // Historical updater directory names are compatibility aliases, not UI copy.
    if (file.endsWith('/updater-helpers.ts') && line.includes('names.add(')) return line
    const displayName = file.endsWith('/ar.ts') || file.includes('/values-ar/') ? 'كور هب' : 'Core Hub'
    let result = line.replaceAll('Agent Studio', displayName).replaceAll('Ekko Studio', displayName)
      .replaceAll('إيجنت استديو', 'كور هب')
    if (file.includes('/i18n/locales/')) {
      // Third-party provider names are not our product branding.
      result = result.replace(/\b(?:LM Studio|Google AI Studio|Studio)\b/g,
        name => name === 'Studio' ? displayName : name)
    }
    if (file.startsWith('clients/android/')) result = result.replaceAll('Hermes Studio', displayName)
    return result
  }).join('\n')
  if (after !== before) writeFileSync(file, after)
}
