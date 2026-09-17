/**
 * Short-lived cache and in-flight coalescing for Kanban reads.
 *
 * The board polls and the event bridge both refresh several endpoints at once;
 * without this, one board refresh issued the same query many times in a row.
 * Entries live for a few seconds and are dropped for a board as soon as a
 * mutation runs or Hermes reports an event, so the UI never shows stale data
 * longer than one poll interval.
 */
const DEFAULT_TTL_MS = 3000

interface CacheEntry<T> {
  value: T
  expiresAt: number
}

const entries = new Map<string, CacheEntry<unknown>>()
const inFlight = new Map<string, Promise<unknown>>()

function cacheKey(board: string, name: string, params: unknown): string {
  return `${board} ${name} ${JSON.stringify(params ?? null)}`
}

export async function cachedRead<T>(
  board: string,
  name: string,
  params: unknown,
  read: () => Promise<T>,
  ttlMs = DEFAULT_TTL_MS,
): Promise<T> {
  const key = cacheKey(board, name, params)
  const hit = entries.get(key)
  if (hit && hit.expiresAt > Date.now()) return hit.value as T
  const pending = inFlight.get(key)
  if (pending) return pending as Promise<T>
  const promise = read()
    .then((value) => {
      entries.set(key, { value, expiresAt: Date.now() + ttlMs })
      return value
    })
    .finally(() => {
      inFlight.delete(key)
    })
  inFlight.set(key, promise)
  return promise
}

/** Drop cached reads for one board (or every board when omitted). */
export function invalidateKanbanReads(board?: string | null): void {
  if (!board) {
    entries.clear()
    return
  }
  const prefix = `${board} `
  for (const key of entries.keys()) {
    if (key.startsWith(prefix)) entries.delete(key)
  }
  // Board lists carry per-board counts, so they change with any board.
  for (const key of entries.keys()) {
    if (key.startsWith('* ')) entries.delete(key)
  }
}

/** Test hook. */
export function resetKanbanReadCache(): void {
  entries.clear()
  inFlight.clear()
}
