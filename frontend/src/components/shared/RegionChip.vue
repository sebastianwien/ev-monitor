<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCountryStore } from '../../stores/country'
import type { CountryCode } from '../../config/unitSystems'
import { GlobeAltIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { COUNTRY_OPTIONS } from '../../config/countries'

const { t, locale } = useI18n()
const countryStore = useCountryStore()

const open = ref(false)
const chipRef = ref<HTMLElement | null>(null)

const currentCountry = computed(() =>
  COUNTRY_OPTIONS.find(c => c.code === countryStore.country)
)

function select(code: CountryCode) {
  countryStore.setCountry(code)
  open.value = false
}

function handleOutsideClick(e: MouseEvent) {
  if (chipRef.value && !chipRef.value.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', handleOutsideClick))
onBeforeUnmount(() => document.removeEventListener('click', handleOutsideClick))
</script>

<template>
  <div ref="chipRef" class="relative inline-block" @keydown.escape="open = false">
    <button
      @click.stop="open = !open"
      class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium
             bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300
             hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors border border-gray-200 dark:border-gray-600"
    >
      <GlobeAltIcon class="h-3.5 w-3.5" />
      <span>{{ t('region_chip.wrong') }}</span>
      <span v-if="currentCountry">{{ currentCountry.flag }} {{ currentCountry.name[locale] || currentCountry.name.en }}</span>
    </button>

    <!-- Dropdown - opens upward so it doesn't go off-screen in footer -->
    <div
      v-if="open"
      class="absolute z-50 bottom-full mb-1 right-0 w-64 bg-white dark:bg-gray-800 rounded-xl shadow-xl border border-gray-200 dark:border-gray-700 p-3"
    >
      <div class="flex items-center justify-between mb-2.5">
        <span class="text-xs font-semibold text-gray-700 dark:text-gray-300 uppercase tracking-wide">
          {{ t('region_chip.title') }}
        </span>
        <button @click="open = false" class="p-0.5 rounded hover:bg-gray-100 dark:hover:bg-gray-700">
          <XMarkIcon class="h-4 w-4 text-gray-400" />
        </button>
      </div>
      <div class="grid grid-cols-2 gap-1.5">
        <button
          v-for="c in COUNTRY_OPTIONS"
          :key="c.code"
          @click="select(c.code)"
          class="flex items-center gap-2 px-2.5 py-2 rounded-lg text-sm transition-colors text-left"
          :class="countryStore.country === c.code
            ? 'bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-400 font-medium border border-green-300 dark:border-green-700'
            : 'text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'"
        >
          <span class="text-base">{{ c.flag }}</span>
          <span class="text-xs">{{ c.name[locale] || c.name.en }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
