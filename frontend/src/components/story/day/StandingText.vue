<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../../composables/useLocaleFormat'

/**
 * Standzeit zwischen zwei Einträgen. Story: "2 h 29 min gestanden". Logbuch, mit Standverlust und seinem Wert
 * zum Preis der Ladung davor: "2 h 29 min · 0,24 kWh Standverlust ≈ 0,07 €", kurz genug für eine Zeile auf dem
 * Handy; solange er nur als Vorgeschmack offen ist, dahinter der Link zum Freischalten.
 * Inhalt einer Zeile, die Zeile stellt der Rahmen.
 */
const props = defineProps<{ minutes: number; kwh?: number | null; eur?: number | null; unlockTo?: string | null }>()
const { t } = useI18n()
const { formatDecimal, formatCurrency } = useLocaleFormat()

function dur(min: number) {
  const h = Math.floor(min / 60), m = min % 60
  return h && m ? t('storyday.dur_hm', { h, m }) : h ? t('storyday.dur_h', { h }) : t('storyday.dur_m', { m })
}
</script>

<template>
  <template v-if="kwh != null">
    <span class="whitespace-nowrap">{{ dur(props.minutes) }}</span>·
    <span class="inline-flex items-center gap-1.5 whitespace-nowrap" data-testid="day-drain">
      <i class="inline-block h-2 w-2 shrink-0 rounded-[2px] bg-amber-400" aria-hidden="true" /><b class="font-medium text-gray-600 dark:text-gray-300">{{ formatDecimal(kwh, 2) }} kWh</b> {{ t('dashboard.phantom_drain_word') }}<template v-if="eur != null"> ≈ {{ formatCurrency(eur) }}</template>
    </span>
    <router-link v-if="unlockTo" :to="unlockTo" class="font-semibold text-gray-900 underline underline-offset-2 dark:text-gray-100">{{ t('dashboard.phantom_teaser_unlock') }}</router-link>
  </template>
  <span v-else class="whitespace-nowrap">{{ t('storyday.standing', { d: dur(props.minutes) }) }}</span>
</template>
