import { watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pricelessDeepLink } from '../utils/pricelessDeepLink'

/**
 * Einstieg aus der Monatsrückblick-Mail (/logs?car=<id>&nachtragen=preis), für Log-Feed und Logbuch.
 * Wartet, bis die Autos geladen sind, und nimmt die Parameter danach aus der URL, damit Neuladen das
 * Modal nicht erneut öffnet.
 */
export function usePricelessDeepLink(cars: Ref<{ id: string }[]>, selectedCarId: Ref<string | null>, open: () => void) {
  const route = useRoute()
  const router = useRouter()
  let handled = false
  watch(cars, (list) => {
    if (handled || !list.length) return
    handled = true
    const link = pricelessDeepLink(route.query, list)
    if (!link) return
    if (link.carId) selectedCarId.value = link.carId
    open()
    router.replace({ query: { ...route.query, car: undefined, nachtragen: undefined } })
  }, { immediate: true })
}
