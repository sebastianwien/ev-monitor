<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { ChargingTypeSplit } from '../../../composables/useDashboardStats'

/** Normal- gegen Schnellladen: kWh und Preis je kWh, dazu was eine kWh schnell mehr kostet. */
const props = defineProps<{ split: ChargingTypeSplit }>()
const { t } = useI18n()
const { formatDecimal, formatCurrency } = useLocaleFormat()

const rows = computed(() => [
  { key: 'ac', label: t('story.charging_ac'), kwh: props.split.acKwh, eur: props.split.acEurPerKwh ?? null, cls: 'text-green-700 dark:text-green-400' },
  { key: 'dc', label: t('story.charging_dc'), kwh: props.split.dcKwh, eur: props.split.dcEurPerKwh ?? null, cls: 'text-sky-700 dark:text-sky-400' },
])
const extra = computed(() => {
  const { acEurPerKwh: ac, dcEurPerKwh: dc } = props.split
  return ac != null && dc != null && dc > ac ? dc - ac : null
})
</script>

<template>
  <div data-testid="sheet-charging">
    <p v-if="extra != null" class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ t('story.sheet_charging_extra', { eur: formatCurrency(extra) }) }}</p>
    <dl class="mt-4 grid grid-cols-[1fr_auto_auto] gap-x-5 gap-y-2 text-sm tabular-nums">
      <template v-for="r in rows" :key="r.key">
        <dt class="font-semibold" :class="r.cls">{{ r.label }}</dt>
        <dd class="text-right text-gray-900 dark:text-gray-100">{{ formatDecimal(r.kwh, 0) }} kWh</dd>
        <dd class="text-right text-gray-900 dark:text-gray-100">{{ r.eur != null ? t('story.per_kwh', { eur: formatCurrency(r.eur) }) : '–' }}</dd>
      </template>
    </dl>
    <p v-if="split.unknownKwh > 0" class="mt-3 text-xs text-gray-500 dark:text-gray-400">{{ t('story.sheet_charging_unknown', { kwh: formatDecimal(split.unknownKwh, 0) }) }}</p>
  </div>
</template>
