<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { HomeIcon, QueueListIcon, BoltIcon, Bars3Icon } from '@heroicons/vue/24/outline'
import { useHaptic } from '../../composables/useHaptic'
import { useKeyboardOpen } from '../../composables/useKeyboardOpen'

const props = defineProps<{
  /** Ob das Mehr-Sheet gerade offen ist (steuert den Aktiv-Zustand des Mehr-Tabs). */
  moreOpen: boolean
  /** Watt-Guthaben (wird als eigener Tab angezeigt). */
  wattBalance: number
  /** Demo-Account: kein Watt-Tab (Guthaben fuer Demo nicht relevant). */
  isDemo: boolean
}>()

const emit = defineEmits<{
  /** Zentraler FAB: Ladevorgang erfassen. */
  record: []
  /** Mehr-Tab: Sheet oeffnen/schliessen. */
  toggleMore: []
}>()

const route = useRoute()
const { t } = useI18n()
const { haptic } = useHaptic()
const keyboardOpen = useKeyboardOpen()

const isStart = computed(() => route.path === '/dashboard')
const isLogs = computed(() => route.path === '/logs')
const isWatt = computed(() => route.path === '/coins/history')

const TAB_BASE = 'flex-1 flex flex-col items-center justify-center gap-0.5 min-h-[44px] transition-colors'
const LABEL = 'text-[11px] font-medium leading-none'
const tabClass = (active: boolean) =>
  `${TAB_BASE} ${active ? 'text-indigo-600 dark:text-indigo-400' : 'text-gray-500 dark:text-gray-400'}`

/** Tippt man den bereits aktiven Tab erneut, an den Seitenanfang scrollen (Standard-Pattern). */
function onTabClick(active: boolean) {
  haptic()
  if (active) window.scrollTo({ top: 0, behavior: 'smooth' })
}

function onRecord() {
  haptic()
  emit('record')
}

function onMore() {
  haptic()
  emit('toggleMore')
}
</script>

<template>
  <nav
    v-show="!keyboardOpen"
    class="bottom-nav fixed bottom-0 left-0 right-0 z-40 md:hidden bg-white/95 dark:bg-gray-900/95 backdrop-blur border-t border-gray-200 dark:border-gray-800 shadow-[0_-6px_20px_-6px_rgba(0,0,0,0.18)] dark:shadow-[0_-6px_20px_-6px_rgba(0,0,0,0.6)] pb-[env(safe-area-inset-bottom)]"
    :aria-label="t('nav.bottom.aria')"
  >
    <div class="flex items-stretch h-14">
      <!-- Linke Gruppe: Start + Logs -->
      <div class="flex-1 flex">
        <router-link
          to="/dashboard"
          :class="tabClass(isStart)"
          :aria-current="isStart ? 'page' : undefined"
          @click="onTabClick(isStart)"
        >
          <HomeIcon class="h-6 w-6" />
          <span :class="LABEL">{{ t('nav.bottom.start') }}</span>
        </router-link>
        <router-link
          to="/logs"
          :class="tabClass(isLogs)"
          :aria-current="isLogs ? 'page' : undefined"
          @click="onTabClick(isLogs)"
        >
          <QueueListIcon class="h-6 w-6" />
          <span :class="LABEL">{{ t('nav.bottom.logs') }}</span>
        </router-link>
      </div>

      <!-- Zentraler FAB: Erfassen -->
      <div class="relative w-20 flex justify-center">
        <button
          type="button"
          class="absolute -top-5 flex items-center justify-center w-14 h-14 rounded-full bg-green-600 text-white shadow-[3px_3px_0_rgba(0,0,0,0.30)] dark:shadow-[3px_3px_0_rgba(255,255,255,0.30)] transition-transform active:scale-95 active:bg-green-700"
          :aria-label="t('nav.bottom.record')"
          @click="onRecord"
        >
          <BoltIcon class="h-7 w-7" />
        </button>
        <span class="absolute bottom-1.5 text-[11px] font-medium leading-none text-green-700 dark:text-green-400">{{ t('nav.bottom.record') }}</span>
      </div>

      <!-- Rechte Gruppe: Watt + Mehr -->
      <div class="flex-1 flex">
        <router-link
          v-if="!props.isDemo"
          to="/coins/history"
          :class="tabClass(isWatt)"
          :aria-current="isWatt ? 'page' : undefined"
          :title="t('nav.bottom.watt')"
          @click="onTabClick(isWatt)"
        >
          <BoltIcon class="h-6 w-6" />
          <span :class="LABEL">{{ props.wattBalance }} W</span>
        </router-link>
        <button
          type="button"
          :class="tabClass(props.moreOpen)"
          :aria-expanded="props.moreOpen"
          @click="onMore"
        >
          <Bars3Icon class="h-6 w-6" />
          <span :class="LABEL">{{ t('nav.bottom.more') }}</span>
        </button>
      </div>
    </div>
  </nav>
</template>
