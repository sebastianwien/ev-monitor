<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { PeriodGroup } from '../../utils/tripPeriods'

/**
 * Tagesraster eines Zeitraums in der Sprache der Story-Tagesleiste (DataQualityLine): eine
 * blaue Saeule je Fahrtag, hoch wie die gefahrenen Kilometer, darunter ein Feld je Tag -
 * gruen, wenn geladen wurde, sonst grau. Zwei Aussagen, zwei Formen: ein eingefaerbter
 * Balken laese sich wie ein groesserer Wert. Die Tage teilen sich die volle Breite.
 *
 * In der Wochenansicht steht die Tagesleistung als Zahl direkt unter dem Tag - die Saeule
 * zeigt das Verhaeltnis, die Zahl den Wert. Im Monat fehlt dafuer der Platz.
 */
const props = defineProps<{
  bars: PeriodGroup['bars']
  month?: boolean
}>()

const { t, locale } = useI18n()
const { formatDistance } = useLocaleFormat()

const LOCALE_MAP: Record<string, string> = { en: 'en-GB', nb: 'nb-NO', sv: 'sv-SE' }
const dateLocale = computed(() => LOCALE_MAP[locale.value] ?? 'de-DE')

/** Hoehe der hoechsten Saeule in Pixeln - praesent, ohne den Kopf zu dominieren. */
const BAR_HEIGHT_PX = 40

/**
 * Die Saeulen des Rasters.
 *
 * Der hoechste Tag gibt den Massstab, sonst braeuchte es eine Achse. Ein gefahrener Tag ist
 * immer mindestens drei Pixel hoch: ein Kilometer neben zweihundert waere sonst unsichtbar,
 * obwohl er stattgefunden hat.
 */
const dayBars = computed(() => {
  const raw = props.bars
  if (!raw?.length) return []
  const max = Math.max(...raw.map((bar) => bar.km), 1)

  return raw.map((bar) => {
    const date = new Date(`${bar.dateKey}T12:00:00`)
    const isMonday = date.getDay() === 1
    return {
      ...bar,
      height: bar.km > 0 ? Math.max(3, Math.round((bar.km / max) * BAR_HEIGHT_PX)) : 0,
      // Im Monat waeren 31 Wochentagskuerzel Grafik statt Beschriftung - dort nur die
      // Datumszahl an den Wochenanfaengen, dazu ein Haarstrich als Wochengrenze.
      label: props.month
        ? (isMonday || date.getDate() === 1 ? String(date.getDate()) : '')
        : date.toLocaleDateString(dateLocale.value, { weekday: 'short' }).replace(/\.$/, ''),
      separator: props.month && isMonday,
      title: [
        date.toLocaleDateString(dateLocale.value, { weekday: 'short', day: 'numeric', month: 'numeric' }),
        formatDistance(bar.km),
        bar.charged ? t('logs.period.charges', { count: 1 }, 1) : '',
      ].filter(Boolean).join(' · '),
    }
  })
})

</script>

<template>
  <div v-if="dayBars.length" class="w-full">
    <div class="flex items-end" :class="month ? 'gap-[2px] md:gap-[3px]' : 'gap-1.5'">
      <template v-for="bar in dayBars" :key="bar.dateKey">
        <span v-if="bar.separator" class="w-px self-stretch bg-gray-200 dark:bg-gray-700" />
        <span class="flex min-w-0 flex-1 flex-col items-stretch gap-[2px]" :title="bar.title">
          <span class="flex flex-col justify-end" :style="{ height: BAR_HEIGHT_PX + 'px' }">
            <i v-if="bar.height" class="block rounded-[2px] bg-sky-500 dark:bg-sky-400" :style="{ height: bar.height + 'px' }" />
          </span>
          <i class="block h-1.5 rounded-[2px]" :class="bar.charged ? 'bg-green-700 dark:bg-green-400' : 'bg-gray-200 dark:bg-gray-700'" />
          <span class="h-3 text-center text-[10.5px] leading-3 text-gray-400 tabular-nums whitespace-nowrap dark:text-gray-500"
            :class="month ? 'text-left' : ''">{{ bar.label }}</span>
          <span v-if="!month" class="h-3 text-center text-[10.5px] leading-3 tabular-nums"
                :class="bar.km > 0 ? 'font-semibold text-gray-700 dark:text-gray-300' : 'text-gray-300 dark:text-gray-600'">
            {{ bar.km > 0 ? formatDistance(bar.km, { showUnit: false }) : '·' }}
          </span>
        </span>
      </template>
    </div>
  </div>
</template>
