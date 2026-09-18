import { appendFileSync, mkdirSync, readFileSync, renameSync, statSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'
import type { DeviceAgentAuditEntry } from './protocol'

// Persistent settings and audit log of the desktop Device Agent. Both live
// next to desktop-mode.json in the Electron userData directory.

export type DeviceAgentApprovalMode = 'ask' | 'always'

export interface DeviceAgentConfig {
  enabled: boolean
  capabilities: { exec: boolean; files: boolean }
  allowedFolders: string[]
  approvalMode: DeviceAgentApprovalMode
  /** Studio server this device identity was paired with (normalized origin + path). */
  pairedServerUrl: string | null
  pairedAt: number | null
}

export const DEVICE_AGENT_CONFIG_FILE_NAME = 'device-agent.json'
export const DEVICE_AGENT_IDENTITY_FILE_NAME = 'device-identity.json'
export const DEVICE_AGENT_AUDIT_FILE_NAME = 'device-agent-audit.jsonl'

const AUDIT_MAX_BYTES = 2 * 1024 * 1024
const AUDIT_KEEP_LINES = 2000

export function defaultDeviceAgentConfig(): DeviceAgentConfig {
  return {
    enabled: false,
    capabilities: { exec: false, files: false },
    allowedFolders: [],
    approvalMode: 'ask',
    pairedServerUrl: null,
    pairedAt: null,
  }
}

export function parseDeviceAgentConfig(raw: unknown): DeviceAgentConfig {
  const base = defaultDeviceAgentConfig()
  if (!raw || typeof raw !== 'object') return base
  const record = raw as Record<string, unknown>
  const capabilities = (record.capabilities && typeof record.capabilities === 'object' ? record.capabilities : {}) as Record<string, unknown>
  const folders = Array.isArray(record.allowedFolders)
    ? record.allowedFolders.filter((value): value is string => typeof value === 'string' && value.trim().length > 0).map(value => value.trim())
    : []
  return {
    enabled: record.enabled === true,
    capabilities: { exec: capabilities.exec === true, files: capabilities.files === true },
    allowedFolders: [...new Set(folders)],
    approvalMode: record.approvalMode === 'always' ? 'always' : 'ask',
    pairedServerUrl: typeof record.pairedServerUrl === 'string' && record.pairedServerUrl.trim() ? record.pairedServerUrl.trim() : null,
    pairedAt: typeof record.pairedAt === 'number' && Number.isFinite(record.pairedAt) ? record.pairedAt : null,
  }
}

export function readDeviceAgentConfig(filePath: string): DeviceAgentConfig {
  try {
    return parseDeviceAgentConfig(JSON.parse(readFileSync(filePath, 'utf8')))
  } catch {
    return defaultDeviceAgentConfig()
  }
}

export function writeDeviceAgentConfig(filePath: string, config: DeviceAgentConfig): DeviceAgentConfig {
  const normalized = parseDeviceAgentConfig(config)
  mkdirSync(dirname(filePath), { recursive: true })
  const tmp = `${filePath}.${process.pid}.tmp`
  writeFileSync(tmp, `${JSON.stringify({ version: 1, ...normalized }, null, 2)}\n`, 'utf8')
  renameSync(tmp, filePath)
  return normalized
}

export function appendDeviceAgentAudit(filePath: string, entry: DeviceAgentAuditEntry): void {
  mkdirSync(dirname(filePath), { recursive: true })
  appendFileSync(filePath, `${JSON.stringify(entry)}\n`, 'utf8')
  try {
    if (statSync(filePath).size > AUDIT_MAX_BYTES) {
      const lines = readFileSync(filePath, 'utf8').split('\n').filter(Boolean)
      writeFileSync(filePath, `${lines.slice(-AUDIT_KEEP_LINES).join('\n')}\n`, 'utf8')
    }
  } catch {
    // trimming is best-effort
  }
}

export function readDeviceAgentAudit(filePath: string, limit = 50): DeviceAgentAuditEntry[] {
  let text: string
  try {
    text = readFileSync(filePath, 'utf8')
  } catch {
    return []
  }
  const entries: DeviceAgentAuditEntry[] = []
  for (const line of text.split('\n').filter(Boolean).slice(-limit)) {
    try {
      const parsed = JSON.parse(line)
      if (parsed && typeof parsed === 'object' && typeof parsed.at === 'number') entries.push(parsed as DeviceAgentAuditEntry)
    } catch {
      // skip corrupt line
    }
  }
  return entries.reverse()
}
