/** Personal fork policy. No official paid relay or official self-update service. */
export const PERSONAL_FORK = true

export function isOfficialStudioService(value: string | undefined): boolean {
  try {
    const host = new URL(value || '').hostname.toLowerCase().replace(/\.$/, '')
    return ['ekkostudio.xyz', 'hermes-studio.ai', 'ekkolearnai.com'].some(domain =>
      host === domain || host.endsWith(`.${domain}`))
  } catch {
    return false
  }
}
