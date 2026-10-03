<script setup lang="ts">
import { computed } from 'vue'
import type { EnergyFlow } from '../../composables/storyEnergy'
import { prefersReducedEffects } from '../../composables/prefersReducedEffects'

/**
 * Mini-Sankey als inline SVG: Netz (Zuhause und Unterwegs gestapelt) fliesst in den Akku,
 * der Verlust zweigt nach unten ab, aus dem Akku geht es auf die Strasse.
 * Band-Hoehen sind proportional zu kWh. Reveal und Partikel nur ohne reduced motion.
 */
const props = withDefaults(defineProps<{
  flow: EnergyFlow
  labels: { grid: string; battery: string; road: string; loss: string; home: string; public: string }
  /** Kilometer-Text am Strassen-Knoten, optional. */
  roadValue?: string
  large?: boolean
}>(), { roadValue: '', large: false })

const W = 320
const H = 120
const TOP = 12
const FLOW_H = 78
const NODE_W = 10
const X_GRID = 6
const X_BATTERY = 158
const X_ROAD = 304
const LOSS_GAP = 8

let seq = 0
const uid = `ef${++seq}`

const reduceMotion = prefersReducedEffects()

const geo = computed(() => {
  const f = props.flow
  const vehicleH = FLOW_H * (f.vehicleKwh / f.gridKwh)
  const lossH = FLOW_H - vehicleH
  const split = f.privateKwh + f.publicKwh
  const homeH = split > 0 ? FLOW_H * (f.privateKwh / split) : FLOW_H
  return {
    vehicleH,
    lossH,
    homeH,
    publicH: FLOW_H - homeH,
    hasSplit: split > 0,
    lossTop: TOP + vehicleH + LOSS_GAP,
  }
})

function ribbon(x0: number, top0: number, bot0: number, x1: number, top1: number, bot1: number): string {
  const mx = (x0 + x1) / 2
  return `M${x0},${top0} C${mx},${top0} ${mx},${top1} ${x1},${top1} L${x1},${bot1} C${mx},${bot1} ${mx},${bot0} ${x0},${bot0} Z`
}

function centerline(x0: number, y0: number, x1: number, y1: number): string {
  const mx = (x0 + x1) / 2
  return `M${x0},${y0} C${mx},${y0} ${mx},${y1} ${x1},${y1}`
}

const ribbons = computed(() => {
  const g = geo.value
  const gx = X_GRID + NODE_W
  const list = [{
    name: 'vehicle',
    d: ribbon(gx, TOP, TOP + g.vehicleH, X_BATTERY, TOP, TOP + g.vehicleH),
    c: centerline(gx, TOP + g.vehicleH / 2, X_BATTERY, TOP + g.vehicleH / 2),
    height: g.vehicleH,
    cls: 'fill-story-accent dark:fill-story-accent-dark',
    particles: 3,
  }, {
    name: 'road',
    d: ribbon(X_BATTERY + NODE_W, TOP, TOP + g.vehicleH, X_ROAD, TOP, TOP + g.vehicleH),
    c: centerline(X_BATTERY + NODE_W, TOP + g.vehicleH / 2, X_ROAD, TOP + g.vehicleH / 2),
    height: g.vehicleH,
    cls: 'fill-story-accent dark:fill-story-accent-dark',
    particles: 3,
  }]
  if (g.lossH > 0.01) {
    list.push({
      name: 'loss',
      d: ribbon(gx, TOP + g.vehicleH, TOP + FLOW_H, X_BATTERY, g.lossTop, g.lossTop + g.lossH),
      c: centerline(gx, TOP + g.vehicleH + g.lossH / 2, X_BATTERY, g.lossTop + g.lossH / 2),
      height: g.lossH,
      cls: 'fill-amber-500',
      particles: 1,
    })
  }
  return list
})

const fontSize = computed(() => props.large ? 10 : 9)
</script>

