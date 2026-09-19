import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { Readable } from 'node:stream'
import { afterEach, describe, expect, it, vi } from 'vitest'

// Local files (the desktop's local server, or the server host itself) are
// streamed with HTTP range support so the chat's <video> player can play large
// exports instead of freezing on a buffered, size-capped read.

vi.mock('../../packages/server/src/modules/studio/public/profile-config', () => ({ getActiveProfileName: () => 'default' }))
vi.mock('../../packages/server/src/modules/studio/services/files/file-provider', async importOriginal => {
  const actual = await importOriginal<typeof import('../../packages/server/src/modules/studio/services/files/file-provider')>()
  return { ...actual, createFileProvider: async () => actual.localProvider }
})

const dirs: string[] = []
afterEach(() => { for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true }) })

function fakeCtx(query: Record<string, string>, headers: Record<string, string> = {}) {
  const set: Record<string, string> = {}
  return {
    query,
    status: 200,
    body: undefined as unknown,
    headers: set,
    set(name: string, value: string) { set[name.toLowerCase()] = value },
    get(name: string) { return headers[name.toLowerCase()] || '' },
    state: {},
  }
}

async function collect(body: unknown): Promise<Buffer> {
  const chunks: Buffer[] = []
  for await (const chunk of body as Readable) chunks.push(Buffer.from(chunk))
  return Buffer.concat(chunks)
}

describe('file download streaming', () => {
  it('streams a local video inline with Accept-Ranges and serves byte ranges as 206', async () => {
    const { download } = await import('../../packages/server/src/modules/studio/controllers/download')
    const dir = mkdtempSync(join(tmpdir(), 'core-hub-download-'))
    dirs.push(dir)
    const file = join(dir, 'final.mp4')
    const bytes = Buffer.from('0123456789abcdefghij')
    writeFileSync(file, bytes)

    const full = fakeCtx({ path: file })
    await download(full)
    expect(full.status).toBe(200)
    expect(full.headers['content-type']).toBe('video/mp4')
    expect(full.headers['accept-ranges']).toBe('bytes')
    expect(full.headers['content-disposition']).toMatch(/^inline;/)
    expect(full.headers['content-length']).toBe(String(bytes.length))
    expect((await collect(full.body)).equals(bytes)).toBe(true)

    const partial = fakeCtx({ path: file }, { range: 'bytes=5-9' })
    await download(partial)
    expect(partial.status).toBe(206)
    expect(partial.headers['content-range']).toBe(`bytes 5-9/${bytes.length}`)
    expect(partial.headers['content-length']).toBe('5')
    expect((await collect(partial.body)).toString()).toBe('56789')

    const tail = fakeCtx({ path: file }, { range: 'bytes=15-' })
    await download(tail)
    expect(tail.status).toBe(206)
    expect((await collect(tail.body)).toString()).toBe('fghij')

    const bad = fakeCtx({ path: file }, { range: 'bytes=99-100' })
    await download(bad)
    expect(bad.status).toBe(416)
    expect(bad.headers['content-range']).toBe(`bytes */${bytes.length}`)
  })

  it('keeps documents as attachments and reports missing files as 404', async () => {
    const { download } = await import('../../packages/server/src/modules/studio/controllers/download')
    const dir = mkdtempSync(join(tmpdir(), 'core-hub-download-'))
    dirs.push(dir)
    const doc = join(dir, 'notes.txt')
    writeFileSync(doc, 'hello')
    const ctx = fakeCtx({ path: doc })
    await download(ctx)
    expect(ctx.status).toBe(200)
    expect(ctx.headers['content-disposition']).toMatch(/^attachment;/)
    expect((await collect(ctx.body)).toString()).toBe('hello')

    const missing = fakeCtx({ path: join(dir, 'nope.mp4') })
    await download(missing)
    expect(missing.status).toBe(404)
  })
})
