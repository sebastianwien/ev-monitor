<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { InformationCircleIcon, ChevronDownIcon, ChevronRightIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import Collapse from '../shared/Collapse.vue'

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
  <!-- Nachschlagestoff in der App-CI: ruhige Flaeche, Aufklappen mit Collapse und drehendem Chevron. -->
  <div class="rounded-2xl bg-gray-50 dark:bg-white/5">
    <button
      type="button"
      @click="expanded = !expanded"
      :aria-expanded="expanded"
      class="flex min-h-14 w-full items-center justify-between gap-3 rounded-2xl px-3 py-2.5 text-left transition-colors hover:bg-gray-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:hover:bg-white/10">
      <span class="flex items-center gap-3">
        <span class="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-white text-gray-600 dark:bg-white/10 dark:text-gray-300">
          <InformationCircleIcon class="h-[18px] w-[18px]" aria-hidden="true" />
        </span>
        <span class="text-[14px] font-semibold text-gray-900 dark:text-gray-100">{{ t('consumption_info.trigger') }}</span>
      </span>
      <ChevronDownIcon class="h-4 w-4 shrink-0 text-gray-400 transition-transform duration-300 ease-out"
        :class="expanded ? 'rotate-180' : ''" aria-hidden="true" />
    </button>
    <Collapse :open="expanded">
      <div class="space-y-2.5 px-4 pb-4 pt-1 text-[14px] leading-relaxed text-gray-700 dark:text-gray-300">
        <p v-html="t('consumption_info.p1')" />
        <p>
          <span v-html="t('consumption_info.p2_pre', { minTrips })" />
          <ExclamationTriangleIcon class="mx-1 inline-block h-4 w-4 align-text-bottom text-amber-700 dark:text-amber-400" aria-hidden="true" />{{ t('consumption_info.p2_post') }}
        </p>
        <p v-html="t('consumption_info.p3')" class="border-t border-gray-200 pt-2.5 dark:border-gray-700" />
        <p v-html="t('consumption_info.p4')" class="border-t border-gray-200 pt-2.5 dark:border-gray-700" />
        <p v-html="t('consumption_info.p5_distance_estimate')" class="border-t border-gray-200 pt-2.5 dark:border-gray-700" />
        <div class="border-t border-gray-200 pt-2.5 dark:border-gray-700">
          <router-link
            to="/consumption-methodology"
            class="inline-flex items-center gap-1 font-semibold text-gray-900 underline underline-offset-4 dark:text-gray-100">
            {{ t('consumption_info.methodology_link') }}
            <ChevronRightIcon class="h-3.5 w-3.5" aria-hidden="true" />
          </router-link>
        </div>
      </div>
    </Collapse>
  </div>
</template>
