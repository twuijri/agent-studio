// Official Orpheus catalog: https://console.groq.com/docs/text-to-speech/orpheus
export const GROQ_TTS_DEFAULT_MODEL = 'canopylabs/orpheus-v1-english'
export const GROQ_TTS_MODEL_OPTIONS = [
  { label: GROQ_TTS_DEFAULT_MODEL, value: GROQ_TTS_DEFAULT_MODEL },
  { label: 'canopylabs/orpheus-arabic-saudi', value: 'canopylabs/orpheus-arabic-saudi' },
]

export const GROQ_TTS_VOICES: Record<string, readonly string[]> = {
  'canopylabs/orpheus-v1-english': ['autumn', 'diana', 'hannah', 'austin', 'daniel', 'troy'],
  'canopylabs/orpheus-arabic-saudi': ['abdullah', 'fahad', 'sultan', 'lulwa', 'noura', 'aisha'],
}

export function groqTtsVoiceOptions(model: string) {
  const voices = Object.hasOwn(GROQ_TTS_VOICES, model) ? GROQ_TTS_VOICES[model]! : []
  return voices.map(voice => ({ label: voice, value: voice }))
}

export function groqTtsVoiceForModel(model: string, current: string): string {
  const options = groqTtsVoiceOptions(model)
  if (options.some(option => option.value === current)) return current
  return model === GROQ_TTS_DEFAULT_MODEL ? 'troy' : options[0]?.value || ''
}
