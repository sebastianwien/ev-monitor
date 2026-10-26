import { computed, nextTick, onMounted, onUnmounted, ref, watch, type Ref, type WatchSource } from 'vue'

/**
 * Lage eines gleitenden Markers unter dem aktiven Element eines Containers: Unterstrich der Navbar-Reiter,
 * Fläche des Monats-Schalters. Der Container muss positioniert sein (offsetLeft bezieht sich auf ihn).
 * Der erste Stand springt, danach gleitet der Marker (glide), sonst käme er beim Laden von links herein.
 * Neu gemessen wird bei neuer Auswahl, bei Größenänderung (die Navbar-Leiste ist ausgeblendet, solange sie nicht passt)
 * und sobald die Schrift geladen ist.
 */
export function useActiveIndicator(container: Ref<HTMLElement | null>, selector: string, active: WatchSource<unknown>) {
  const box = ref<{ x: number; width: number } | null>(null)
  const glide = ref(false)

  function measure() {
    const el = container.value?.querySelector<HTMLElement>(selector)
    box.value = el && el.offsetWidth > 0 ? { x: el.offsetLeft, width: el.offsetWidth } : null
  }

  watch(active, () => nextTick(measure))
  let observer: ResizeObserver | null = null
  onMounted(() => {
    measure()
    requestAnimationFrame(() => { glide.value = true })
    if (typeof ResizeObserver !== 'undefined' && container.value) {
      observer = new ResizeObserver(measure)
      observer.observe(container.value)
    }
    document.fonts?.ready.then(measure)
  })
  onUnmounted(() => observer?.disconnect())

  const style = computed(() => box.value ? { width: `${box.value.width}px`, transform: `translateX(${box.value.x}px)` } : undefined)
  return { visible: computed(() => box.value != null), glide, style }
}
