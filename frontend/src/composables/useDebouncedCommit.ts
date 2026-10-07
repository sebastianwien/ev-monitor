import { useDelayedCall } from './useDelayedCall'

/**
 * Sammelt Werte eines Eingabefelds und übergibt nur den letzten, {@code ms} nach der letzten
 * Eingabe. flush() übergibt sofort (Blur, Enter). Das Feld selbst zeigt jede Ziffer sofort,
 * nur die teure Folge (Liste neu rechnen, speichern) läuft einmal statt je Tastendruck.
 * Beim Unmount fällt ein offener Wert weg.
 */
export function useDebouncedCommit<T>(commit: (value: T) => void, ms: number) {
  let pending: T | undefined
  let hasPending = false
  const { schedule, cancel } = useDelayedCall(() => flush(), ms)

  function set(value: T) {
    pending = value
    hasPending = true
    schedule()
  }

  function flush() {
    cancel()
    if (!hasPending) return
    hasPending = false
    commit(pending as T)
  }

  return { set, flush }
}
