<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  XMarkIcon, TruckIcon, ArrowsRightLeftIcon, ArrowDownTrayIcon,
  Cog6ToothIcon,
  ArrowRightOnRectangleIcon, ChatBubbleLeftEllipsisIcon,
} from '@heroicons/vue/24/outline'
import ThemeToggle from './ThemeToggle.vue'
import LocaleSwitcher from './LocaleSwitcher.vue'
import SupportPopover from '../settings/SupportPopover.vue'
import { useHaptic } from '../../composables/useHaptic'

const props = defineProps<{
  open: boolean
  feedbackMailto: string
  isDemo: boolean
  isNative: boolean
}>()

const emit = defineEmits<{ close: []; logout: [] }>()

const { t } = useI18n()
const { haptic } = useHaptic()

interface NavItem { to: string; label: string; icon: typeof TruckIcon; demoHidden?: boolean }
const items: NavItem[] = [
  { to: '/cars', label: 'dashboard.vehicles_btn', icon: TruckIcon },
  { to: '/modelle', label: 'nav.models_compare', icon: ArrowsRightLeftIcon },
  { to: '/imports', label: 'nav.bottom.import', icon: ArrowDownTrayIcon, demoHidden: true },
  { to: '/settings', label: 'nav.bottom.settings', icon: Cog6ToothIcon, demoHidden: true },
]

// Pull-down-to-dismiss: das Sheet laesst sich nach unten wegziehen. Die Geste
// startet nur, wenn der Scroll-Container ganz oben steht (sonst will der User
// scrollen). Ueber der Schwelle schliesst es, sonst federt es zurueck.
const DISMISS_THRESHOLD_PX = 120
const sheetRef = ref<HTMLElement | null>(null)
const dragY = ref(0)
const dragging = ref(false)
let startY = 0
let active = false

// Beim Oeffnen den evtl. vom Schliessen stehengebliebenen Offset zuruecksetzen.
watch(() => props.open, (open) => { if (open) dragY.value = 0 })

const sheetStyle = computed(() => {
  if (!dragging.value && dragY.value === 0) return undefined
  return {
    transform: `translateY(${dragY.value}px)`,
    transition: dragging.value ? 'none' : 'transform 0.25s cubic-bezier(0.32, 0.72, 0, 1)',
  }
})

function onTouchStart(e: TouchEvent) {
  if (e.touches.length !== 1 || (sheetRef.value?.scrollTop ?? 0) > 0) return
  startY = e.touches[0].clientY
  active = true
  dragging.value = true
}
function onTouchMove(e: TouchEvent) {
  if (!active) return
  const dy = e.touches[0].clientY - startY
  if (dy > 0) {
    dragY.value = dy
    if (e.cancelable) e.preventDefault() // native Overscroll waehrend des Zugs unterdruecken
  } else {
    // nach oben -> Geste abbrechen, nativen Scroll wieder zulassen
    active = false
    dragging.value = false
    dragY.value = 0
  }
}
function onTouchEnd() {
  if (!active) return
  active = false
  dragging.value = false
  if (dragY.value > DISMISS_THRESHOLD_PX) {
    haptic()
    dragY.value = sheetRef.value?.offsetHeight ?? window.innerHeight // aus dem Bild ziehen
    setTimeout(() => emit('close'), 250)
  } else {
    dragY.value = 0 // zurueckfedern (transition greift, da !dragging)
  }
}

function go() {
  haptic()
  emit('close')
}

function onLogout() {
  haptic()
  emit('logout')
}
</script>

