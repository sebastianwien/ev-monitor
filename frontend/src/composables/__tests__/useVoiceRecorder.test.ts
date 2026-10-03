import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { effectScope } from 'vue'
import { useVoiceRecorder, pickMimeType, MAX_RECORDING_MS } from '../useVoiceRecorder'

/** MediaRecorder-Attrappe: liefert beim Stoppen einen Datenblock und dann onstop, wie der Browser. */
class FakeRecorder {
  static supported = ['audio/webm;codecs=opus', 'audio/webm']
  static isTypeSupported = (t: string) => FakeRecorder.supported.includes(t)
  static last: FakeRecorder | null = null
  state: 'inactive' | 'recording' = 'inactive'
  mimeType: string
  bitsPerSecond: number | undefined
  ondataavailable: ((e: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null
  constructor(_stream: unknown, opts?: { mimeType?: string; audioBitsPerSecond?: number }) {
    this.mimeType = opts?.mimeType ?? ''; this.bitsPerSecond = opts?.audioBitsPerSecond; FakeRecorder.last = this
  }
  start() { this.state = 'recording' }
  stop() {
    this.state = 'inactive'
    this.ondataavailable?.({ data: new Blob(['audio'], { type: this.mimeType }) })
    this.onstop?.()
  }
}

const track = { stop: vi.fn() }
const getUserMedia = vi.fn()

beforeEach(() => {
  vi.useFakeTimers()
  track.stop.mockReset()
  getUserMedia.mockReset().mockResolvedValue({ getTracks: () => [track] })
  FakeRecorder.supported = ['audio/webm;codecs=opus', 'audio/webm']
  vi.stubGlobal('MediaRecorder', FakeRecorder)
  vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } })
})
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

const settle = () => vi.advanceTimersByTimeAsync(0)
const setup = (upload = vi.fn().mockResolvedValue(undefined)) => {
  const scope = effectScope()
  const rec = scope.run(() => useVoiceRecorder(upload))!
  return { rec, upload, scope }
}

describe('pickMimeType', () => {
  it('WebM/Opus vor MP4: neuere Chrome können beides, gemessen ist bei Mistral nur WebM/Opus und MP4/AAC', () => {
    FakeRecorder.supported = ['audio/mp4', 'audio/webm;codecs=opus', 'audio/webm']
    expect(pickMimeType()).toBe('audio/webm;codecs=opus')
  })

  it('Safari ohne WebM nimmt MP4', () => {
    FakeRecorder.supported = ['audio/mp4']
    expect(pickMimeType()).toBe('audio/mp4')
  })

  it('ohne MediaRecorder oder ohne passendes Format: null', () => {
    FakeRecorder.supported = ['audio/x-unknown']
    expect(pickMimeType()).toBeNull()
    vi.stubGlobal('MediaRecorder', undefined)
    expect(pickMimeType()).toBeNull()
  })
})

describe('useVoiceRecorder', () => {
  it('Tap startet, zweiter Tap stoppt und lädt die Aufnahme hoch', async () => {
    const { rec, upload } = setup()
    const started = rec.press()
    expect(rec.state.value).toBe('requesting')
    await started
    expect(rec.state.value).toBe('recording')
    // Sprache braucht keine Musikqualität: 64 kbit/s halten 60 s sicher unter dem 2-MB-Limit des Backends
    expect(FakeRecorder.last!.bitsPerSecond).toBe(64_000)
    vi.advanceTimersByTime(100); rec.release()
    expect(rec.state.value).toBe('recording')
    vi.advanceTimersByTime(3000)
    expect(rec.elapsedMs.value).toBeGreaterThanOrEqual(3000)
    await rec.press()
    await settle()
    expect(upload).toHaveBeenCalledOnce()
    expect((upload.mock.calls[0][0] as Blob).type).toBe('audio/webm;codecs=opus')
    expect(rec.state.value).toBe('done')
    expect(track.stop).toHaveBeenCalled()
  })

  it('Gedrückt halten: Loslassen beendet die Aufnahme (Push-to-Talk)', async () => {
    const { rec, upload } = setup()
    await rec.press()
    vi.advanceTimersByTime(1500)
    rec.release()
    await settle()
    expect(upload).toHaveBeenCalledOnce()
  })

  it('stoppt nach 60 Sekunden von selbst', async () => {
    const { rec, upload } = setup()
    await rec.press()
    await vi.advanceTimersByTimeAsync(MAX_RECORDING_MS)
    expect(upload).toHaveBeenCalledOnce()
  })

  it('zeigt "uploading", solange der Server rechnet', async () => {
    let resolve!: () => void
    const { rec } = setup(vi.fn(() => new Promise<void>(r => { resolve = r })))
    await rec.press(); await rec.press()
    expect(rec.state.value).toBe('uploading')
    resolve(); await settle()
    expect(rec.state.value).toBe('done')
  })

  it('verweigertes Mikrofon: denied, kein Upload', async () => {
    getUserMedia.mockRejectedValue(Object.assign(new Error('no'), { name: 'NotAllowedError' }))
    const { rec, upload } = setup()
    await rec.press()
    expect(rec.state.value).toBe('denied')
    expect(upload).not.toHaveBeenCalled()
  })

  it('kein Mikrofon da: error', async () => {
    getUserMedia.mockRejectedValue(Object.assign(new Error('no'), { name: 'NotFoundError' }))
    const { rec } = setup()
    await rec.press()
    expect(rec.state.value).toBe('error')
  })

  it('Upload scheitert: error, ein neuer Tap nimmt neu auf', async () => {
    const { rec } = setup(vi.fn().mockRejectedValue(new Error('502')))
    await rec.press(); await rec.press(); await settle()
    expect(rec.state.value).toBe('error')
    await rec.press()
    expect(rec.state.value).toBe('recording')
  })

  it('Abbrechen verwirft die Aufnahme', async () => {
    const { rec, upload } = setup()
    await rec.press()
    rec.cancel(); await settle()
    expect(rec.state.value).toBe('idle')
    expect(upload).not.toHaveBeenCalled()
    expect(track.stop).toHaveBeenCalled()
  })

  it('Verlassen der Seite stoppt das Mikrofon, ohne hochzuladen', async () => {
    const { rec, upload, scope } = setup()
    await rec.press()
    scope.stop(); await settle()
    expect(track.stop).toHaveBeenCalled()
    expect(upload).not.toHaveBeenCalled()
    expect(FakeRecorder.last!.state).toBe('inactive')
  })
})
