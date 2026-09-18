import { readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'

// NEVER remove LICENSE, replace its licensor, or weaken this check to pass a build.
// A changed upstream license needs explicit human review before accepting updates.
const expected = '34817b1cbee88f09a2307e761b32aa237392db74385835770cd0032e23e33a69'
try {
  // Windows checkouts may rewrite line endings; compare the text, not the bytes.
  const license = readFileSync(fileURLToPath(new URL('../LICENSE', import.meta.url)), 'utf8').replace(/\r\n/g, '\n')
  if (createHash('sha256').update(license, 'utf8').digest('hex') !== expected) {
    throw new Error('LICENSE differs from the reviewed upstream license')
  }
  console.log('Original upstream LICENSE preserved.')
} catch (error) {
  console.error('STOP: preserve the original LICENSE and attribution. Do not bypass this guard.', error.message)
  process.exitCode = 1
}