<template>
  <Transition name="more-sheet">
    <div v-if="props.open" class="fixed inset-0 z-50 md:hidden" @click.self="emit('close')">
      <!-- Backdrop -->
      <div class="absolute inset-0 bg-black/50" @click="emit('close')" />

      <!-- Sheet -->
      <div
        ref="sheetRef"
        class="more-sheet absolute bottom-0 left-0 right-0 bg-white dark:bg-gray-900 rounded-t-2xl shadow-[0_-4px_20px_rgba(0,0,0,0.25)] max-h-[80vh] overflow-y-auto pb-[env(safe-area-inset-bottom)]"
        :style="sheetStyle"
        role="dialog"
        aria-modal="true"
        :aria-label="t('nav.bottom.more')"
        @touchstart.passive="onTouchStart"
        @touchmove="onTouchMove"
        @touchend="onTouchEnd"
        @touchcancel="onTouchEnd"
      >
        <!-- Grabber + Header -->
        <div class="sticky top-0 bg-white dark:bg-gray-900 pt-3 px-4 pb-2">
          <div class="mx-auto mb-3 h-1 w-10 rounded-full bg-gray-300 dark:bg-gray-700" />
          <div class="flex items-center justify-between">
            <h2 class="text-base font-bold text-gray-900 dark:text-gray-100">{{ t('nav.bottom.more') }}</h2>
            <button
              type="button"
              class="p-1.5 rounded-full hover:bg-gray-100 dark:hover:bg-gray-800 transition"
              :aria-label="t('common.close')"
              @click="emit('close')"
            >
              <XMarkIcon class="h-5 w-5 text-gray-500 dark:text-gray-400" />
            </button>
          </div>
        </div>

        <!-- Nav-Items -->
        <nav class="px-2 py-1">
          <template v-for="item in items" :key="item.to">
            <router-link
              v-if="!(item.demoHidden && props.isDemo)"
              :to="item.to"
              class="flex items-center gap-3 px-3 py-3 rounded-lg text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition"
              @click="go"
            >
              <component :is="item.icon" class="h-5 w-5 text-gray-500 dark:text-gray-400 shrink-0" />
              <span class="text-sm font-medium">{{ t(item.label) }}</span>
            </router-link>
          </template>

          <a
            :href="props.feedbackMailto"
            class="flex items-center gap-3 px-3 py-3 rounded-lg text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition"
            @click="go"
          >
            <ChatBubbleLeftEllipsisIcon class="h-5 w-5 text-gray-500 dark:text-gray-400 shrink-0" />
            <span class="text-sm font-medium">{{ t('nav.bottom.feedback') }}</span>
          </a>
        </nav>

        <!-- Utilities -->
        <div class="border-t border-gray-200 dark:border-gray-800 mt-1 px-4 py-3 flex items-center gap-4">
          <LocaleSwitcher variant="public" />
          <ThemeToggle v-if="!props.isDemo" />
          <button
            v-if="!props.isDemo"
            type="button"
            class="ml-auto flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 transition"
            @click="onLogout"
          >
            <ArrowRightOnRectangleIcon class="h-5 w-5" />
            {{ t('nav.bottom.logout') }}
          </button>
        </div>

        <!-- Footer: Unterstuetzen (immer) + Rechtliches (nur native App - im Web liegen
             die Legal-Links im Seiten-Footer). -->
        <div class="border-t border-gray-200 dark:border-gray-800 px-4 py-3 flex flex-wrap justify-center items-center gap-x-4 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
          <SupportPopover variant="compact" />
          <template v-if="props.isNative">
            <router-link to="/impressum" class="hover:text-gray-700 dark:hover:text-gray-200 underline" @click="go">{{ t('footer.imprint') }}</router-link>
            <router-link to="/datenschutz" class="hover:text-gray-700 dark:hover:text-gray-200 underline" @click="go">{{ t('footer.privacy') }}</router-link>
            <router-link to="/agb" class="hover:text-gray-700 dark:hover:text-gray-200 underline" @click="go">{{ t('footer.terms') }}</router-link>
          </template>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.more-sheet-enter-active,
.more-sheet-leave-active {
  transition: opacity 0.2s ease;
}
.more-sheet-enter-active .more-sheet,
.more-sheet-leave-active .more-sheet {
  transition: transform 0.25s cubic-bezier(0.32, 0.72, 0, 1);
}
.more-sheet-enter-from,
.more-sheet-leave-to {
  opacity: 0;
}
.more-sheet-enter-from .more-sheet,
.more-sheet-leave-to .more-sheet {
  transform: translateY(100%);
}
</style>
