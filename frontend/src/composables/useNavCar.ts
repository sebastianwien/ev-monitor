import { onUnmounted, ref } from 'vue'
import { prefersReducedEffects } from './prefersReducedEffects'

/** Pause bis zur ersten Fahrt nach dem Laden, danach zwischen zwei Fahrten [min, max] in ms. */
export const NAV_CAR_FIRST_MS: [number, number] = [5_000, 5_000]
export const NAV_CAR_GAP_MS: [number, number] = [120_000, 300_000]

/**
 * Takt für das Auto, das ab und zu über die Navbar fährt (NavCar.vue): driving wird nach einer zufälligen Pause
 * true, arrived() beendet die Fahrt und startet die nächste Pause. Ein Hintergrund-Tab lässt die Fahrt ausfallen,
 * bei weniger Bewegung fährt es nie.
 */
export function useNavCar(random: () => number = Math.random) {
  const driving = ref(false)
  let timer: ReturnType<typeof setTimeout> | undefined
  const pause = ([min, max]: [number, number]) => min + random() * (max - min)

  const schedule = (range: [number, number]) => {
    clearTimeout(timer)
    timer = setTimeout(() => {
      if (document.hidden) schedule(NAV_CAR_GAP_MS)
      else driving.value = true
    }, pause(range))
  }
  const arrived = () => {
    driving.value = false
    schedule(NAV_CAR_GAP_MS)
  }

  if (!prefersReducedEffects()) schedule(NAV_CAR_FIRST_MS)
  onUnmounted(() => clearTimeout(timer))
  return { driving, arrived }
}
