import { ref, watch, onScopeDispose, type Ref } from 'vue'

/** Haltezeit an jedem Ende eines überlangen Ticker-Texts. */
export const PING_PONG_HOLD_MS = 3000
/** Laufgeschwindigkeit überlanger Texte. */
export const OVERFLOW_PX_PER_S = 40

/** Fahrzeit für eine Richtung. */
export function scrollMs(overflowPx: number): number {
  return (overflowPx / OVERFLOW_PX_PER_S) * 1000
}

/** Einmal hin und zurück inklusive Haltezeiten; 0 ohne Überlänge. */
export function roundTripMs(overflowPx: number): number {
  return overflowPx > 0 ? 2 * (PING_PONG_HOLD_MS + scrollMs(overflowPx)) : 0
}

interface Options {
  overflowPx: Ref<number>
  /** Stoppt die Bewegung an der aktuellen Position (Pause-Knopf, verborgener Tab). */
  paused: Ref<boolean>
}

/**
 * Überlange Texte laufen hin und her: Halt am Anfang, Fahrt nach links, Halt am Ende, zurück.
 * atEnd schaltet die Zielposition, die Bewegung selbst macht eine CSS-Transition.
 */
export function useTickerPingPong({ overflowPx, paused }: Options) {
  const atEnd = ref(false)
  let timer: ReturnType<typeof setTimeout> | undefined

  const clear = () => { if (timer) { clearTimeout(timer); timer = undefined } }
  const step = (delay: number) => {
    timer = setTimeout(() => {
      atEnd.value = !atEnd.value
      step(scrollMs(overflowPx.value) + PING_PONG_HOLD_MS)
    }, delay)
  }
  const start = () => {
    clear()
    if (paused.value || overflowPx.value <= 0) return
    step(PING_PONG_HOLD_MS)
  }

  /** Neuer Eintrag: zurück an den Anfang, Zyklus neu beginnen. */
  function reset() {
    atEnd.value = false
    start()
  }

  watch(paused, start)
  onScopeDispose(clear)

  return { atEnd, reset }
}
