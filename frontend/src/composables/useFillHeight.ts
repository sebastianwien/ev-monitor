import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/**
 * Höhe für einen Kopf (Minimap), der den freien Platz zwischen Shell-Kopfzeile und Inhalt füllt.
 * Gemessen wird der Scrollbereich der WizardShell (Großeltern des Wurzelelements), nicht das eigene
 * Element - das wüchse sonst mit dem Kopf und die Messung bisse sich in den Schwanz. Wächst der
 * Inhalt (aufgeklapptes Rädchen, Details), schrumpft der Kopf bis zum Minimum; der Aufrufer animiert.
 */
export function useFillHeight(root: Ref<HTMLElement | null>, body: Ref<HTMLElement | null>, min = 112, max = 640) {
  const height = ref(min)
  let watch: ResizeObserver | null = null
  const measure = () => {
    const scroller = root.value?.parentElement?.parentElement
    if (!scroller || !body.value) return
    const pad = parseFloat(getComputedStyle(root.value!.parentElement!).paddingBottom) || 0
    const free = scroller.clientHeight - pad - body.value.offsetHeight
    height.value = Math.max(min, Math.min(max, free))
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
