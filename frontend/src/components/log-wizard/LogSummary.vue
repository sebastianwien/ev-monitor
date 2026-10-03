<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/logFormData'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { type RequiredField } from './wizardLogic'
import OptionalPanel from './OptionalPanel.vue'
import type { CostMetric } from './costMetrics'

export type SummarySection = 'place' | 'energy' | 'vehicle' | 'cost' | 'time'

const props = defineProps<{
  placeLabel: string
  /** Pflichtwerte, die noch fehlen - ihre Kacheln zeigen "offen" statt eines Werts */
  missing?: RequiredField[]
  /** Zeit als eigene Kachel (Bearbeiten); beim Anlegen steht sie unter "Mehr Details" */
  showTimeTile?: boolean
  /** Wizard: der Ort steht als Kartenkopf darüber, die Ort-Kachel entfällt und es bleiben vier im Raster */
  hidePlace?: boolean
  /** Wizard: "Mehr Details" nur mit Akku vorher, Ladedauer, Leistung - Zeit, Strecke, Reifen wurden in Schritt 2 gesetzt */
  detailsNumbersOnly?: boolean
  /** Kacheln beim Erscheinen nacheinander leicht von unten aufsteigen lassen (Prüfseite: "Ergebnis wird aufgedeckt") */
  reveal?: boolean
  /** ct/kWh, €/100 km, kWh/100 km für die Kosten-Kachel; ersetzt dort die Zeile "Ändern", die Kachel bleibt gleich hoch */
  costMetrics?: CostMetric[]
}>()
const form = defineModel<LogFormData>({ required: true })
const emit = defineEmits<{ edit: [section: SummarySection] }>()
const { t, locale } = useI18n()
const { formatDistance, formatCurrency, formatNumber } = useLocaleFormat()

const timeLabel = computed(() => {
  if (!form.value.loggedAt) return t('logwizard.time_now')
  return new Date(form.value.loggedAt).toLocaleString(locale.value === 'en' ? 'en-GB' : 'de-DE', { day: 'numeric', month: 'numeric', hour: '2-digit', minute: '2-digit' })
})

/** Kosten-Kachel: Preis je kWh in die Kopfzeile, die beiden Werte je 100 km in die dritte Zeile */
const perKwh = computed(() => props.costMetrics?.find(m => m.unit.endsWith('/kWh')) ?? null)
const per100 = computed(() => props.costMetrics?.filter(m => !m.unit.endsWith('/kWh')) ?? [])
const isMissing = (f: RequiredField) => props.missing?.includes(f) ?? false
const energy = computed(() => {
  const v = form.value.kwhCharged ?? form.value.kwhAtVehicle
  return v == null ? null : `${formatNumber(v)} kWh`
})

interface Tile { label: string; value: string | null; section: SummarySection; testid: string }
const tiles = computed<Tile[]>(() => [
  ...(props.hidePlace ? [] : [{ label: t('logwizard.place'), value: props.placeLabel, section: 'place' as SummarySection, testid: 'summary-place' }]),
  ...(props.showTimeTile ? [{ label: t('logfields.timestamp'), value: timeLabel.value, section: 'time' as SummarySection, testid: 'summary-time' }] : []),
  { label: t('logfields.energy'), value: isMissing('energy') ? null : energy.value, section: 'energy', testid: 'summary-energy' },
  { label: t('logfields.odometer'), value: isMissing('odometer') || form.value.odometerKm == null ? null : formatDistance(form.value.odometerKm), section: 'vehicle', testid: 'summary-odometer' },
  { label: t('logfields.soc_after'), value: isMissing('soc') || form.value.socAfterChargePercent == null ? null : `${form.value.socAfterChargePercent} %`, section: 'vehicle', testid: 'summary-soc' },
  { label: t('logfields.cost_eur'), value: isMissing('cost') || form.value.costEur == null ? null : formatCurrency(form.value.costEur), section: 'cost', testid: 'summary-cost' },
])
</script>

<template>
  <div class="space-y-4">
    <div class="grid grid-cols-2 gap-2">
      <button v-for="(tile, i) in tiles" :key="tile.testid" type="button" :data-testid="tile.testid" @click="emit('edit', tile.section)"
        :style="reveal ? { animationDelay: `${i * 40}ms` } : undefined"
        :class="['btn-3d text-left p-3 rounded-sm transition', reveal && 'tile-rise', tile.value == null ? 'bg-amber-50 dark:bg-amber-900/20 ring-1 ring-inset ring-amber-300 dark:ring-amber-700 hover:bg-amber-100 dark:hover:bg-amber-900/40' : 'bg-gray-100 dark:bg-gray-700/60 hover:bg-gray-200 dark:hover:bg-gray-700']">
        <span class="block truncate text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ tile.label }}</span>
        <!-- Gleicher Aufbau wie alle Kacheln: Label, Wert, eine graue Zeile. Kosten: der Preis je kWh steht leise neben dem Betrag. -->
        <span v-if="tile.value != null" class="flex items-baseline gap-1.5 min-w-0">
          <b class="text-base font-semibold tabular-nums text-gray-800 dark:text-gray-100 truncate">{{ tile.value }}</b>
          <span v-if="tile.section === 'cost' && perKwh" class="flex-shrink-0 text-xs tabular-nums text-gray-500 dark:text-gray-400">{{ perKwh.value }} {{ perKwh.unit }}</span>
        </span>
        <b v-else class="block text-base font-semibold text-amber-700 dark:text-amber-300">{{ t('logwizard.open') }}</b>
        <!-- Kosten: dritte Zeile zeigt die Kennzahlen, Wert dunkel und Einheit grau, Trenner als Punkt; Tippen ändert wie bei allen Kacheln -->
        <!-- Kosten: Kennzahlen je 100 km in Kachel-Schrift (text-xs), umbrechend statt abgeschnitten -->
        <span v-if="tile.section === 'cost' && per100.length" class="flex flex-wrap gap-x-2 text-xs tabular-nums text-gray-500 dark:text-gray-400">
          <span v-for="m in per100" :key="m.unit" :class="['whitespace-nowrap', m.tone === 'notice' && 'text-amber-600 dark:text-amber-400']">{{ m.value }} {{ m.unit }}</span>
        </span>
        <span v-else class="text-xs text-indigo-600 dark:text-indigo-300">{{ t('logwizard.change') }}</span>
      </button>
    </div>

    <OptionalPanel v-model="form" :show-time="!showTimeTile" :numbers-only="detailsNumbersOnly" />
  </div>
</template>

<style scoped>
@keyframes tile-rise { from { opacity: 0; translate: 0 12px; } to { opacity: 1; translate: 0 0; } }
.tile-rise { animation: tile-rise 300ms ease-out both; }
@media (prefers-reduced-motion: reduce) { .tile-rise { animation: none; } }
</style>
