<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import apiClient from '../../api/axios'
import { TrophyIcon, BoltIcon, LightBulbIcon, FaceSmileIcon, ChevronDownIcon, ChevronUpIcon } from '@heroicons/vue/24/outline'
import { useTickerState } from '../../composables/useTickerState'

interface TickerItem {
  type: 'LEADER' | 'STAT' | 'FACT' | 'JOKE'
  text: string
  icon: string
}

const { tickerHasItems, tickerCollapsed: collapsed, toggle } = useTickerState()

const items = ref<TickerItem[]>([])

const animDuration = computed(() => Math.max(120, items.value.length * 8) + 's')

const tabVisible = ref(typeof document !== 'undefined' ? !document.hidden : true)
function onVisibilityChange() {
  tabVisible.value = !document.hidden
}

const animationActive = computed(() => !collapsed.value && tabVisible.value)

async function fetchTicker() {
  try {
    const res = await apiClient.get('/public/leaderboard/ticker')
    items.value = res.data
    tickerHasItems.value = res.data.length > 0
  } catch {
    // silent fail
  }
}

function iconComponent(icon: string) {
  if (icon === 'trophy') return TrophyIcon
  if (icon === 'bolt') return BoltIcon
  if (icon === 'face-smile') return FaceSmileIcon
  return LightBulbIcon
}

function itemIconColor(type: string): string {
  if (type === 'LEADER') return 'text-yellow-300'
  if (type === 'STAT') return 'text-emerald-300'
  if (type === 'JOKE') return 'text-pink-300'
  return 'text-sky-300'
}

function itemTextColor(type: string): string {
  if (type === 'LEADER') return 'text-yellow-200'
  if (type === 'STAT') return 'text-emerald-200'
  if (type === 'JOKE') return 'text-pink-200'
  return 'text-sky-200'
}

onMounted(() => {
  fetchTicker()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <!-- Outer wrapper: relative + overflow-visible so the lasche can hang below -->
  <div v-if="items.length > 0" class="fixed top-[58px] left-0 right-0 z-39">

    <!-- Green band: collapses to a thin stripe -->
    <div
      class="bg-indigo-800 border-t border-indigo-700 overflow-hidden transition-all duration-300"
      :class="collapsed ? 'h-1' : 'h-8'">
      <div class="ticker-track-wrapper flex items-center h-full">
        <div class="ticker-track flex items-center" :class="{ 'animation-paused': !animationActive }" :style="{ animationDuration: animDuration }">
          <template v-for="pass in [0, 1]" :key="pass">
            <span
              v-for="(item, i) in items"
              :key="`${pass}-${i}`"
              :class="['flex items-center gap-1.5 text-xs whitespace-nowrap px-5', itemTextColor(item.type)]">
              <component
                :is="iconComponent(item.icon)"
                :class="['h-3.5 w-3.5 flex-shrink-0', itemIconColor(item.type)]" />
              {{ item.text }}
            </span>
            <span class="text-indigo-400 px-2 flex-shrink-0 font-bold tracking-widest">+++</span>
          </template>
        </div>
      </div>
    </div>

    <!-- Lasche: hangs below the green band, centered -->
    <button
      @click="toggle"
      class="absolute bottom-0 left-1/2 -translate-x-1/2 translate-y-full bg-indigo-800 border border-t-0 border-indigo-700 rounded-b-lg px-4 py-0.5 flex items-center gap-1 text-indigo-300 hover:text-white transition-colors"
      :title="collapsed ? 'Ticker einblenden' : 'Ticker ausblenden'">
      <ChevronUpIcon v-if="!collapsed" class="h-3 w-3" />
      <ChevronDownIcon v-else class="h-3 w-3" />
    </button>
  </div>
</template>

<style scoped>
.ticker-track-wrapper {
  contain: layout paint style;
}

.ticker-track {
  display: flex;
  animation: ticker-scroll linear infinite;
  width: max-content;
  will-change: transform;
  transform: translateZ(0);
}

.ticker-track:hover,
.ticker-track:active {
  animation-play-state: paused;
}

.ticker-track.animation-paused {
  animation: none;
  will-change: auto;
}

@keyframes ticker-scroll {
  0%   { transform: translateX(0); }
  100% { transform: translateX(-50%); }
}

@media (prefers-reduced-motion: reduce) {
  .ticker-track {
    animation: none;
    will-change: auto;
    transform: none;
  }
}
</style>
