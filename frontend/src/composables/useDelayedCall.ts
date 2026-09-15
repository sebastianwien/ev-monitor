import { onUnmounted } from 'vue'

/**
 * Ruft {@code fn} erst nach einer kurzen Wartezeit auf, ein erneutes schedule() startet sie neu.
 * Gedacht fuer "Auswahl kurz zeigen, dann weiter": die Kachel soll als gewaehlt sichtbar werden,
 * bevor der naechste Schritt erscheint. Beim Unmount faellt der ausstehende Aufruf weg.
 */
export function useDelayedCall(fn: () => void, ms: number) {
  let timer: ReturnType<typeof setTimeout> | undefined
  const cancel = () => { clearTimeout(timer); timer = undefined }
  const schedule = () => { cancel(); timer = setTimeout(fn, ms) }
  onUnmounted(cancel)
  return { schedule, cancel }
}
