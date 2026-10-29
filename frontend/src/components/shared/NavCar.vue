<script setup lang="ts">
import { ref, watch } from 'vue'
import { useNavCar } from '../../composables/useNavCar'

/**
 * Ein Comic-Auto, das ab und zu über die Unterkante der Navbar fährt: die Kante ist die Straße. Je Fahrt eins von drei
 * Modellen (Knubbel-Wagen, Bus, Sportwagen). Reine Deko, ohne Klickfläche und für Screenreader unsichtbar.
 * Takt, Richtung, Modellwahl und Abschalten bei weniger Bewegung: useNavCar.
 */
const { driving, direction, model, arrived } = useNavCar()
const road = ref<HTMLElement | null>(null)
const car = ref<SVGSVGElement | null>(null)

// Tempo je Modell (Knubbel-Wagen, Bus, Sportwagen): Bus langsam, Sportwagen schnell. Gleiches Tempo auf jeder Breite;
// auf dem Handy rechnet die Fahrt mit einer Mindeststrecke, damit sie kein Wischer wird und die Tempi verschieden bleiben.
const PX_PER_S = [240, 150, 420]
const MIN_PX = 840

// Start und Ziel liegen je eine Autobreite außerhalb der Straße, das Auto fährt also ganz herein und ganz hinaus.
// Nach links fährt es gespiegelt, die Front zeigt immer in Fahrtrichtung.
watch(car, el => {
  if (!el || !road.value) return
  if (typeof el.animate !== 'function') { arrived(); return }
  const outLeft = -el.getBoundingClientRect().width
  const outRight = road.value.clientWidth
  const [from, to, flip] = direction.value === 'right' ? [outLeft, outRight, ''] : [outRight, outLeft, ' scaleX(-1)']
  const ride = el.animate(
    [{ transform: `translateX(${from}px)${flip}` }, { transform: `translateX(${to}px)${flip}` }],
    { duration: (Math.max(MIN_PX, Math.abs(to - from)) / PX_PER_S[model.value]) * 1000, easing: 'linear', fill: 'backwards' },
  )
  ride.onfinish = arrived
  ride.oncancel = arrived
}, { flush: 'post' })
</script>

