<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  XMarkIcon, TruckIcon, ArrowsRightLeftIcon, ArrowDownTrayIcon,
  Cog6ToothIcon,
  ArrowRightOnRectangleIcon, ChatBubbleLeftEllipsisIcon,
} from '@heroicons/vue/24/outline'
import LocaleSwitcher from './LocaleSwitcher.vue'
import SupportPopover from '../settings/SupportPopover.vue'
import BottomSheet from './BottomSheet.vue'
import { useHaptic } from '../../composables/useHaptic'
import { CI_MENU_ROW as ROW } from '../../utils/ciClasses'

/** Mehr-Menü der Bottom-Nav. Der Aufrufer hängt es an ein v-if und hört auf close, das erst nach der Schließ-Animation kommt. */
const props = defineProps<{
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

const ROW_ICON = 'h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400'

/** Link angetippt: Sheet über BottomSheet schließen, damit es wie beim X nach unten fährt. */
function go(close: () => void) {
  haptic()
  close()
}

function onLogout() {
  haptic()
  emit('logout')
}
</script>

<template>
  <BottomSheet edge :label="t('nav.bottom.more')" testid="more-sheet" panel-class="sm:max-w-md" @close="emit('close')">
    <template #default="{ close }">
      <div class="flex shrink-0 items-center justify-between pl-5 pr-2 pt-3 pb-1">
        <h2 class="text-base font-bold text-gray-900 dark:text-gray-100">{{ t('nav.bottom.more') }}</h2>
        <button
          type="button"
          class="grid h-11 w-11 place-items-center rounded-full text-gray-600 hover:bg-black/10 dark:text-gray-300 dark:hover:bg-white/10 transition-colors"
          :aria-label="t('common.close')"
          @click="close"
        >
          <XMarkIcon class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>

      <div class="overflow-y-auto">
        <nav class="px-2 pb-2">
          <template v-for="item in items" :key="item.to">
            <router-link v-if="!(item.demoHidden && props.isDemo)" :to="item.to" :class="ROW" @click="go(close)">
              <component :is="item.icon" :class="ROW_ICON" aria-hidden="true" />
              {{ t(item.label) }}
            </router-link>
          </template>
          <a :href="props.feedbackMailto" :class="ROW" @click="go(close)">
            <ChatBubbleLeftEllipsisIcon :class="ROW_ICON" aria-hidden="true" />
            {{ t('nav.bottom.feedback') }}
          </a>
        </nav>

        <div class="border-t border-gray-200 dark:border-white/10 px-4 py-2 flex items-center gap-3 text-gray-900 dark:text-gray-100">
          <LocaleSwitcher variant="public" />
          <SupportPopover variant="compact" />
          <button
            v-if="!props.isDemo"
            type="button"
            class="ml-auto flex min-h-11 items-center gap-2 rounded-full px-3 text-[15px] font-medium text-gray-900 hover:bg-black/10 dark:text-gray-100 dark:hover:bg-white/10 transition-colors"
            @click="onLogout"
          >
            <ArrowRightOnRectangleIcon :class="ROW_ICON" aria-hidden="true" />
            {{ t('nav.bottom.logout') }}
          </button>
        </div>

        <!-- Rechtliches nur in der nativen App, im Web liegen die Links im Seiten-Footer.
             Unterstützen blendet sich nativ selbst aus (iapPolicy). -->
        <div v-if="props.isNative" class="border-t border-gray-200 dark:border-white/10 px-4 py-3 flex flex-wrap justify-center items-center gap-x-4 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
          <router-link to="/impressum" class="hover:text-gray-700 dark:hover:text-gray-200 underline" @click="go(close)">{{ t('footer.imprint') }}</router-link>
          <router-link to="/datenschutz" class="hover:text-gray-700 dark:hover:text-gray-200 underline" @click="go(close)">{{ t('footer.privacy') }}</router-link>
          <router-link to="/agb" class="hover:text-gray-700 dark:hover:text-gray-200 underline" @click="go(close)">{{ t('footer.terms') }}</router-link>
        </div>
      </div>
    </template>
  </BottomSheet>
</template>
