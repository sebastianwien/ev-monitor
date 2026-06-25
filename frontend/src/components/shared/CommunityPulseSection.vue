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
const tabVisible = ref(typeof document !== 'undefined' ? !document.hidden : true)

const displayItems = computed(() =>
  items.value.filter(i => i.type === 'STAT' || i.type === 'LEADER')
)

const marqueeSpeed = computed(() =>
  Math.max(50, displayItems.value.length * 9) + 's'
)

const marqueeActive = computed(() => tabVisible.value)

type CardStyle = { shadowClass: string; iconClass: string; iconComponent: Component }

function cardStyle(item: TickerItem): CardStyle {
  if (item.type === 'LEADER')
    return { shadowClass: 'shadow-[5px_5px_0_0_#facc15]', iconClass: 'text-yellow-500', iconComponent: TrophyIcon }
  const text = item.text.toLowerCase()
  if (text.includes('co2') || text.includes('solarmodul') || text.includes('windrad') || text.includes('haushalt'))
    return { shadowClass: 'shadow-[5px_5px_0_0_#34d399]', iconClass: 'text-emerald-500', iconComponent: SparklesIcon }
  if (text.includes('€') || text.includes('benzin'))
    return { shadowClass: 'shadow-[5px_5px_0_0_#60a5fa]', iconClass: 'text-blue-500', iconComponent: BanknotesIcon }
  return { shadowClass: 'shadow-[5px_5px_0_0_#4ade80]', iconClass: 'text-green-500', iconComponent: BoltIcon }
}

function onVisibilityChange() {
  tabVisible.value = !document.hidden
}

onMounted(async () => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  try {
    const res = await apiClient.get('/public/leaderboard/ticker')
    items.value = res.data
  } catch {
    // silent fail - section simply stays hidden
  }
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <section v-if="displayItems.length > 0" class="py-10 sm:py-14 border-t border-b border-gray-200 dark:border-gray-700">
    <div class="max-w-7xl mx-auto">

      <div class="flex items-center justify-center gap-2 mb-3">
        <span class="relative flex h-2 w-2">
          <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
          <span class="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
        </span>
        <span class="text-xs sm:text-sm font-extrabold text-green-700 dark:text-green-400 uppercase tracking-[0.18em]">
          {{ t('landing.community_pulse.live_label') }}
        </span>
      </div>

      <h2 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 text-center mb-8 px-6">
        {{ t('landing.community_pulse.title') }}
      </h2>

      <!-- Animated marquee (all breakpoints). py gives the cards' bottom shadow
           room inside the overflow-hidden clip box (else it gets cut off) -->
      <div class="overflow-hidden py-3">
        <div
          class="marquee-track"
          :class="{ 'marquee-paused': !marqueeActive }"
          :style="{ animationDuration: marqueeSpeed }"
        >
          <template v-for="pass in [0, 1]" :key="pass">
            <div
              v-for="(item, i) in displayItems"
              :key="`${pass}-${i}`"
              :class="['shrink-0 w-[210px] overflow-hidden bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 rounded-lg p-4 mx-2.5', cardStyle(item).shadowClass]"
            >
              <component
                :is="cardStyle(item).iconComponent"
                :class="['float-left h-7 w-7 mr-3 mt-0.5 flex-shrink-0', cardStyle(item).iconClass]"
              />
              <p class="text-sm font-medium text-gray-800 dark:text-gray-200 leading-snug">
                {{ item.text }}
              </p>
            </div>
          </template>
        </div>
      </div>

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

@media (prefers-reduced-motion: reduce) {
  .marquee-track {
    animation: none;
    overflow-x: auto;
    width: 100%;
    flex-wrap: nowrap;
  }
}
</style>
