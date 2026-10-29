import { ref } from 'vue'

/**
 * Das Erfassen-Sheet, app-weit eins: der Knopf in der Bottom-Nav, die FAB und jeder Link auf
 * /erfassen öffnen es über der aktuellen Seite. Minimiert hängt es mit den Eingaben unten über
 * der Nav, bis der Nutzer weitermacht oder verwirft.
 */
const open = ref(false)
const minimized = ref(false)

/** Ein Link auf /erfassen aus der laufenden App öffnet das Sheet; der erste Aufruf (Lesezeichen, Reload) zeigt die Seite. */
export function opensAsSheet(to: { name?: unknown }, from: { matched: ArrayLike<unknown> }): boolean {
  return to.name === 'log-form' && from.matched.length > 0
}

export function useLogCapture() {
  return {
    open,
    minimized,
    /** Öffnet das Sheet, ein minimierter Entwurf klappt mit seinen Eingaben wieder auf. */
    show() { open.value = true; minimized.value = false },
    close() { open.value = false; minimized.value = false },
  }
}
