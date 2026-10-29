<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { BoltIcon, PencilSquareIcon } from '@heroicons/vue/24/outline'
import LogbookMonthHead from './LogbookMonthHead.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { chargeKwh, chargePlace } from '../../composables/storyDay'
import { chargePricePerKwh } from '../../composables/useChargingEfficiency'
import type { ChargeLog, ChargeMonth } from '../../utils/logbook'

/**
 * Logbuch für Autos ohne Fahrten: eine Ladung je Zeile, dazwischen die Strecke aus den km-Ständen. Die Höhe
 * der Strecke folgt den Tagen dazwischen, so wird der Laderhythmus sichtbar; der Monatskopf sagt ihn als Satz.
 * Fehlt etwas, steht dort die Handlung in Amber (Preis, km-Stand).
 */
defineProps<{ months: ChargeMonth[]; selectedId: string | null }>()
const emit = defineEmits<{ select: [log: ChargeLog]; amend: [log: ChargeLog]; fix: [logId: string] }>()
const { t, locale } = useI18n()
const { formatCurrency, formatDecimal, formatDistance, formatConsumption, formatCostPerKwh } = useLocaleFormat()

const AMBER_CHIP = 'inline-flex min-h-8 items-center gap-1 whitespace-nowrap rounded-full bg-amber-100 px-2.5 text-[12px] font-semibold text-amber-800 transition-colors hover:bg-amber-200 dark:bg-amber-400/15 dark:text-amber-300 dark:hover:bg-amber-400/25'

const line = (m: ChargeMonth) => [
  t('storyday.pill_charges', { n: m.totals.charges }, m.totals.charges),
  m.totals.kwh ? `${formatDecimal(m.totals.kwh, 1)} kWh` : null,
  m.totals.eur ? formatCurrency(m.totals.eur) : null,
].filter(Boolean).join(' · ')
const dateParts = (iso: string) => {
  const d = new Date(iso)
  const loc = locale.value === 'en' ? 'en-GB' : locale.value
  return {
    day: d.toLocaleDateString(loc, { day: '2-digit', month: '2-digit' }),
    sub: `${d.toLocaleDateString(loc, { weekday: 'short' }).replace(/\.$/, '')} ${d.toLocaleTimeString(loc, { hour: '2-digit', minute: '2-digit' })}`,
  }
}
function placeName(log: ChargeLog) {
  const p = chargePlace(log)
  return p.kind === 'operator' ? p.name : t(`storyday.place_${p.kind}`)
}
function chargeSub(log: ChargeLog) {
  const k = chargeKwh(log)
  const type = log.chargingType && log.chargingType !== 'UNKNOWN' ? log.chargingType : null
  return [type, k != null ? `${formatDecimal(k, 1)} kWh` : null].filter(Boolean).join(' · ')
}
const pricePerKwh = (log: ChargeLog) => { const p = chargePricePerKwh(log); return p != null ? formatCostPerKwh(p) : null }
/** Mehr Tage, längere Strecke; gedeckelt, damit ein Monat Pause nicht den Bildschirm füllt */
const stretchHeight = (days: number) => `${Math.min(150, 44 + days * 11)}px`
</script>

