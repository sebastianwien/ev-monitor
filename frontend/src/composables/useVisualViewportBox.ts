import { ref, onMounted, onUnmounted, type Ref } from 'vue'

export interface ViewportBox { top: number; height: number }

/**
 * Versatz und Hoehe des tatsaechlich sichtbaren Ausschnitts (Visual Viewport).
 * iOS Safari und aeltere WebViews verkleinern bei offener Tastatur nur diesen Ausschnitt
 * und schieben ihn zum Eingabefeld - fixierte Elemente wandern dabei aus dem Bild.
 * Wer seinen Rahmen an diese Box bindet, bleibt sichtbar. Null, wenn die API fehlt.
 */
export function useVisualViewportBox(): Ref<ViewportBox | null> {
  const box = ref<ViewportBox | null>(null)
  const vv = typeof window !== 'undefined' ? window.visualViewport : null
  const update = () => { if (vv) box.value = { top: Math.round(vv.offsetTop), height: Math.round(vv.height) } }
  update()
  onMounted(() => { vv?.addEventListener('resize', update); vv?.addEventListener('scroll', update) })
  onUnmounted(() => { vv?.removeEventListener('resize', update); vv?.removeEventListener('scroll', update) })
  return box
}