<template>
  <svg :viewBox="`0 0 ${W} ${H}`" class="block w-full h-auto select-none" role="img">
    <defs>
      <clipPath :id="`${uid}-clip`">
        <rect x="0" y="0" :width="W" :height="H" class="story-flow-reveal" :class="{ 'no-motion': reduceMotion }" />
      </clipPath>
      <path v-for="r in ribbons" :key="r.name" :id="`${uid}-${r.name}`" :d="r.c" fill="none" />
    </defs>

    <g :clip-path="`url(#${uid}-clip)`">
      <path
        v-for="r in ribbons" :key="r.name"
        data-role="ribbon" :data-ribbon="r.name" :data-height="r.height"
        :d="r.d" :class="r.cls" opacity="0.42" />
    </g>

    <!-- Knoten links: Zuhause ueber Unterwegs -->
    <template v-if="geo.hasSplit">
      <rect data-role="home" :x="X_GRID" :y="TOP" :width="NODE_W" :height="geo.homeH" rx="2" class="fill-story-accent dark:fill-story-accent-dark" />
      <rect data-role="public" :x="X_GRID" :y="TOP + geo.homeH" :width="NODE_W" :height="geo.publicH" rx="2" class="fill-sky-500" />
    </template>
    <rect v-else data-role="grid" :x="X_GRID" :y="TOP" :width="NODE_W" :height="FLOW_H" rx="2" class="fill-story-accent dark:fill-story-accent-dark" />

    <!-- Akku und Strasse -->
    <rect :x="X_BATTERY" :y="TOP" :width="NODE_W" :height="geo.vehicleH" rx="2" class="fill-gray-800 dark:fill-gray-100" />
    <rect :x="X_ROAD" :y="TOP" :width="NODE_W" :height="geo.vehicleH" rx="2" class="fill-gray-800 dark:fill-gray-100" />
    <rect v-if="geo.lossH > 0.01" :x="X_BATTERY" :y="geo.lossTop" :width="NODE_W" :height="geo.lossH" rx="2" class="fill-amber-500" />

    <!-- Partikel -->
    <template v-if="!reduceMotion">
      <template v-for="r in ribbons" :key="`p-${r.name}`">
        <circle v-for="i in r.particles" :key="i" r="2.2" class="story-particle" :class="r.cls" opacity="0.9">
          <animateMotion :dur="r.name === 'loss' ? '3.2s' : '2.6s'" repeatCount="indefinite" :begin="`${(i - 1) * 0.85}s`">
            <mpath :href="`#${uid}-${r.name}`" />
          </animateMotion>
        </circle>
      </template>
    </template>

    <!-- Beschriftung -->
    <g class="fill-gray-500 dark:fill-gray-400" :font-size="fontSize" font-weight="600">
      <text :x="X_GRID" :y="TOP - 4">{{ labels.grid }}</text>
      <text :x="X_BATTERY + NODE_W / 2" :y="TOP - 4" text-anchor="middle">{{ labels.battery }}</text>
      <text :x="X_ROAD + NODE_W" :y="TOP - 4" text-anchor="end">{{ labels.road }}</text>
      <text v-if="geo.lossH > 0.01" :x="X_BATTERY - 4" :y="geo.lossTop + geo.lossH / 2 + 3" text-anchor="end" class="fill-amber-700 dark:fill-amber-400">
        {{ labels.loss }} {{ flow.lossPercent }} %
      </text>
      <text v-if="roadValue" :x="X_ROAD + NODE_W" :y="TOP + geo.vehicleH + 11" text-anchor="end" class="fill-gray-700 dark:fill-gray-200">{{ roadValue }}</text>
      <!-- Orts-Labels: im Sheet immer, in der Kachel erst ab 380 px Containerbreite. -->
      <template v-if="geo.hasSplit">
        <text :x="X_GRID + NODE_W + 4" :y="TOP + geo.homeH / 2 + 3" class="fill-story-accent dark:fill-story-accent-dark" :class="large ? '' : 'hidden @[380px]:inline'">{{ labels.home }}</text>
        <text :x="X_GRID + NODE_W + 4" :y="TOP + geo.homeH + geo.publicH / 2 + 3" class="fill-sky-600 dark:fill-sky-400" :class="large ? '' : 'hidden @[380px]:inline'">{{ labels.public }}</text>
      </template>
    </g>
  </svg>
</template>

<style scoped>
.story-flow-reveal {
  transform-box: fill-box;
  transform-origin: left center;
  animation: story-flow-reveal 900ms cubic-bezier(0.2, 0.7, 0.2, 1) forwards;
}
.story-flow-reveal.no-motion { animation: none; }
@keyframes story-flow-reveal {
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
}
</style>
