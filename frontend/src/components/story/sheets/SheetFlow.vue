<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import EnergyFlow from '../EnergyFlow.vue'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { EnergyFlow as Flow } from '../../../composables/storyEnergy'

/** Energiefluss gross, mit Zahlen zu Netz, Akku, Verlust, AC/DC und Abdeckung. */
const props = defineProps<{ flow: Flow }>()

const { t } = useI18n()
const { formatDecimal, formatDistance } = useLocaleFormat()

const labels = computed(() => ({
  grid: t('story.flow_grid'), battery: t('story.flow_battery'), road: t('story.flow_road'),
  loss: t('story.flow_loss_label'), home: t('story.flow_home'), public: t('story.flow_public'),
}))
const kwh = (v: number) => `${formatDecimal(v, 0)} kWh`
</script>

<template>
  <div>
    <p class="text-sm text-gray-600 dark:text-gray-300 mt-1">
      {{ t('story.sheet_flow_text', { grid: kwh(flow.gridKwh), vehicle: kwh(flow.vehicleKwh), loss: kwh(flow.lossKwh), pct: flow.lossPercent }) }}
    </p>
    <div class="mt-4">
      <EnergyFlow :flow="flow" :labels="labels" :road-value="flow.distanceKm ? formatDistance(flow.distanceKm) : ''" large />
    </div>
    <dl class="mt-3 grid grid-cols-2 gap-x-6 gap-y-1.5 text-sm tabular-nums">
      <dt class="text-gray-500 dark:text-gray-400">AC</dt><dd class="text-right text-gray-900 dark:text-gray-100">{{ kwh(flow.acKwh) }}</dd>
      <dt class="text-gray-500 dark:text-gray-400">DC</dt><dd class="text-right text-gray-900 dark:text-gray-100">{{ kwh(flow.dcKwh) }}</dd>
      <dt class="text-gray-500 dark:text-gray-400">{{ t('story.flow_home') }}</dt><dd class="text-right text-gray-900 dark:text-gray-100">{{ kwh(flow.privateKwh) }}</dd>
      <dt class="text-gray-500 dark:text-gray-400">{{ t('story.flow_public') }}</dt><dd class="text-right text-gray-900 dark:text-gray-100">{{ kwh(flow.publicKwh) }}</dd>
    </dl>
    <p v-if="flow.partial" class="mt-3 text-xs text-amber-700 dark:text-amber-400">
      {{ t('story.sheet_flow_partial', { covered: flow.coveredLogCount, total: flow.totalLogCount }) }}
    </p>
  </div>
</template>
