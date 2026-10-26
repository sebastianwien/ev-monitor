<script setup lang="ts">
import { ref, watch } from 'vue'
import { useNavCar } from '../../composables/useNavCar'

/**
 * Ein Comic-Auto, das ab und zu über die Unterkante der Navbar fährt: die Kante ist die Straße. Reine Deko,
 * ohne Klickfläche und für Screenreader unsichtbar. Takt und Abschalten bei weniger Bewegung: useNavCar.
 */
const { driving, arrived } = useNavCar()
const road = ref<HTMLElement | null>(null)
const car = ref<SVGSVGElement | null>(null)

// Gleiches Tempo auf jeder Breite, auf dem Handy aber nicht nur ein Wischer
const PX_PER_S = 240
const MIN_MS = 3500

watch(car, el => {
  if (!el || !road.value) return
  if (typeof el.animate !== 'function') { arrived(); return }
  const from = -el.getBoundingClientRect().width
  const to = road.value.clientWidth
  const ride = el.animate(
    [{ transform: `translateX(${from}px)` }, { transform: `translateX(${to}px)` }],
    { duration: Math.max(MIN_MS, ((to - from) / PX_PER_S) * 1000), easing: 'linear' },
  )
  ride.onfinish = arrived
  ride.oncancel = arrived
}, { flush: 'post' })
</script>

<template>
  <div ref="road" class="pointer-events-none absolute inset-x-0 bottom-0 h-7 overflow-hidden" aria-hidden="true">
    <svg v-if="driving" ref="car" data-testid="nav-car" viewBox="-16 0 80 29"
         class="absolute bottom-0 left-0 h-[26px] w-auto -translate-x-full text-gray-900 dark:text-gray-100"
         fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
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
