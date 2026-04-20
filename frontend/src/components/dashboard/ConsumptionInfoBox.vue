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
  <div class="bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-xl overflow-hidden">
    <button
      type="button"
      @click="expanded = !expanded"
      class="w-full flex items-center justify-between px-3 py-2.5 text-sm text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-600 transition text-left">
      <span class="flex items-center gap-1.5">
        <InformationCircleIcon class="w-4 h-4 text-indigo-400 flex-shrink-0" />
        {{ t('consumption_info.trigger') }}
      </span>
      <ChevronRightIcon class="w-4 h-4 flex-shrink-0 transition-transform duration-200"
        :class="expanded ? 'rotate-90' : ''" />
    </button>
    <div v-if="expanded" class="px-3 pb-3 text-sm text-gray-600 dark:text-gray-400 space-y-2 border-t border-gray-200 dark:border-gray-600 pt-2.5">
      <p v-html="t('consumption_info.p1')" />
      <p>
        <span v-html="t('consumption_info.p2_pre', { minTrips })" />
        <span class="inline-flex items-center gap-0.5 px-1.5 py-0.5 bg-red-100 dark:bg-red-900/30 border border-red-400 dark:border-red-700 rounded-full text-xs text-red-700 dark:text-red-400 font-medium mx-1">
          <ExclamationTriangleIcon class="w-3 h-3" />
        </span>{{ t('consumption_info.p2_post') }}
      </p>
      <p v-html="t('consumption_info.p3')" class="border-t border-gray-200 dark:border-gray-600 pt-2" />
      <p v-html="t('consumption_info.p4')" class="border-t border-gray-200 dark:border-gray-600 pt-2" />
      <div class="border-t border-gray-200 dark:border-gray-600 pt-2">
        <router-link
          to="/consumption-methodology"
          class="inline-flex items-center gap-1 text-indigo-500 dark:text-indigo-400 hover:underline font-medium">
          {{ t('consumption_info.methodology_link') }}
          <ChevronRightIcon class="w-3.5 h-3.5" />
        </router-link>
      </div>
    </div>
  </div>
</template>
