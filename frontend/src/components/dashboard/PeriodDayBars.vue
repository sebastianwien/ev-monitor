<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import type { PeriodGroup } from '../../utils/tripPeriods'

/**
 * Tagesraster eines Zeitraums: eine Saeule je Kalendertag, hoch wie die gefahrenen
 * Kilometer. Ob an einem Tag geladen wurde, sagt ein indigofarbener Sockel am Fuss der
 * Saeule - zwei Aussagen brauchen zwei Formen, sonst liest sich ein eingefaerbter Balken
 * wie ein groesserer Wert. Der Sockel funktioniert in jeder Saeulenbreite und -hoehe,
 * wo ein Icon in der Saeule es nicht taete.
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
const BAR_HEIGHT_PX = 52

/** Hoehe des Lade-Sockels am Fuss einer Saeule. */
const CHARGE_BASE_PX = 4

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

const maxKm = computed(() => Math.max(...(props.bars ?? []).map((bar) => bar.km), 0))
</script>

<template>
  <!-- Feste Saeulenbreite statt flex-1: ueber die volle Zeile gestreckt liest sich eine
       Saeule wie ein Fortschrittsbalken, nicht wie ein Messwert. w-min: die Saeulenreihe
       bestimmt die Breite, die Legende bricht darunter um - sonst zieht ihre einzeilige
       Max-Content-Breite das shrink-0-Flex-Item breiter als die Karte (Scrollbalken). -->
  <div v-if="dayBars.length" class="w-min">
    <!-- Ab md: breitere Monats-Saeulen - das Raster darf auf grossen Screens praesenter
         sein, aber nicht auf volle Kartenbreite gestreckt (siehe Kopfkommentar). -->
    <div class="flex items-end" :class="month ? 'gap-px md:gap-[3px]' : 'gap-1'">
      <template v-for="bar in dayBars" :key="bar.dateKey">
        <span v-if="bar.separator" class="w-px self-stretch bg-gray-200 dark:bg-gray-600 mx-0.5" aria-hidden="true" />
        <span class="flex flex-col items-center gap-0.5" :class="month ? 'w-[7px] md:w-3' : 'w-[30px]'" :title="bar.title">
          <span class="flex flex-col justify-end items-stretch w-full" :style="{ height: (BAR_HEIGHT_PX + CHARGE_BASE_PX) + 'px' }">
            <span class="w-full rounded-t-sm"
                  :class="bar.height ? 'bg-emerald-300 dark:bg-emerald-600' : (bar.charged ? '' : 'bg-gray-200 dark:bg-gray-600')"
                  :style="{ height: bar.height ? bar.height + 'px' : (bar.charged ? '0' : '2px') }" />
            <span v-if="bar.charged" class="w-full bg-indigo-500 dark:bg-indigo-400"
                  :class="bar.height ? '' : 'rounded-t-sm'"
                  :style="{ height: CHARGE_BASE_PX + 'px' }" />
          </span>
          <span class="text-[9px] leading-none text-gray-400 dark:text-gray-500 tabular-nums h-2.5">{{ bar.label }}</span>
          <span v-if="!month" class="text-[9px] leading-none tabular-nums h-2.5"
                :class="bar.km > 0 ? 'text-gray-600 dark:text-gray-300 font-medium' : 'text-gray-300 dark:text-gray-600'">
            {{ bar.km > 0 ? formatDistance(bar.km, { showUnit: false }) : '·' }}
          </span>
        </span>
      </template>
    </div>
    <p class="mt-1 text-[10px] text-gray-400 dark:text-gray-500 text-center">
      {{ month
        ? t('logs.period.bars_legend', { max: formatDistance(maxKm) })
        : t('logs.period.bars_legend_km') }}
    </p>
  </div>
</template>
