#!/usr/bin/env node
// Read Git objects, not the working tree: validate the exact PR head being reviewed.
import { execFileSync } from 'node:child_process'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const headings = [
  'المشكلة والهدف', 'القرار والموافقات', 'الملفات والتأثير',
  'الفحوص', 'المخاطر والرجوع', 'التسليم والخطوة التالية',
]
const recordPath = /^docs\/changes\/\d{4}-\d{2}-\d{2}-[a-z0-9][a-z0-9-]*\.md$/

export function validateRecord(text) {
  const errors = []
  for (const field of ['المسؤول', 'الفرع']) {
    const value = text.match(new RegExp(`^${field}:[ \\t]*(.+)$`, 'm'))?.[1]?.trim()
    if (!value || /[<>]/.test(value)) errors.push(`Missing real value for ${field}`)
  }
  if (!/^الحالة: (planned|in-progress|blocked|review|done)\s*$/m.test(text)) {
    errors.push('Missing or invalid task status')
  }
  const sections = new Map()
  for (const section of text.split(/^## /m).slice(1)) {
    const newline = section.indexOf('\n')
    sections.set(section.slice(0, newline).trim(), section.slice(newline + 1).replace(/<!--[\s\S]*?-->/g, '').trim())
  }
  for (const heading of headings) {
    if (!sections.get(heading)) errors.push(`Missing or empty section: ${heading}`)
  }
  return errors
}

export function checkChangeRecords({ cwd = process.cwd(), base, head = 'HEAD' }) {
  const git = (...args) => execFileSync('git', args, { cwd, encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 }).trimEnd()
  const revision = ref => {
    if (!ref || ref.startsWith('-')) throw new Error('Expected a Git revision, not an option')
    return git('rev-parse', '--verify', '--end-of-options', `${ref}^{commit}`)
  }
  const baseSha = revision(base)
  const headSha = revision(head)
  const changed = git('diff', '--name-only', '--no-renames', '--diff-filter=AM', '-z', `${baseSha}...${headSha}`, '--')
    .split('\0').filter(Boolean)
  const records = changed.filter(file => recordPath.test(file))
  if (!records.length) return ['Every PR must add or update a dated task record under docs/changes/; README alone is not a record.']
  return records.flatMap(file => validateRecord(git('show', `${headSha}:${file}`)).map(error => `${file}: ${error}`))
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const [base, head = 'HEAD', extra] = process.argv.slice(2)
    if (!base || extra) throw new Error('Usage: node scripts/check-change-record.mjs BASE [HEAD]')
    const errors = checkChangeRecords({ base, head })
    if (errors.length) throw new Error(errors.join('\n'))
    console.log('Change record structure verified; human review must verify its accuracy.')
  } catch (error) {
    console.error(error.message)
    process.exitCode = 1
  }
}
