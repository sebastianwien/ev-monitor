<script setup lang="ts">
import { XMarkIcon } from '@heroicons/vue/24/outline'
import { useCountryStore } from '../../stores/country'
import { analytics } from '../../services/analytics'

const props = defineProps<{ dismissable?: boolean; personal?: boolean }>()
const emit = defineEmits<{ dismiss: [] }>()

const countryStore = useCountryStore()

function handleClick() {
  analytics.trackAffiliateBannerClicked('thg')
}

function handleDismiss(e: Event) {
  e.preventDefault()
  e.stopPropagation()
  emit('dismiss')
}
</script>

<template>
  <a
    v-if="countryStore.country === 'DE'"
    href="https://Geld-fuer-eAuto.de/ref/evmonitor"
    target="_blank"
    rel="noopener sponsored"
    class="relative flex items-center justify-between gap-3 bg-green-50 dark:bg-green-900/15 border border-green-100 dark:border-green-900/40 rounded-xl px-4 py-3 hover:bg-green-100 dark:hover:bg-green-900/25 transition-colors group mb-4"
    @click="handleClick"
  >
    <div class="flex items-center gap-3 min-w-0">
      <div class="min-w-0">
        <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 leading-snug">THG-Prämie schon beantragt?</p>
        <p class="text-xs text-gray-500 dark:text-gray-400 leading-snug mt-0.5">Falls nicht, kannst du das hier tun und gleichzeitig den Betrieb der Seite unterstützen. <span class="hidden md:inline font-medium text-green-600 dark:text-green-400">Bis zu 260 € dieses Jahr möglich.</span></p>
      </div>
    </div>
    <span class="shrink-0 text-xs font-semibold text-green-700 dark:text-green-400 group-hover:underline whitespace-nowrap">Jetzt beantragen →</span>
    <span class="absolute bottom-1 right-3 text-[10px] text-gray-300 dark:text-gray-600">{{ personal ? 'Affiliate-Link' : 'Anzeige' }}</span>
    <button
      v-if="dismissable"
      @click="handleDismiss"
      class="absolute -top-2 -right-2 h-5 w-5 rounded-full bg-green-200 hover:bg-green-300 text-green-700 flex items-center justify-center transition"
      title="Hinweis ausblenden"
    >
      <XMarkIcon class="h-3 w-3" />
    </button>
  </a>
</template>
