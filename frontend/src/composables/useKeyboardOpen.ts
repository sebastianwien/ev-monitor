import { ref, onMounted, onUnmounted, type Ref } from 'vue'

/**
 * Mindest-Differenz (CSS-px) zwischen ungeschrumpftem und aktuellem Visual-Viewport,
 * ab der wir von einer offenen Software-Tastatur ausgehen. Gross genug, um normale
 * Schwankungen (ein-/ausblendende Browser-Toolbar, URL-Bar) zu ignorieren.
 */
export const KEYBOARD_MIN_DELTA_PX = 150

/**
 * Reine, testbare Erkennung: Tastatur gilt als offen, wenn der aktuelle
 * Visual-Viewport um mehr als `threshold` unter der Referenzhoehe (ungeschrumpft) liegt.
 * Robust gegen 0/negative Werte (kein false positive bei uninitialisiertem Viewport).
 */
export function isKeyboardOpen(referenceHeight: number, visualHeight: number, threshold = KEYBOARD_MIN_DELTA_PX): boolean {
  if (referenceHeight <= 0 || visualHeight <= 0) return false
  return referenceHeight - visualHeight > threshold
}

/**
 * Liefert ein reaktives Flag, das `true` ist, solange die Software-Tastatur offen ist.
 *
 * Nutzt die `visualViewport`-API (in der Android-WebView und mobilen Browsern verfuegbar,
 * kein Capacitor-Plugin noetig). Als Referenz dient die groesste je gesehene Viewport-Hoehe,
 * damit die Erkennung unabhaengig vom Keyboard-Resize-Modus funktioniert. Bei Orientierungs-
 * wechsel wird die Referenz zurueckgesetzt. Auf Desktop/ohne visualViewport bleibt es `false`.
 */
export function useKeyboardOpen(): Ref<boolean> {
  const open = ref(false)
  const vv = typeof window !== 'undefined' ? window.visualViewport : null
  let reference = 0

  const update = () => {
    if (!vv) return
    reference = Math.max(reference, vv.height)
    open.value = isKeyboardOpen(reference, vv.height)
  }

  const resetReference = () => {
    reference = 0
    update()
  }

  onMounted(() => {
    if (!vv) return
    vv.addEventListener('resize', update)
    window.addEventListener('orientationchange', resetReference)
    update()
  })

  onUnmounted(() => {
    vv?.removeEventListener('resize', update)
    window.removeEventListener('orientationchange', resetReference)
  })

  return open
}
