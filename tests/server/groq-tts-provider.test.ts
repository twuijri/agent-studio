import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { groqTtsProvider, GROQ_TTS_VOICES, joinGroqWavAudio, splitGroqTtsText } from '../../packages/server/src/modules/studio/services/voice/tts/providers/groq'
import { GROQ_TTS_VOICES as UI_VOICES, groqTtsVoiceForModel } from '../../packages/client/src/constants/groqTtsVoices'
import { cleanTtsText, clampTtsText } from '../../packages/server/src/modules/studio/services/voice/tts/providers/text'

const english = 'canopylabs/orpheus-v1-english'
const arabic = 'canopylabs/orpheus-arabic-saudi'
const fetchMock = vi.fn()

function wav(samples = Buffer.from([1, 0, 2, 0]), rate = 24000) {
  const header = Buffer.alloc(44)
  header.write('RIFF')
  header.writeUInt32LE(36 + samples.length, 4)
  header.write('WAVEfmt ', 8)
  header.writeUInt32LE(16, 16)
  header.writeUInt16LE(1, 20)
  header.writeUInt16LE(1, 22)
  header.writeUInt32LE(rate, 24)
  header.writeUInt32LE(rate * 2, 28)
  header.writeUInt16LE(2, 32)
  header.writeUInt16LE(16, 34)
  header.write('data', 36)
  header.writeUInt32LE(samples.length, 40)
  return Buffer.concat([header, samples])
}

beforeEach(() => {
  fetchMock.mockReset().mockImplementation(async () => new Response(wav(), { headers: { 'content-type': 'audio/wav' } }))
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe('Groq TTS', () => {
  it('keeps the client and server catalogs aligned, with valid defaults', () => {
    expect(UI_VOICES).toEqual(GROQ_TTS_VOICES)
    for (const [model, voices] of Object.entries(UI_VOICES)) {
      expect(voices).toHaveLength(6)
      expect(voices).toContain(groqTtsVoiceForModel(model, ''))
      for (const voice of voices) expect(groqTtsVoiceForModel(model, voice)).toBe(voice)
    }
    expect(groqTtsVoiceForModel(arabic, 'troy')).toBe('abdullah')
  })

  it.each([[english, 'troy', 'Hello'], [arabic, 'noura', 'هلا والله']])('sends %s and its selected voice as WAV', async (model, voice, text) => {
    const controller = new AbortController()
    const result = await groqTtsProvider.synthesize({ text, signal: controller.signal }, { apiKey: 'fake-key', model, voice, format: 'mp3', rate: '2', pitch: '5' })
    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe('https://api.groq.com/openai/v1/audio/speech')
    expect(init.headers.Authorization).toBe('Bearer fake-key')
    expect(init.signal).toBe(controller.signal)
    expect(JSON.parse(init.body)).toEqual({ model, voice, input: text, response_format: 'wav' })
    expect(result).toEqual({ audio: wav(), contentType: 'audio/wav', engine: 'groq', provider: 'groq' })
  })

  it('uses documented defaults, including the Saudi voice when only the model is provided', async () => {
    await groqTtsProvider.synthesize({ text: 'Hello' }, { apiKey: 'fake' })
    await groqTtsProvider.synthesize({ text: 'مرحبا' }, { apiKey: 'fake', model: arabic })
    expect(JSON.parse(fetchMock.mock.calls[0]![1].body)).toMatchObject({ model: english, voice: 'troy' })
    expect(JSON.parse(fetchMock.mock.calls[1]![1].body)).toMatchObject({ model: arabic, voice: 'abdullah' })
  })

  it.each(['hello world '.repeat(80), 'أهلًا وسهلًا بك في الاستديو '.repeat(30)])('preserves long cleaned text within bounded requests and creates one valid WAV', async text => {
    const result = await groqTtsProvider.synthesize({ text }, { apiKey: 'fake', model: arabic, voice: 'fahad' })
    const inputs = fetchMock.mock.calls.map(([, init]) => JSON.parse(init.body).input as string)
    expect(inputs.length).toBeGreaterThan(1)
    expect(inputs.every(input => input.length > 0 && input.length <= 200)).toBe(true)
    expect(inputs.join(' ')).toBe(clampTtsText(cleanTtsText(text)))
    expect(result.audio.readUInt32LE(4)).toBe(result.audio.length - 8)
    expect(result.audio.readUInt32LE(40)).toBe(inputs.length * 4)
    expect(result.audio.subarray(44)).toEqual(Buffer.concat(inputs.map(() => Buffer.from([1, 0, 2, 0]))))
  })

  it('splits boundary and unbroken text without empty chunks or broken surrogate pairs', () => {
    expect(splitGroqTtsText('a'.repeat(200))).toEqual(['a'.repeat(200)])
    expect(splitGroqTtsText('a'.repeat(201))).toEqual(['a'.repeat(200), 'a'])
    const text = 'a'.repeat(199) + '𐐀'.repeat(110)
    const chunks = splitGroqTtsText(text)
    expect(chunks.join('')).toBe(text)
    expect(chunks.every(chunk => chunk.length <= 200 && chunk.isWellFormed())).toBe(true)
  })

  it.each([
    [{}, /API key/],
    [{ apiKey: 'fake', model: 'unknown' }, /model/],
    [{ apiKey: 'fake', model: 'constructor' }, /model/],
    [{ apiKey: 'fake', model: arabic, voice: 'troy' }, /voice/],
  ])('rejects unsupported options before any network call', async (options, error) => {
    await expect(groqTtsProvider.synthesize({ text: 'hello' }, options)).rejects.toThrow(error)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects empty cleaned input and unsafe URLs before network calls', async () => {
    await expect(groqTtsProvider.synthesize({ text: '<think>hidden</think>' }, { apiKey: 'fake' })).rejects.toThrow(/empty/)
    await expect(groqTtsProvider.synthesize({ text: 'hello' }, { apiKey: 'fake', baseUrl: 'file:///tmp/speech' })).rejects.toThrow(/baseUrl/)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('stops on an upstream failure without returning partial audio', async () => {
    fetchMock.mockResolvedValueOnce(new Response(wav()))
      .mockResolvedValueOnce(new Response('rate limited', { status: 429 }))
    await expect(groqTtsProvider.synthesize({ text: 'word '.repeat(130) }, { apiKey: 'fake' })).rejects.toThrow(/429/)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('does not issue additional requests after cancellation', async () => {
    const controller = new AbortController()
    fetchMock.mockImplementationOnce(async () => { controller.abort(); return new Response(wav()) })
    await expect(groqTtsProvider.synthesize({ text: 'word '.repeat(130), signal: controller.signal }, { apiKey: 'fake' })).rejects.toThrow()
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('joins PCM samples, skips padded metadata, and rejects corrupt or mismatched WAVs', () => {
    const source = wav()
    const extra = Buffer.concat([source.subarray(0, 12), Buffer.from('JUNK\x01\x00\x00\x00x\x00', 'binary'), source.subarray(12)])
    extra.writeUInt32LE(extra.length - 8, 4)
    expect(joinGroqWavAudio([source, extra])).toEqual(wav(Buffer.from([1, 0, 2, 0, 1, 0, 2, 0])))
    expect(() => joinGroqWavAudio([source, wav(undefined, 16000)])).toThrow(/formats differ/)
    expect(() => joinGroqWavAudio([source.subarray(0, 30)])).toThrow(/invalid/)
    expect(() => joinGroqWavAudio([Buffer.from('not audio')])).toThrow(/invalid/)
    expect(() => joinGroqWavAudio([])).toThrow(/no audio/)
  })
})
