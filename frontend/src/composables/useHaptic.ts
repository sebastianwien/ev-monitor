/** Zwei Auslöser derselben Berührung (globaler Listener und @click="haptic()") geben nur einen Tick. */
const DEDUPE_MS = 50
let lastAt = -Infinity

/** Kurzer Tick: Vibration auf Android, auf iOS ein leiser Klick über den Lautsprecher. */
export function haptic(duration = 10) {
  const now = performance.now()
  if (now - lastAt < DEDUPE_MS) return
  lastAt = now
  // Android: vibrieren; liefert es false (Vibration aus), bleibt es still statt zu klicken
  if (typeof navigator.vibrate === 'function') { navigator.vibrate(duration); return }

  // iOS (kein vibrate): kurzer Sinuston bewegt den Lautsprecher minimal
  try {
    const ctx = new AudioContext()
    const oscillator = ctx.createOscillator()
    const gain = ctx.createGain()
    oscillator.connect(gain)
    gain.connect(ctx.destination)
    oscillator.frequency.value = 440
    gain.gain.value = 0.015
    oscillator.start()
    oscillator.stop(ctx.currentTime + 0.012)
    oscillator.onended = () => ctx.close()
  } catch {
    // kein Audio-Support - still ignorieren
  }
}

export function useHaptic() {
  return { haptic }
}

/** Was beim Tippen tickt: Buttons, 3D-Knöpfe und alles mit v-haptic. */
const HAPTIC_TARGET = 'button, .btn-3d, [data-haptic]'

/**
 * Tick beim Tippen, nicht beim Aufsetzen: click feuert der Browser nur, wenn der Finger ohne Scrollen
 * losgelassen wurde (beginnt ein Scroll, kommt pointercancel und kein click). Nach dem Loslassen darf
 * Android auch beim ersten Tap vibrieren. In der Capture-Phase vor dem Verzögerer von .btn-3d-delay
 * (App.vue) registrieren: der tickt so beim echten Tap, sein Nachklick zählt nicht.
 */
export function onHapticClick(e: MouseEvent) {
  if ((e as MouseEvent & { __delayed?: boolean }).__delayed) return
  if (e.target instanceof Element && e.target.closest(HAPTIC_TARGET)) haptic()
}
