import { ref, watch, type Ref } from 'vue'
import { useRoute } from 'vue-router'

/**
 * Der aktive Tab eines Layouts - und zwar nur, solange die Route ueberhaupt zu diesem
 * Layout gehoert.
 *
 * Beim Sprung auf ein fremdes Ziel (Log-Feed -> Ladekarten) bleibt das alte Layout noch
 * einen Moment stehen, waehrend es ausgeblendet wird. Ein aus der Route abgeleiteter Index
 * faende sich dort nicht wieder und fiele auf 0 zurueck - der Pager wischte also noch
 * schnell den ersten Tab herein, gegen die Richtung des laufenden Uebergangs. Darum haelt
 * der Index in dem Fall seinen letzten gueltigen Wert.
 */
export function nextTabIndex(current: number, path: string, tabs: readonly string[]): number {
  const index = tabs.indexOf(path)
  return index === -1 ? current : index
}

export function useStickyTabIndex(tabs: readonly string[]): Ref<number> {
  const route = useRoute()
  const index = ref(nextTabIndex(0, route.path, tabs))
  watch(() => route.path, (path) => {
    index.value = nextTabIndex(index.value, path, tabs)
  })
  return index
}
