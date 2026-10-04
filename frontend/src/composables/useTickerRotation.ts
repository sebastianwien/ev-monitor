import { ref, watch, onScopeDispose, type Ref } from 'vue'

/**
 * Anzeigedauer eines Ticker-Eintrags: Grundzeit plus Lesezeit je Zeichen, gedeckelt,
 * damit nichts ewig steht. overflowScrollMs ist die Zeit, die ein überlanger Text
 * einmal durchläuft, und kommt obendrauf.
 */
export function dwellMs(chars: number, overflowScrollMs = 0): number {
  const read = Math.min(9000, Math.max(4000, 2500 + chars * 55))
  return read + overflowScrollMs
}

interface Options {
  count: Ref<number>
  paused: Ref<boolean>
  /** Anzeigedauer für den Eintrag mit diesem Index. */
  dwell: (index: number) => number
}

/**
 * Wechselanzeige statt Endlos-Laufband: ein Timer pro Eintrag, keiner während Pause.
 * Zwischen zwei Wechseln läuft nichts, die Seite ist dann komplett im Leerlauf.
 */
export function useTickerRotation({ count, paused, dwell }: Options) {
  const index = ref(0)
  let timer: ReturnType<typeof setTimeout> | undefined

  const clear = () => { if (timer) { clearTimeout(timer); timer = undefined } }
  const schedule = () => {
    clear()
    if (paused.value || count.value < 2) return
    timer = setTimeout(() => { advance(); schedule() }, dwell(index.value))
  }
  const advance = () => { index.value = (index.value + 1) % Math.max(1, count.value) }

  function next() { advance(); schedule() }
  function prev() { index.value = (index.value - 1 + count.value) % Math.max(1, count.value); schedule() }

  watch([paused, count], () => {
    if (index.value >= count.value) index.value = 0
    schedule()
  }, { immediate: true })
  onScopeDispose(clear)

  /** Nach Messung (z. B. Überlänge erkannt) die laufende Anzeigedauer neu ansetzen. */
  function restart() { schedule() }

  return { index, next, prev, restart }
}
