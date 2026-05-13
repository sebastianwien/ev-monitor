<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { InformationCircleIcon, ChevronRightIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'

const props = withDefaults(defineProps<{
  /** Minimum complete trips before switching from WLTP-bootstrap to statistical check */
  minTrips?: number
  initialExpanded?: boolean
}>(), {
  minTrips: 5,
  initialExpanded: false
})

const { t } = useI18n()
const expanded = ref(props.initialExpanded)
</script>

<template>
  <div class="bg-gray-50 dark:bg-gray-900 border-2 border-gray-300 dark:border-gray-700 rounded-sm overflow-hidden shadow-[2px_2px_0_0_#9ca3af] dark:shadow-[2px_2px_0_0_#4b5563]">
    <button
      type="button"
      @click="expanded = !expanded"
      class="w-full flex items-center justify-between px-3 py-2.5 text-xs font-bold uppercase tracking-wider text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition text-left">
      <span class="flex items-center gap-1.5">
        <InformationCircleIcon class="w-4 h-4 text-indigo-500 flex-shrink-0" />
        {{ t('consumption_info.trigger') }}
      </span>
      <ChevronRightIcon class="w-4 h-4 flex-shrink-0 transition-transform duration-200"
        :class="expanded ? 'rotate-90' : ''" />
    </button>
    <div v-if="expanded" class="px-3 pb-3 text-sm font-medium text-gray-700 dark:text-gray-300 space-y-2 border-t-2 border-gray-300 dark:border-gray-700 pt-2.5">
      <p v-html="t('consumption_info.p1')" />
      <p>
        <span v-html="t('consumption_info.p2_pre', { minTrips })" />
        <span class="inline-flex items-center gap-0.5 px-1.5 py-0.5 bg-red-100 dark:bg-red-900/30 border-2 border-red-400 dark:border-red-700 rounded-sm text-xs text-red-700 dark:text-red-400 font-bold mx-1">
          <ExclamationTriangleIcon class="w-3 h-3" />
        </span>{{ t('consumption_info.p2_post') }}
      </p>
      <p v-html="t('consumption_info.p3')" class="border-t-2 border-gray-300 dark:border-gray-700 pt-2" />
      <p v-html="t('consumption_info.p4')" class="border-t-2 border-gray-300 dark:border-gray-700 pt-2" />
      <div class="border-t-2 border-gray-300 dark:border-gray-700 pt-2">
        <router-link
          to="/consumption-methodology"
          class="inline-flex items-center gap-1 text-indigo-600 dark:text-indigo-400 hover:underline font-bold uppercase tracking-wider text-[11px]">
          {{ t('consumption_info.methodology_link') }}
          <ChevronRightIcon class="w-3.5 h-3.5" />
        </router-link>
      </div>
    </div>
  </div>
</template>