<template>
  <div ref="road" class="pointer-events-none absolute inset-x-0 bottom-0 h-7 overflow-hidden" aria-hidden="true">
    <svg v-if="driving" ref="car" data-testid="nav-car" viewBox="-16 0 80 29"
         class="absolute bottom-0 left-0 h-[18px] w-auto text-gray-900 dark:text-gray-100"
         fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
      <!-- Knubbel-Wagen -->
      <template v-if="model === 0">
        <path class="nav-car-lines" d="M-13 9h6M-15 14.5h9M-12 20h5" />
        <g class="nav-car-body">
          <path class="fill-green-50 dark:fill-green-950"
                d="M5 22.5Q2 22.5 2 19.5V16.5Q2 12.6 6 12L14.5 10.8Q19 3 27.5 3H32Q40 3 46 10.8L53.5 11.8Q59 12.6 59 17.5V19.5Q59 22.5 56 22.5Z" />
          <path d="M18.5 10.8Q21.5 5.6 27.5 5.6H29.8V10.8ZM33 5.6Q39 5.6 42.5 10.8H33ZM31.4 13.5V18.5M2.2 16.5H4" />
          <circle cx="55.5" cy="15.5" r="1.6" fill="currentColor" stroke="none" />
        </g>
        <g v-for="x in [15.5, 46]" :key="x" class="nav-car-wheel">
          <circle :cx="x" cy="22.4" r="5.4" class="fill-green-50 dark:fill-green-950" />
          <circle :cx="x" cy="22.4" r="1.4" fill="currentColor" stroke="none" />
          <circle :cx="x" cy="19.2" r="0.9" fill="currentColor" stroke="none" />
        </g>
      </template>
      <!-- Bus -->
      <template v-else-if="model === 1">
        <path class="nav-car-lines" d="M-13 8h6M-15 13.5h9M-12 19h5" />
        <g class="nav-car-body">
          <path class="fill-green-50 dark:fill-green-950"
                d="M5 22.5Q2 22.5 2 19.5V7.5Q2 2.5 7 2.5H43Q47.5 2.5 50 6.5L57 14Q59 15.8 59 18.5V19.5Q59 22.5 56 22.5Z" />
          <path d="M7 7H18V12.5H7ZM22 7H35V12.5H22ZM40 7H45Q46.6 7 47.6 8.4L51.5 12.5H40ZM38 4V20M2.5 16H58.5" />
          <circle cx="55.8" cy="18.2" r="1.5" fill="currentColor" stroke="none" />
        </g>
        <g v-for="x in [14, 46.5]" :key="x" class="nav-car-wheel">
          <circle :cx="x" cy="22.4" r="5.4" class="fill-green-50 dark:fill-green-950" />
          <circle :cx="x" cy="22.4" r="1.4" fill="currentColor" stroke="none" />
          <circle :cx="x" cy="19.2" r="0.9" fill="currentColor" stroke="none" />
        </g>
      </template>
      <!-- Sportwagen: flache Keilform mit Radhäusern, lange Haube, Fließheck, getöntes Fenster -->
      <g v-else stroke-width="2">
        <path class="nav-car-lines" d="M-13 12h6M-15 16.5h9M-12 21h5" />
        <g class="nav-car-body">
          <path class="fill-green-50 dark:fill-green-950"
                d="M3 22.7H6.8A7.2 7.2 0 0 1 21.2 22.7H41.8A7.2 7.2 0 0 1 56.2 22.7H59.5Q61.5 22.7 61.5 20.8V19Q61.5 17.2 59.5 16.6L47 13.4Q42 12.5 39.5 11.6L34.5 7.6Q33 6.5 30.5 6.5H27.5Q25 6.5 23 7.6Q15 11 6 12.2L3.2 12.6Q1.8 12.8 1.8 14.5V21.2Q1.8 22.7 3 22.7Z" />
          <path d="M23 11.3Q26 8.3 28.8 8.3H30.6Q32.2 8.3 33.3 9.2L35.6 11.3Z" fill="currentColor" />
          <path d="M25 17.6Q28 15.2 32 15.4M53.6 15.3L58.2 16.5M2.6 12.6L1.8 10.6" />
          <circle cx="3.6" cy="15.6" r="0.9" fill="currentColor" stroke="none" />
        </g>
        <g v-for="x in [14, 49]" :key="x" class="nav-car-wheel">
          <circle :cx="x" cy="22.8" r="5" class="fill-green-50 dark:fill-green-950" />
          <circle :cx="x" cy="22.8" r="2.6" stroke-width="1" />
          <circle :cx="x" cy="22.8" r="1.1" fill="currentColor" stroke="none" />
          <circle :cx="x" cy="19.8" r="0.7" fill="currentColor" stroke="none" />
        </g>
      </g>
    </svg>
  </div>
</template>

<style scoped>
/* Comic-Fahrt: Karosse nach vorn gelehnt und wippend, Räder drehen, Speed-Striche flackern */
.nav-car-body {
  transform-origin: 30px 22px;
  animation: nav-car-bob 0.3s ease-in-out infinite alternate;
}
.nav-car-wheel {
  transform-box: fill-box;
  transform-origin: center;
  animation: nav-car-spin 0.35s linear infinite;
}
.nav-car-lines { animation: nav-car-flicker 0.2s steps(2) infinite; }

@keyframes nav-car-bob {
  from { transform: skewX(-7deg) translateY(0); }
  to { transform: skewX(-7deg) translateY(-0.8px); }
}
@keyframes nav-car-spin { to { transform: rotate(360deg); } }
@keyframes nav-car-flicker { 50% { opacity: 0.35; } }
</style>