<template>
  <section v-for="m in months" :key="m.key" data-testid="chargebook-month">
    <LogbookMonthHead :month-key="m.key" :line="line(m)">
      <p v-if="m.rhythmDays != null" class="mt-0.5 text-[13px] font-semibold" data-testid="chargebook-rhythm">
        {{ t('logbook.rhythm', { n: m.rhythmDays }, m.rhythmDays) }}
      </p>
    </LogbookMonthHead>
    <ul class="py-2">
      <template v-for="e in m.entries" :key="e.key">
        <li v-if="e.kind === 'charge'">
          <div class="flex items-start gap-3 px-4 py-2.5 transition-colors"
            :class="e.log.id === selectedId ? 'bg-green-50 dark:bg-green-400/10' : 'hover:bg-gray-50 dark:hover:bg-white/5'">
            <button type="button" class="flex min-w-0 flex-1 items-start gap-3 text-left" data-testid="chargebook-row"
              :aria-current="e.log.id === selectedId" @click="emit('select', e.log)">
              <span class="w-14 shrink-0 pt-0.5 text-[13px] font-semibold leading-tight tabular-nums">
                {{ dateParts(e.log.loggedAt).day }}
                <span class="block text-[12px] font-normal text-gray-500 dark:text-gray-400">{{ dateParts(e.log.loggedAt).sub }}</span>
              </span>
              <span class="grid h-9 w-9 shrink-0 place-items-center rounded-full border-2 border-edge bg-green-600 text-white dark:border-transparent dark:bg-green-500 dark:text-gray-950">
                <BoltIcon class="h-[18px] w-[18px]" aria-hidden="true" />
              </span>
              <span class="min-w-0 flex-1 pt-0.5">
                <span class="block truncate text-[15px] font-semibold leading-snug">{{ placeName(e.log) }}</span>
                <span class="block text-[13px] tabular-nums text-gray-500 dark:text-gray-400">{{ chargeSub(e.log) }}</span>
              </span>
              <span v-if="e.log.costEur != null" class="shrink-0 pt-0.5 text-right tabular-nums">
                <b class="block text-[15px] font-semibold">{{ formatCurrency(e.log.costEur) }}</b>
                <span v-if="pricePerKwh(e.log)" class="block text-[12px] text-gray-500 dark:text-gray-400">{{ pricePerKwh(e.log) }}</span>
              </span>
            </button>
            <!-- Preis fehlt: führt zum Nachtragen, wie in der alten Liste -->
            <button v-if="e.log.costEur == null && !e.log.priceUnknown" type="button" :class="AMBER_CHIP" class="mt-0.5 shrink-0"
              data-testid="chargebook-amend" @click="emit('amend', e.log)">
              <PencilSquareIcon class="h-3.5 w-3.5" aria-hidden="true" />{{ t('storyday.missing_price') }}
            </button>
          </div>
        </li>
        <li v-else class="flex items-center gap-3 px-4" :style="{ height: stretchHeight(e.days) }" data-testid="chargebook-stretch">
          <span class="w-14 shrink-0 text-[12px] font-semibold tabular-nums text-gray-500 dark:text-gray-400">{{ e.days ? t('logbook.days', { n: e.days }, e.days) : t('logbook.same_day') }}</span>
          <span class="flex h-full w-9 shrink-0 justify-center" aria-hidden="true"><span class="h-full border-l-2 border-dashed border-gray-300 dark:border-gray-600" /></span>
          <span class="min-w-0 flex-1">
            <template v-if="e.km != null">
              <b class="block text-[14px] font-semibold tabular-nums">{{ t('logbook.driven', { km: formatDistance(e.km, { round: true }) }) }}</b>
              <span class="block text-[13px] tabular-nums text-gray-500 dark:text-gray-400">
                {{ [e.kwhPer100km != null ? formatConsumption(e.kwhPer100km) : null, e.eurPer100km != null ? t('logbook.per_100km', { cost: formatCurrency(e.eurPer100km) }) : null].filter(Boolean).join(' · ') }}
              </span>
            </template>
            <template v-else-if="e.fix">
              <button type="button" :class="AMBER_CHIP" data-testid="chargebook-fix" @click="emit('fix', e.fix)">
                <PencilSquareIcon class="h-3.5 w-3.5" aria-hidden="true" />{{ t('logbook.add_odometer') }}
              </button>
              <span class="mt-1 block text-[12px] text-gray-500 dark:text-gray-400">{{ t('logbook.add_odometer_why') }}</span>
            </template>
          </span>
        </li>
      </template>
    </ul>
  </section>
</template>
