<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { OdometerGap } from '../../../composables/storyDay'

/**
 * Strecke ohne Aufzeichnung im Detail: von wann bis wann der Kilometerstand um wie viel gestiegen ist,
 * in Alltagssprache. Nichts zu bearbeiten, nur zurück bzw. schließen.
 */
const props = withDefaults(defineProps<{ gap: OdometerGap; dismiss?: 'close' | 'back' }>(), { dismiss: 'close' })
const emit = defineEmits<{ cancel: [] }>()
const { t, locale } = useI18n()
const { formatDistance } = useLocaleFormat()
const km = computed(() => formatDistance(props.gap.distanceKm, { round: false }))

const at = (iso: string) => new Date(iso).toLocaleString(locale.value, { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
const text = computed(() => t('storyday.gap_text', {
  from: at(props.gap.from), to: at(props.gap.to), km: km.value,
}))
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col bg-white dark:bg-gray-900" data-testid="gap-detail">
    <div class="flex shrink-0 items-center gap-1 border-b-2 border-gray-100 px-4 py-2 dark:border-gray-800">
      <button v-if="dismiss === 'back'" type="button" :aria-label="t('common.back')" @click="emit('cancel')"
        class="-ml-2 grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
        <ChevronLeftIcon class="h-5 w-5" />
      </button>
      <h2 class="min-w-0 flex-1 truncate text-[17px] font-bold text-gray-900 dark:text-gray-50">{{ t('storyday.gap_title') }}</h2>
      <button v-if="dismiss === 'close'" type="button" :aria-label="t('common.cancel')" @click="emit('cancel')"
        class="-mr-2 grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
        <XMarkIcon class="h-5 w-5" />
      </button>
    </div>
    <div class="flex-1 overflow-y-auto p-4">
      <p class="text-[26px] font-bold tabular-nums text-gray-900 dark:text-gray-50">{{ km }}</p>
      <p class="mt-2 text-[15px] leading-relaxed text-gray-700 dark:text-gray-200" data-testid="gap-text">{{ text }}</p>
    </div>
  </div>
</template>
