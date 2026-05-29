<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { BoltIcon, TrophyIcon, SparklesIcon, BanknotesIcon } from '@heroicons/vue/24/outline'
import type { Component } from 'vue'
import apiClient from '../../api/axios'
import { useI18n } from 'vue-i18n'

interface TickerItem {
  type: 'LEADER' | 'STAT' | 'FACT' | 'JOKE' | 'NEWS'
  text: string
  icon: string
}

const { t } = useI18n()

const items = ref<TickerItem[]>([])
const pageIndex = ref(0)
const tabVisible = ref(typeof document !== 'undefined' ? !document.hidden : true)
let timer: ReturnType<typeof setInterval> | null = null

const displayItems = computed(() =>
  items.value.filter(i => i.type === 'STAT' || i.type === 'LEADER')
)

const marqueeSpeed = computed(() =>
  Math.max(50, displayItems.value.length * 9) + 's'
)

const pages = computed<TickerItem[][]>(() => {
  const all = displayItems.value
  if (!all.length) return []
  const result: TickerItem[][] = []
  for (let i = 0; i < all.length; i += 4) {
    result.push(all.slice(i, i + 4))
  }
  return result
})

const currentPage = computed(() => pages.value[pageIndex.value] ?? [])

const marqueeActive = computed(() => tabVisible.value)

type CardStyle = { borderClass: string; iconClass: string; iconComponent: Component }

function cardStyle(item: TickerItem): CardStyle {
  if (item.type === 'LEADER')
    return { borderClass: 'border-l-yellow-400', iconClass: 'text-yellow-500', iconComponent: TrophyIcon }
  const text = item.text.toLowerCase()
  if (text.includes('co2') || text.includes('solarmodul') || text.includes('windrad') || text.includes('haushalt'))
    return { borderClass: 'border-l-emerald-400', iconClass: 'text-emerald-500', iconComponent: SparklesIcon }
  if (text.includes('€') || text.includes('benzin'))
    return { borderClass: 'border-l-blue-400', iconClass: 'text-blue-500', iconComponent: BanknotesIcon }
  return { borderClass: 'border-l-green-400', iconClass: 'text-green-500', iconComponent: BoltIcon }
}

function onVisibilityChange() {
  tabVisible.value = !document.hidden
}

onMounted(async () => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  try {
    const res = await apiClient.get('/public/leaderboard/ticker')
    items.value = res.data
    if (pages.value.length > 1) {
      timer = setInterval(() => {
        pageIndex.value = (pageIndex.value + 1) % pages.value.length
      }, 12000)
    }
  } catch {
    // silent fail - section simply stays hidden
  }
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  if (timer) clearInterval(timer)
})
</script>

<template>
  <section v-if="displayItems.length > 0" class="py-8 sm:py-12 border-t border-gray-100 dark:border-gray-800">
    <div class="max-w-7xl mx-auto">

      <div class="flex items-center justify-center gap-2 mb-3">
        <span class="relative flex h-2 w-2">
          <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
          <span class="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
        </span>
        <span class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
          {{ t('landing.community_pulse.live_label') }}
        </span>
      </div>

      <h2 class="text-xl sm:text-2xl font-semibold text-gray-900 dark:text-gray-100 text-center mb-6 px-6">
        {{ t('landing.community_pulse.title') }}
      </h2>

      <!-- Mobile: animated marquee -->
      <div class="lg:hidden overflow-hidden">
        <div
          class="marquee-track"
          :class="{ 'marquee-paused': !marqueeActive }"
          :style="{ animationDuration: marqueeSpeed }"
        >
          <template v-for="pass in [0, 1]" :key="pass">
            <div
              v-for="(item, i) in displayItems"
              :key="`${pass}-${i}`"
              :class="['shrink-0 w-[202px] overflow-hidden bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 border-l-4 rounded-sm p-4 mx-2', cardStyle(item).borderClass]"
            >
              <component
                :is="cardStyle(item).iconComponent"
                :class="['float-left h-6 w-6 mr-3 mt-0.5 flex-shrink-0', cardStyle(item).iconClass]"
              />
              <p class="text-sm text-gray-700 dark:text-gray-300 leading-snug">
                {{ item.text }}
              </p>
            </div>
          </template>
        </div>
      </div>

      <!-- Desktop: 4-col grid with auto-rotation -->
      <Transition name="pulse-fade" mode="out-in">
        <div :key="pageIndex" class="hidden lg:grid grid-cols-4 gap-4 px-8 lg:px-12">
          <div
            v-for="(item, i) in currentPage"
            :key="i"
            :class="['bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 border-l-4 rounded-sm p-4', cardStyle(item).borderClass]"
          >
            <component
              :is="cardStyle(item).iconComponent"
              :class="['h-5 w-5 mb-2 flex-shrink-0', cardStyle(item).iconClass]"
            />
            <p class="text-sm text-gray-700 dark:text-gray-300 leading-snug">
              {{ item.text }}
            </p>
          </div>
        </div>
      </Transition>

    </div>
  </section>
</template>

<style scoped>
.marquee-track {
  display: flex;
  width: max-content;
  animation: marquee-scroll linear infinite;
  will-change: transform;
  transform: translateZ(0);
}

.marquee-track:hover,
.marquee-track.marquee-paused {
  animation-play-state: paused;
}

@keyframes marquee-scroll {
  0%   { transform: translateX(0); }
  100% { transform: translateX(-50%); }
}

.pulse-fade-enter-active,
.pulse-fade-leave-active {
  transition: opacity 0.4s ease;
}
.pulse-fade-enter-from,
.pulse-fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .marquee-track {
    animation: none;
    overflow-x: auto;
    width: 100%;
    flex-wrap: nowrap;
  }
}
</style>
