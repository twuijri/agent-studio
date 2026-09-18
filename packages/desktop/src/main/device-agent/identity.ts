import { createHash, generateKeyPairSync, sign } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

// Device identity for the desktop Device Agent. The format and signing
// scheme mirror the Studio server's own device identity
// (packages/server/src/modules/studio/public/system-info.ts) so a Studio
// server can verify this device with its existing pairing code paths.

export interface DeviceIdentity {
  device_id: string
  device_public_key: string
  device_private_key: string
}

export function deviceIdFromPublicKey(publicKey: string): string {
  return `hwui_${createHash('sha256').update(publicKey).digest('base64url').slice(0, 32)}`
}

function isValidDeviceIdentity(value: unknown): value is DeviceIdentity {
  const record = value as Partial<DeviceIdentity> | null
  return typeof record?.device_id === 'string'
    && record.device_id.length >= 16
    && typeof record.device_public_key === 'string'
    && record.device_public_key.includes('PUBLIC KEY')
    && typeof record.device_private_key === 'string'
    && record.device_private_key.includes('PRIVATE KEY')
    && deviceIdFromPublicKey(record.device_public_key) === record.device_id
}

export function loadOrCreateDeviceIdentity(filePath: string): DeviceIdentity {
  try {
    const existing = JSON.parse(readFileSync(filePath, 'utf8'))
    if (isValidDeviceIdentity(existing)) return existing
  } catch {
    // create below
  }
  const keyPair = generateKeyPairSync('ed25519', {
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  })
  const identity: DeviceIdentity = {
    device_id: deviceIdFromPublicKey(keyPair.publicKey),
    device_public_key: keyPair.publicKey,
    device_private_key: keyPair.privateKey,
  }
  mkdirSync(dirname(filePath), { recursive: true })
  writeFileSync(filePath, JSON.stringify(identity, null, 2), { encoding: 'utf8', mode: 0o600 })
  return identity
}

export function createDeviceSigningPayload(deviceId: string, nonce: string, timestamp: number): string {
  return `${deviceId}.${nonce}.${timestamp}`
}

export function signDeviceChallenge(identity: DeviceIdentity, nonce: string, timestamp: number): string {
  return sign(null, Buffer.from(createDeviceSigningPayload(identity.device_id, nonce, timestamp)), identity.device_private_key)
    .toString('base64url')
}
