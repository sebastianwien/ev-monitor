<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { LockClosedIcon } from '@heroicons/vue/24/outline'
import FlowBar from '../FlowBar.vue'
import SheetStandby from './SheetStandby.vue'
import UseBar from '../UseBar.vue'
import { USE_STYLE, LOCKED_CLS } from '../energyUseStyle'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'
import type { EnergyFlow as Flow, EnergyUse, UseKey } from '../../../composables/storyEnergy'
import type { StandbyGap } from '../../../composables/storyInsights'

/**
 * Oben, sobald die Fahrten Energiedaten haben: Fahren, Stand und Laden mit Herkunft und Euro, dazu die größten Standzeiten.
 * Darunter, mit gemessenen Ladungen, der Netzstrom: Balken gross, Netz, Akku, Verlust, AC/DC und Abdeckung.
 */
const props = withDefaults(defineProps<{ flow?: Flow; use?: EnergyUse; standbyGaps?: StandbyGap[] }>(), { standbyGaps: () => [] })

const { t } = useI18n()
const { formatDecimal, formatCurrency } = useLocaleFormat()
const kwh = (v: number) => `${formatDecimal(v, 0)} kWh`
// Im Sheet mit Cent, auf der Kachel ganze Euro
const eur = (v: number) => formatCurrency(v)

const eurLine = computed(() => {
  const u = props.use
  if (!u || u.chargeEur == null) return ''
  return u.standbyEur == null
    ? t('story.sheet_use_eur_charge', { charge: eur(u.chargeEur) })
    : t('story.sheet_use_eur', { standby: eur(u.standbyEur), charge: eur(u.chargeEur) })
})

const rows = computed(() => {
  const u = props.use
  if (!u) return []
  const row = (key: UseKey, kwh: string | null, sub: string) => ({
    key, kwh, sub, label: t(USE_STYLE[key].label),
    pct: u.shares.find(s => s.key === key)?.pct ?? null,
    dot: kwh == null ? LOCKED_CLS : USE_STYLE[key].cls,
  })
  const est = u.estimatedCharges > 0
  return [
    row('drive', formatDecimal(u.driveKwh, 1), t('story.sheet_use_drive_sub', { n: u.trips }, u.trips)),
    u.standbyKwh == null
      ? row('standby', null, t('story.sheet_use_standby_locked'))
      : row('standby', formatDecimal(u.standbyKwh, 1), t('story.sheet_use_standby_sub')),
    row('charge', `${est ? '~' : ''}${formatDecimal(u.chargeLossKwh, 1)}`, est
      ? t('story.sheet_use_charge_estimated', { n: u.charges, e: u.estimatedCharges })
      : t('story.sheet_use_charge_sub', { n: u.charges }, u.charges)),
  ]
})
</script>

<template>
  <div>
    <template v-if="use">
      <p v-if="eurLine" class="text-sm text-gray-600 dark:text-gray-300 mt-1">{{ eurLine }}</p>
      <div class="mt-4">
        <UseBar :use="use" large />
      </div>
      <ul class="mt-3 divide-y divide-gray-100 dark:divide-gray-800">
        <li v-for="r in rows" :key="r.key" class="flex items-start gap-3 py-2.5">
          <i class="mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full" :class="r.dot" aria-hidden="true" />
          <div class="min-w-0 flex-1">
            <div class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ r.label }}</div>
            <div class="text-[13px] text-gray-500 dark:text-gray-400">{{ r.sub }}</div>
          </div>
          <div v-if="r.kwh != null" class="text-right tabular-nums">
            <div class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ r.kwh }} kWh</div>
            <div v-if="r.pct != null" class="text-[13px] text-gray-500 dark:text-gray-400">{{ r.pct }} %</div>
          </div>
          <LockClosedIcon v-else class="mt-0.5 h-4 w-4 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
        </li>
      </ul>
      <template v-if="standbyGaps.length">
        <h4 class="mt-5 text-[11.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400">{{ t('story.sheet_use_standby_gaps') }}</h4>
        <SheetStandby :gaps="standbyGaps" />
      </template>
    </template>

    <template v-if="flow">
      <h4 v-if="use" class="mt-5 text-[11.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400">{{ t('story.sheet_use_charged') }}</h4>
      <p class="text-sm text-gray-600 dark:text-gray-300 mt-1">
        {{ t('story.sheet_flow_text', { grid: kwh(flow.gridKwh), vehicle: kwh(flow.vehicleKwh), loss: kwh(flow.lossKwh), pct: flow.lossPercent }) }}
      </p>
      <div class="mt-4">
        <FlowBar :flow="flow" large />
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
    </template>
  </div>
</template>
