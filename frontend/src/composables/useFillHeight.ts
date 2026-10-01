import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/**
 * Höhe für einen Kopf (Minimap), der den freien Platz zwischen Shell-Kopfzeile und Inhalt füllt.
 * Gemessen wird der Scrollbereich der WizardShell (Großeltern des Wurzelelements), nicht das eigene
 * Element - das wüchse sonst mit dem Kopf und die Messung bisse sich in den Schwanz. Wächst der
 * Inhalt (aufgeklapptes Rädchen, Details), schrumpft der Kopf bis zum Minimum; der Aufrufer animiert.
 * `extra` zieht den Kopf um diesen Betrag über den freien Platz hinaus, der Inhalt rutscht dann so weit
 * unter die Falz. Sobald der Inhalt selbst den Platz braucht (freier Platz unter dem Minimum), entfällt er.
 */
export interface FillHeightOptions { min?: number; max?: number; extra?: number }

export function useFillHeight(root: Ref<HTMLElement | null>, body: Ref<HTMLElement | null>, opts: FillHeightOptions = {}) {
  const { min = 112, max = 640, extra = 0 } = opts
  const height = ref(min)
  let watch: ResizeObserver | null = null
  const measure = () => {
    const scroller = root.value?.parentElement?.parentElement
    if (!scroller || !body.value) return
    const pad = parseFloat(getComputedStyle(root.value!.parentElement!).paddingBottom) || 0
    // Abstand zwischen Kopf und Body (space-y) mitrechnen, sonst bleibt genau diese Lücke als Scrollweg übrig
    // Tailwind 4 setzt space-y als margin-bottom auf das vorige Kind, ältere Versionen als margin-top auf das Kind
    const prev = body.value.previousElementSibling
    const gap = (parseFloat(getComputedStyle(body.value).marginTop) || 0) + (prev ? parseFloat(getComputedStyle(prev).marginBottom) || 0 : 0)
    const free = scroller.clientHeight - pad - body.value.offsetHeight - gap
    height.value = Math.max(min, Math.min(max, free > min ? free + extra : free))
  }
  onMounted(() => {
    if (typeof ResizeObserver === 'undefined') return
    watch = new ResizeObserver(measure)
    if (body.value) watch.observe(body.value)
    const scroller = root.value?.parentElement?.parentElement
    if (scroller) watch.observe(scroller)
  })
  onBeforeUnmount(() => watch?.disconnect())
  return height
}
