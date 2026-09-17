import { createOpenaiCompatibleTtsProvider } from './openai'
import { cleanTtsText, clampTtsText } from './text'
import type { OpenaiTtsProvider } from './types'

// Official Orpheus catalog: https://console.groq.com/docs/text-to-speech/orpheus
// The UI mirrors this catalog; groq-tts-provider.test.ts checks they stay aligned.
export const GROQ_TTS_VOICES: Record<string, readonly string[]> = {
  'canopylabs/orpheus-v1-english': ['autumn', 'diana', 'hannah', 'austin', 'daniel', 'troy'],
  'canopylabs/orpheus-arabic-saudi': ['abdullah', 'fahad', 'sultan', 'lulwa', 'noura', 'aisha'],
}
const DEFAULT_MODEL = 'canopylabs/orpheus-v1-english'
const transport = createOpenaiCompatibleTtsProvider('groq', {
  defaultBaseUrl: 'https://api.groq.com/openai/v1',
})

export function splitGroqTtsText(text: string): string[] {
  const chunks: string[] = []
  let remaining = text.trim()
  while (remaining) {
    let end = Math.min(200, remaining.length)
    if (end < remaining.length) {
      // Prefer a word boundary; never split a UTF-16 surrogate pair.
      const space = remaining.lastIndexOf(' ', end)
      if (space > 0) end = space
      else if (/[\uD800-\uDBFF]/.test(remaining[end - 1]!)) end--
    }
    chunks.push(remaining.slice(0, end))
    remaining = remaining.slice(end).trimStart()
  }
  return chunks
}

function readPcmWav(audio: Buffer): { format: Buffer; data: Buffer } {
  const invalid = () => new Error('Groq TTS returned invalid or unsupported PCM WAV audio')
  if (audio.length < 12 || audio.toString('ascii', 0, 4) !== 'RIFF' || audio.toString('ascii', 8, 12) !== 'WAVE') throw invalid()
  const limit = audio.readUInt32LE(4) + 8
  if (limit > audio.length || limit < 12) throw invalid()
  let format: Buffer | undefined
  const data: Buffer[] = []
  for (let offset = 12; offset < limit;) {
    if (offset + 8 > limit) throw invalid()
    const size = audio.readUInt32LE(offset + 4)
    const end = offset + 8 + size
    if (end + (size % 2) > limit) throw invalid()
    const id = audio.toString('ascii', offset, offset + 4)
    if (id === 'fmt ') format = audio.subarray(offset + 8, end)
    if (id === 'data') data.push(audio.subarray(offset + 8, end))
    offset = end + (size % 2)
  }
  if (!format || format.length < 16 || format.readUInt16LE(0) !== 1 || !data.length) throw invalid()
  const blockAlign = format.readUInt16LE(12)
  const channels = format.readUInt16LE(2)
  const sampleRate = format.readUInt32LE(4)
  const bits = format.readUInt16LE(14)
  if (!channels || !sampleRate || !bits || bits % 8 || blockAlign !== channels * bits / 8 || format.readUInt32LE(8) !== sampleRate * blockAlign) throw invalid()
  const samples = Buffer.concat(data)
  if (!blockAlign || !samples.length || samples.length % blockAlign !== 0) throw invalid()
  return { format: format.subarray(0, 16), data: samples }
}

export function joinGroqWavAudio(parts: Buffer[]): Buffer {
  if (!parts.length) throw new Error('Groq TTS returned no audio')
  const parsed = parts.map(readPcmWav)
  const format = parsed[0]!.format
  if (parsed.some(part => !part.format.equals(format))) throw new Error('Groq TTS audio formats differ between segments')
  const data = Buffer.concat(parsed.map(part => part.data))
  const output = Buffer.alloc(44 + data.length + (data.length % 2))
  output.write('RIFF', 0)
  output.writeUInt32LE(output.length - 8, 4)
  output.write('WAVEfmt ', 8)
  output.writeUInt32LE(16, 16)
  format.copy(output, 20)
  output.write('data', 36)
  output.writeUInt32LE(data.length, 40)
  data.copy(output, 44)
  return output
}

export const groqTtsProvider: OpenaiTtsProvider = {
  id: 'groq',
  async synthesize(req, opts) {
    if (!opts.apiKey?.trim()) throw new Error('Groq TTS API key is required')
    const model = opts.model || DEFAULT_MODEL
    const voices = Object.hasOwn(GROQ_TTS_VOICES, model) ? GROQ_TTS_VOICES[model] : undefined
    if (!voices) throw new Error('Unsupported Groq TTS model')
    const voice = opts.voice || (model === DEFAULT_MODEL ? 'troy' : 'abdullah')
    if (!voices.includes(voice)) throw new Error('Groq TTS voice does not match the selected model')
    // Keep Studio's existing text budget, but respect Groq's 200-character limit
    // per request. WAV headers cannot simply be concatenated along with audio.
    const chunks = splitGroqTtsText(clampTtsText(cleanTtsText(req.text)))
    if (!chunks.length) throw new Error('Groq TTS text is empty after cleaning')
    const parts: Buffer[] = []
    for (const text of chunks) {
      req.signal?.throwIfAborted()
      const result = await transport.synthesize({ ...req, text }, {
        apiKey: opts.apiKey, baseUrl: opts.baseUrl, model, voice, format: 'wav',
      })
      parts.push(result.audio)
    }
    req.signal?.throwIfAborted()
    return {
      audio: parts.length === 1 ? parts[0]! : joinGroqWavAudio(parts),
      contentType: 'audio/wav', engine: 'groq', provider: 'groq',
    }
  },
}
