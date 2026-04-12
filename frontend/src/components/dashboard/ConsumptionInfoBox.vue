<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { InformationCircleIcon, ChevronRightIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'

const props = withDefaults(defineProps<{
  /** Minimum complete trips before switching from WLTP-bootstrap to statistical check */
  minTrips?: number
}>(), {
  minTrips: 5
})

const { t } = useI18n()
const expanded = ref(false)
</script>

<template>
  <div class="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
    <button
      type="button"
      @click="expanded = !expanded"
      class="w-full flex items-center justify-between px-3 py-2.5 text-sm text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700 transition text-left">
      <span class="flex items-center gap-1.5">
        <InformationCircleIcon class="w-4 h-4 text-indigo-400 flex-shrink-0" />
        {{ t('consumption_info.trigger') }}
      </span>
      <ChevronRightIcon class="w-4 h-4 flex-shrink-0 transition-transform duration-200"
        :class="expanded ? 'rotate-90' : ''" />
    </button>
    <div v-if="expanded" class="px-3 pb-3 text-sm text-gray-600 dark:text-gray-400 space-y-2 border-t border-gray-100 dark:border-gray-700 pt-2.5">
      <p v-html="t('consumption_info.p1')" />
      <p>
        <span v-html="t('consumption_info.p2_pre', { minTrips })" />
        <span class="inline-flex items-center gap-0.5 px-1.5 py-0.5 bg-red-100 border border-red-400 rounded-full text-xs text-red-700 font-medium mx-1">
          <ExclamationTriangleIcon class="w-3 h-3" />
        </span>{{ t('consumption_info.p2_post') }}
      </p>
      <p v-html="t('consumption_info.p3')" class="border-t border-gray-100 dark:border-gray-700 pt-2" />
    </div>
  </div>
</template>
