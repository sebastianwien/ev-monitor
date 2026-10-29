<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { HomeIcon, QueueListIcon, BoltIcon, Bars3Icon } from '@heroicons/vue/24/outline'
import { useHaptic } from '../../composables/useHaptic'
import { useKeyboardOpen } from '../../composables/useKeyboardOpen'
import { useActiveIndicator } from '../../composables/useActiveIndicator'

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

const isStart = computed(() => route.path === '/dashboard' || route.path === '/story')
const isLogs = computed(() => route.path === '/logs')
const isWatt = computed(() => route.path === '/coins/history')
/** Genau ein Reiter sieht aktiv aus; ist das Mehr-Sheet offen, ist es Mehr. aria-current bleibt an der Route, Mehr meldet sich über aria-expanded. */
const activeTab = computed(() => props.moreOpen ? 'more' : isStart.value ? 'start' : isLogs.value ? 'logs' : isWatt.value ? 'watt' : null)

// Wie die Navbar-Reiter (docs/design/ci.md): aktiv gray-900 mit gleitendem 3 px Strich, hier an der Oberkante
const TAB_BASE = 'flex-1 flex flex-col items-center justify-center gap-0.5 min-h-[44px] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-green-600'
const LABEL = 'text-[11px] font-semibold leading-none'
const tabClass = (tab: string) =>
  `${TAB_BASE} ${activeTab.value === tab ? 'text-gray-900 dark:text-gray-50' : 'text-gray-600 dark:text-gray-300'}`

const row = ref<HTMLElement | null>(null)
const line = useActiveIndicator(row, '[data-active]', activeTab)

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
    class="bottom-nav fixed bottom-0 left-0 right-0 z-40 md:hidden bg-green-50 dark:bg-green-950 border-t-2 border-gray-900 dark:border-gray-800 pb-[env(safe-area-inset-bottom)]"
    :aria-label="t('nav.bottom.aria')"
  >
    <div ref="row" class="relative flex items-stretch h-14">
      <!-- Linke Gruppe: Start + Logs -->
      <div class="flex-1 flex">
        <router-link
          to="/dashboard"
          :class="tabClass('start')"
          :data-active="activeTab === 'start' || undefined"
          :aria-current="isStart ? 'page' : undefined"
          @click="onTabClick(isStart)"
        >
          <HomeIcon class="h-6 w-6" />
          <span :class="LABEL">{{ t('nav.bottom.start') }}</span>
        </router-link>
        <router-link
          to="/logs"
          :class="tabClass('logs')"
          :data-active="activeTab === 'logs' || undefined"
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
          class="btn-3d absolute -top-5 flex items-center justify-center w-14 h-14 rounded-full bg-green-600 text-white border-2 border-edge active:bg-green-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gray-900 dark:focus-visible:outline-gray-100"
          :aria-label="t('nav.bottom.record')"
          @click="onRecord"
        >
          <BoltIcon class="h-7 w-7" />
        </button>
        <span class="absolute bottom-1.5 text-[11px] font-semibold leading-none text-gray-900 dark:text-gray-50">{{ t('nav.bottom.record') }}</span>
      </div>

      <!-- Rechte Gruppe: Watt + Mehr -->
      <div class="flex-1 flex">
        <router-link
          v-if="!props.isDemo"
          to="/coins/history"
          :class="tabClass('watt')"
          :data-active="activeTab === 'watt' || undefined"
          :aria-current="isWatt ? 'page' : undefined"
          :title="t('nav.bottom.watt')"
          @click="onTabClick(isWatt)"
        >
          <BoltIcon class="h-6 w-6" />
          <span :class="LABEL">{{ props.wattBalance }} W</span>
        </router-link>
        <button
          type="button"
          :class="tabClass('more')"
          :data-active="activeTab === 'more' || undefined"
          :aria-expanded="props.moreOpen"
          @click="onMore"
        >
          <Bars3Icon class="h-6 w-6" />
          <span :class="LABEL">{{ t('nav.bottom.more') }}</span>
        </button>
      </div>

      <span aria-hidden="true"
        class="pointer-events-none absolute top-0 left-0 h-[3px] rounded-b-sm bg-gray-900 dark:bg-gray-100"
        :class="[line.glide.value ? 'transition-[transform,width] duration-300 ease-out motion-reduce:transition-none' : '', line.visible.value ? '' : 'opacity-0']"
        :style="line.style.value" />
    </div>
  </nav>
</template>
