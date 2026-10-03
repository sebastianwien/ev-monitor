import { getCurrentScope, onScopeDispose, ref } from 'vue'

export type RecorderState = 'idle' | 'requesting' | 'recording' | 'uploading' | 'done' | 'denied' | 'error'

/**
 * Formate, die Mistral direkt annimmt (gemessen 03.10.2026: WebM/Opus und MP4/AAC) - in dieser
 * Reihenfolge probiert. WebM zuerst: neuere Chrome nehmen auch MP4 auf, dort aber nicht zwingend
 * mit AAC. Safari/iOS ohne WebM landet bei MP4. Kein Transkodieren im Backend.
 */
export const VOICE_MIME_TYPES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus']
/** Sprache braucht keine Musikqualität: 64 kbit/s sind ~480 kB je Minute, weit unter dem 2-MB-Limit. */
export const AUDIO_BITS_PER_SECOND = 64_000
export const MAX_RECORDING_MS = 60_000
/** Länger gehalten: Loslassen beendet (Push-to-Talk). Kürzer: Tap, die Aufnahme läuft bis zum nächsten Tap. */
export const HOLD_MS = 400

export function pickMimeType(): string | null {
  const MR = (globalThis as any).MediaRecorder
  if (!MR || typeof MR.isTypeSupported !== 'function') return null
  return VOICE_MIME_TYPES.find(t => MR.isTypeSupported(t)) ?? null
}

/** Ob dieses Gerät aufnehmen kann - ohne kein Mikrofon-Button. */
export function isVoiceSupported(): boolean {
  return typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia && pickMimeType() != null
}

/**
 * Sprachaufnahme für den Ladevorgang: Mikrofon anfragen, aufnehmen (Tap oder Halten, höchstens
 * 60 s), Pegel für den Ring, dann die Aufnahme an `upload` geben. Verlässt der Nutzer die Seite,
 * gehen Mikrofon und Aufnahme aus, ohne dass etwas hochgeladen wird.
 */
export function useVoiceRecorder(upload: (audio: Blob) => Promise<void>) {
  const state = ref<RecorderState>('idle')
  const elapsedMs = ref(0)
  /** Lautstärke 0..1 für den Pegel-Ring */
  const level = ref(0)

  let stream: MediaStream | null = null
  let recorder: MediaRecorder | null = null
  let chunks: Blob[] = []
  let startedAt = 0
  let pressedAt = 0
  let discard = false
  let disposed = false
  let ticker: ReturnType<typeof setInterval> | undefined
  let limit: ReturnType<typeof setTimeout> | undefined
  let audioCtx: AudioContext | null = null
  let raf = 0

  const startMeter = (s: MediaStream) => {
    const Ctx = (globalThis as any).AudioContext ?? (globalThis as any).webkitAudioContext
    if (!Ctx || typeof requestAnimationFrame !== 'function') return
    try {
      audioCtx = new Ctx() as AudioContext
      // iOS startet den Kontext nach dem await von getUserMedia oft angehalten: ohne resume bleibt der Pegel stumm
      audioCtx.resume?.().catch(() => {})
      const analyser = audioCtx.createAnalyser()
      analyser.fftSize = 512
      audioCtx.createMediaStreamSource(s).connect(analyser)
      const buf = new Uint8Array(analyser.fftSize)
      const tick = () => {
        analyser.getByteTimeDomainData(buf)
        let sum = 0
        for (const v of buf) sum += ((v - 128) / 128) ** 2
        level.value = Math.min(1, Math.sqrt(sum / buf.length) * 4)
        raf = requestAnimationFrame(tick)
      }
      tick()
    } catch { audioCtx = null }
  }

  const releaseDevices = () => {
    clearInterval(ticker); clearTimeout(limit)
    if (raf) cancelAnimationFrame(raf)
    raf = 0; level.value = 0
    audioCtx?.close().catch(() => {}); audioCtx = null
    stream?.getTracks().forEach(t => t.stop()); stream = null
  }

  const finish = async () => {
    const type = recorder?.mimeType || pickMimeType() || ''
    recorder = null
    releaseDevices()
    if (discard || disposed) { discard = false; state.value = 'idle'; return }
    const audio = new Blob(chunks, { type })
    chunks = []
    if (!audio.size) { state.value = 'idle'; return }
    state.value = 'uploading'
    try { await upload(audio); state.value = 'done' } catch { state.value = 'error' }
  }

  const stop = () => { if (recorder?.state === 'recording') recorder.stop() }

  const start = async () => {
    state.value = 'requesting'
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch (e: any) {
      state.value = e?.name === 'NotAllowedError' || e?.name === 'SecurityError' ? 'denied' : 'error'
      return
    }
    if (disposed) { releaseDevices(); return }
    const mimeType = pickMimeType()
    try {
      recorder = new MediaRecorder(stream, { ...(mimeType ? { mimeType } : {}), audioBitsPerSecond: AUDIO_BITS_PER_SECOND })
    } catch { releaseDevices(); state.value = 'error'; return }
    chunks = []
    recorder.ondataavailable = (e: BlobEvent) => { if (e.data?.size) chunks.push(e.data) }
    recorder.onstop = () => { finish() }
    recorder.start()
    startedAt = Date.now(); elapsedMs.value = 0
    state.value = 'recording'
    ticker = setInterval(() => { elapsedMs.value = Date.now() - startedAt }, 200)
    limit = setTimeout(stop, MAX_RECORDING_MS)
    startMeter(stream)
  }

  /** pointerdown: startet, oder beendet eine laufende Aufnahme (zweiter Tap). */
  const press = async () => {
    if (state.value === 'recording') { stop(); return }
    if (state.value === 'requesting' || state.value === 'uploading') return
    pressedAt = Date.now()
    await start()
  }
  /** pointerup: nach langem Halten ist Loslassen das Ende der Aufnahme. */
  const release = () => {
    if (state.value === 'recording' && Date.now() - pressedAt >= HOLD_MS) stop()
  }
  const cancel = () => {
    if (recorder?.state === 'recording') { discard = true; recorder.stop() } else if (state.value !== 'uploading') state.value = 'idle'
  }

  if (getCurrentScope()) onScopeDispose(() => { disposed = true; stop(); releaseDevices() })

  return { state, elapsedMs, level, press, release, cancel }
}
