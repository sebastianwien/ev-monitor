<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/logFormData'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { type RequiredField } from './wizardLogic'
import OptionalPanel from './OptionalPanel.vue'
import type { CostMetric } from './costMetrics'
import { CARD, CARD_EDGE, CARD_SURFACE, CARD_MISSING } from './chipClass'

export type SummarySection = 'place' | 'energy' | 'vehicle' | 'cost' | 'time'

const props = defineProps<{
  placeLabel: string
  /** Pflichtwerte, die noch fehlen - ihre Kacheln zeigen "offen" statt eines Werts */
  missing?: RequiredField[]
  /** Bearbeiten: die Zeit steht im Kartenkopf, nicht unter "Mehr Details" */
  hideTime?: boolean
  /** Wizard: der Ort steht als Kartenkopf darüber, die Ort-Kachel entfällt und es bleiben vier im Raster */
  hidePlace?: boolean
  /** Wizard: "Mehr Details" nur mit Akku vorher, Ladedauer, Leistung - Zeit, Strecke, Reifen wurden in Schritt 2 gesetzt */
  detailsNumbersOnly?: boolean
  /** Kacheln beim Erscheinen nacheinander leicht von unten aufsteigen lassen (Prüfseite: "Ergebnis wird aufgedeckt") */
  reveal?: boolean
  /** ct/kWh, €/100 km, kWh/100 km für die Kosten-Kachel; ersetzt dort die Zeile "Ändern", die Kachel bleibt gleich hoch */
  costMetrics?: CostMetric[]
  /** Sprachlog: Werte, bei denen das Modell unsicher war - Kachel mit Wert, aber gelb umrandet */
  flagged?: RequiredField[]
}>()
const form = defineModel<LogFormData>({ required: true })
const emit = defineEmits<{ edit: [section: SummarySection] }>()
const { t } = useI18n()
const { formatDistance, formatCurrency, formatNumber } = useLocaleFormat()

/** Kosten-Kachel: Preis je kWh leise neben dem Betrag */
const perKwh = computed(() => props.costMetrics?.find(m => m.unit.endsWith('/kWh')) ?? null)
/** Je Kachel genau eine Kennzahlzeile: Kosten je 100 km auf die Kosten, Verbrauch je 100 km auf die Energie */
const per100 = computed(() => props.costMetrics?.filter(m => !m.unit.endsWith('/kWh')) ?? [])
const metricFor = (section: SummarySection) => per100.value.find(m => section === 'energy' ? m.unit.startsWith('kWh') : section === 'cost' && !m.unit.startsWith('kWh')) ?? null
const isMissing = (f: RequiredField) => props.missing?.includes(f) ?? false
const isFlagged = (tile: Tile) => !!tile.field && (props.flagged?.includes(tile.field) ?? false)
const energy = computed(() => {
  const v = form.value.kwhCharged ?? form.value.kwhAtVehicle
  return v == null ? null : `${formatNumber(v)} kWh`
})

interface Tile { label: string; value: string | null; section: SummarySection; testid: string; field?: RequiredField }
const tiles = computed<Tile[]>(() => [
  ...(props.hidePlace ? [] : [{ label: t('logwizard.place'), value: props.placeLabel, section: 'place' as SummarySection, testid: 'summary-place' }]),
  { label: t('logfields.energy'), value: isMissing('energy') ? null : energy.value, section: 'energy', testid: 'summary-energy', field: 'energy' },
  { label: t('logfields.odometer'), value: isMissing('odometer') || form.value.odometerKm == null ? null : formatDistance(form.value.odometerKm), section: 'vehicle', testid: 'summary-odometer', field: 'odometer' },
  { label: t('logfields.soc_after'), value: isMissing('soc') || form.value.socAfterChargePercent == null ? null : `${form.value.socAfterChargePercent} %`, section: 'vehicle', testid: 'summary-soc', field: 'soc' },
  { label: t('logfields.cost_eur'), value: isMissing('cost') || form.value.costEur == null ? null : formatCurrency(form.value.costEur), section: 'cost', testid: 'summary-cost', field: 'cost' },
])
</script>

<template>
  <div class="space-y-4">
    <div class="grid grid-cols-2 gap-2">
      <button v-for="(tile, i) in tiles" :key="tile.testid" type="button" :data-testid="tile.testid" @click="emit('edit', tile.section)"
        :style="reveal ? { animationDelay: `${i * 40}ms` } : undefined"
        :data-flagged="isFlagged(tile) || undefined"
        :class="[CARD, 'p-3', reveal && 'tile-rise', isFlagged(tile) ? 'border-amber-500 dark:border-amber-400' : CARD_EDGE, tile.value == null ? CARD_MISSING : CARD_SURFACE]">
        <span class="block truncate text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ tile.label }}</span>
        <!-- Gleicher Aufbau wie alle Kacheln: Label, Wert, eine graue Zeile. Kosten: der Preis je kWh steht leise neben dem Betrag. -->
        <span v-if="tile.value != null" class="flex items-baseline gap-1.5 min-w-0">
          <b class="text-[17px] font-bold tabular-nums text-gray-900 dark:text-gray-50 truncate">{{ tile.value }}</b>
          <span v-if="tile.section === 'cost' && perKwh" class="flex-shrink-0 text-xs tabular-nums text-gray-500 dark:text-gray-400">{{ perKwh.value }} {{ perKwh.unit }}</span>
        </span>
        <b v-else class="block text-[17px] font-bold text-amber-800 dark:text-amber-300">{{ t('logwizard.open') }}</b>
        <!-- Kosten: dritte Zeile zeigt die Kennzahlen, Wert dunkel und Einheit grau, Trenner als Punkt; Tippen ändert wie bei allen Kacheln -->
        <!-- Statt "Ändern" (die ganze Kachel ist der Button) die eine passende Kennzahl, einzeilig wie die übrigen Kacheln -->
        <span v-if="metricFor(tile.section)" :class="['block truncate text-xs tabular-nums', metricFor(tile.section)!.tone === 'notice' ? 'text-amber-700 dark:text-amber-400' : 'text-gray-500 dark:text-gray-400']">{{ metricFor(tile.section)!.value }} {{ metricFor(tile.section)!.unit }}</span>
        <span v-else class="text-xs text-gray-500 dark:text-gray-400">{{ t('logwizard.change') }}</span>
      </button>
    </div>

    <OptionalPanel v-model="form" :show-time="!hideTime" :numbers-only="detailsNumbersOnly" />
  </div>
</template>

<style scoped>
@keyframes tile-rise { from { opacity: 0; translate: 0 12px; } to { opacity: 1; translate: 0 0; } }
.tile-rise { animation: tile-rise 300ms ease-out both; }
@media (prefers-reduced-motion: reduce) { .tile-rise { animation: none; } }
</style>
