import { onUnmounted, ref } from 'vue'
import { prefersReducedEffects } from './prefersReducedEffects'

/** Pause vor jeder Fahrt [min, max] in ms, auch vor der ersten nach dem Laden. */
export const NAV_CAR_PAUSE_MS: [number, number] = [10_000, 30_000]

export type NavCarDirection = 'right' | 'left'

/**
 * Takt für das Auto, das über die Navbar pendelt (NavCar.vue): driving wird nach einer zufälligen Pause true,
 * arrived() beendet die Fahrt, dreht die Richtung um und startet die nächste Pause. Die erste Fahrt geht nach rechts.
 * Ein Hintergrund-Tab lässt die Fahrt ausfallen, bei weniger Bewegung fährt es nie.
 */
export function useNavCar(random: () => number = Math.random) {
  const driving = ref(false)
  const direction = ref<NavCarDirection>('right')
  let timer: ReturnType<typeof setTimeout> | undefined
  const [min, max] = NAV_CAR_PAUSE_MS

  const schedule = () => {
    clearTimeout(timer)
    timer = setTimeout(() => {
      if (document.hidden) schedule()
      else driving.value = true
    }, min + random() * (max - min))
  }
  const arrived = () => {
    driving.value = false
    direction.value = direction.value === 'right' ? 'left' : 'right'
    schedule()
  }

  if (!prefersReducedEffects()) schedule()
  onUnmounted(() => clearTimeout(timer))
  return { driving, direction, arrived }
}
