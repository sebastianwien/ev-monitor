<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import apiClient from '../api/axios'
import { TrophyIcon, BoltIcon, LightBulbIcon, FaceSmileIcon, ChevronDownIcon, ChevronUpIcon } from '@heroicons/vue/24/outline'

interface TickerItem {
  type: 'LEADER' | 'STAT' | 'FACT' | 'JOKE'
  text: string
  icon: string
}

const STORAGE_KEY = 'ticker-collapsed'

const items = ref<TickerItem[]>([])
const collapsed = ref(localStorage.getItem(STORAGE_KEY) === 'true')

const animDuration = computed(() => Math.max(120, items.value.length * 8) + 's')

async function fetchTicker() {
  try {
    const res = await apiClient.get('/public/leaderboard/ticker')
    items.value = res.data
  } catch {
    // silent fail
  }
}

function toggle() {
  collapsed.value = !collapsed.value
  localStorage.setItem(STORAGE_KEY, String(collapsed.value))
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
})
</script>

<template>
  <!-- Outer wrapper: relative + overflow-visible so the lasche can hang below -->
  <div v-if="items.length > 0" class="relative z-30">

    <!-- Green band: collapses to a thin stripe -->
    <div
      class="bg-green-800 border-t border-green-600 overflow-hidden transition-all duration-300"
      :class="collapsed ? 'h-1' : 'h-8'">
      <div class="ticker-track-wrapper flex items-center h-8">
        <div class="ticker-track flex items-center" :style="{ animationDuration: animDuration }">
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
            <span class="text-green-500 px-2 flex-shrink-0 font-bold tracking-widest">+++</span>
          </template>
        </div>
      </div>
    </div>

    <!-- Lasche: hangs below the green band, centered -->
    <button
      @click="toggle"
      class="absolute bottom-0 left-1/2 -translate-x-1/2 translate-y-full bg-green-800 border border-t-0 border-green-600 rounded-b-lg px-4 py-0.5 flex items-center gap-1 text-green-300 hover:text-white transition-colors"
      :title="collapsed ? 'Ticker einblenden' : 'Ticker ausblenden'">
      <ChevronUpIcon v-if="!collapsed" class="h-3 w-3" />
      <ChevronDownIcon v-else class="h-3 w-3" />
    </button>
  </div>
</template>

<style scoped>
.ticker-track {
  display: flex;
  animation: ticker-scroll linear infinite;
  width: max-content;
}

.ticker-track:hover {
  animation-play-state: paused;
}

@keyframes ticker-scroll {
  0%   { transform: translateX(0); }
  100% { transform: translateX(-50%); }
}
</style>
