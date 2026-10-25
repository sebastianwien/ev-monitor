<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon, HomeIcon, InformationCircleIcon } from '@heroicons/vue/24/outline'

/**
 * Erklaerkasten fuer den Log-Feed. Loest die wiederkehrende Nutzerfrage, warum der Feed
 * einen niedrigeren Verbrauch zeigt als das Dashboard: Feed = reiner Fahrverbrauch,
 * Startseite = Gesamtverbrauch zwischen zwei Ladungen inkl. Verbrauch im Stand.
 *
 * Zwei Modi, damit der rechtsbuendige Trigger in der Aufloesungs-Zeile stehen kann,
 * das Panel aber volle Breite darunter bekommt:
 * - `trigger`: der Aufklapp-Link; die Farben erklaeren die Koepfe selbst (Punkt + Wort).
 * - `panel`: das aufgeklappte Detail mit zwei Bloecken - "In dieser Uebersicht" und
 *   "Auf der Startseite" - als Begriff/Chip + Beschreibung, im Grid ausgerichtet.
 *
 * Die Zeilen sind an die tatsaechlich sichtbaren Chips gekoppelt: wer nur laedt, sieht
 * keinen Fahrverbrauch; wer den Standverlust nicht freigeschaltet hat, keine
 * Standverlust-Zeile. Der offene Zustand lebt beim Aufrufer (LogsView).
 */
defineProps<{
  mode: 'trigger' | 'panel'
  /** Feed enthaelt Fahrten - blendet Fahrverbrauch + Startseiten-Ø-Verbrauch ein. */
  hasTrips: boolean
  /** Feed enthaelt Ladungen - blendet geladen + Startseiten-Gesamtenergie ein. */
  hasCharges: boolean
  /** Standverlust-Chip ist sichtbar (Premium-gegated) - blendet die Standverlust-Zeile ein. */
  showPhantom: boolean
  /** Nur fuer den Trigger: offener Zustand steuert das Chevron. */
  open?: boolean
}>()

defineEmits<{ (e: 'toggle'): void }>()

const { t } = useI18n()
</script>

<template>
  <!-- Trigger: Textlink mit Chevron, 40 px hoch wie die Chips daneben. Auf dem Handy nur das
       Info-Symbol, sonst bekaeme der Link eine eigene Zeile. -->
  <button v-if="mode === 'trigger'" type="button" @click="$emit('toggle')" :aria-expanded="open"
          :aria-label="t('logs.legend.toggle')" :title="t('logs.legend.toggle')"
          class="inline-flex min-h-10 min-w-10 items-center justify-center gap-1 rounded-full px-1 text-[13px] font-semibold text-gray-600 transition-colors hover:text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-green-600 sm:underline sm:decoration-gray-300 sm:underline-offset-4 sm:hover:decoration-gray-900 dark:text-gray-300 dark:decoration-gray-600 dark:hover:text-gray-100"
          :class="open ? 'text-gray-900 dark:text-gray-100' : ''">
    <InformationCircleIcon class="h-5 w-5 sm:hidden" aria-hidden="true" />
    <span class="hidden sm:inline">{{ t('logs.legend.toggle') }}</span>
    <ChevronDownIcon class="hidden h-3.5 w-3.5 transition-transform duration-300 ease-out sm:block" :class="open ? 'rotate-180' : ''" aria-hidden="true" />
  </button>

  <!-- Panel: zwei Bloecke, Begriff + Beschreibung im Grid ausgerichtet. -->
  <div v-else class="rounded-2xl bg-gray-50 text-[13px] leading-snug dark:bg-white/5">
    <!-- Block 1: was in diesem Feed steht - dieselben Zeichen wie in den Koepfen. -->
    <div class="space-y-2.5 p-4">
      <p class="text-[11.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400">
        {{ t('logs.legend.section_feed') }}
      </p>
      <div class="grid grid-cols-[max-content_1fr] items-baseline gap-x-3 gap-y-2.5">
        <template v-if="hasTrips">
          <span class="inline-flex items-baseline gap-1 whitespace-nowrap tabular-nums">
            <b class="font-bold text-gray-900 dark:text-gray-50">12</b><span class="text-[12px] font-semibold text-gray-500 dark:text-gray-400">kWh/100km</span>
            <span class="text-[12px] font-semibold text-green-700 dark:text-green-400">&minus;36 %</span>
          </span>
          <span class="text-gray-600 dark:text-gray-300">{{ t('logs.legend.desc_driving') }}</span>
        </template>
        <template v-if="showPhantom">
          <span class="inline-flex items-center gap-1.5 whitespace-nowrap text-gray-700 dark:text-gray-200">
            <i class="h-2 w-2 rounded-[2px] bg-amber-400" aria-hidden="true" />{{ t('logs.legend.slim_phantom') }}
          </span>
          <span class="text-gray-600 dark:text-gray-300">{{ t('logs.legend.desc_phantom') }}</span>
        </template>
        <template v-if="hasCharges">
          <span class="inline-flex items-center gap-1.5 whitespace-nowrap text-gray-700 dark:text-gray-200">
            <i class="h-2 w-2 rounded-[2px] bg-green-700 dark:bg-green-400" aria-hidden="true" />{{ t('logs.legend.slim_charged') }}
          </span>
          <span class="text-gray-600 dark:text-gray-300">{{ t('logs.legend.desc_charged') }}</span>
        </template>
        <template v-if="hasTrips">
          <span class="flex h-5 items-end gap-[3px] self-center" aria-hidden="true">
            <span class="flex w-2 flex-col gap-[2px]"><i class="block h-[7px] rounded-[2px] bg-sky-500 dark:bg-sky-400" /><i class="block h-1 rounded-[2px] bg-gray-200 dark:bg-gray-700" /></span>
            <span class="flex w-2 flex-col gap-[2px]"><i class="block h-[13px] rounded-[2px] bg-sky-500 dark:bg-sky-400" /><i class="block h-1 rounded-[2px] bg-gray-200 dark:bg-gray-700" /></span>
            <span class="flex w-2 flex-col gap-[2px]"><i class="block h-[9px] rounded-[2px] bg-sky-500 dark:bg-sky-400" /><i class="block h-1 rounded-[2px] bg-green-700 dark:bg-green-400" /></span>
          </span>
          <span class="text-gray-600 dark:text-gray-300">{{ t('logs.legend.bars') }}</span>
        </template>
      </div>
      <p class="border-t border-gray-200 pt-2.5 text-gray-600 dark:border-gray-700 dark:text-gray-300">
        {{ t('logs.legend.percent_note') }}
      </p>
    </div>

    <!-- Block 2: warum die Startseite andere Werte zeigt. -->
    <div class="space-y-2.5 border-t border-gray-200 p-4 dark:border-gray-700">
      <div class="flex items-center gap-1.5 text-[11.5px] font-semibold uppercase tracking-[0.06em] text-gray-500 dark:text-gray-400">
        <HomeIcon class="h-3.5 w-3.5" aria-hidden="true" />
        {{ t('logs.legend.section_home') }}
      </div>
      <p class="text-gray-600 dark:text-gray-300">{{ t('logs.legend.section_home_hint') }}</p>
      <div class="grid grid-cols-[max-content_1fr] items-baseline gap-x-3 gap-y-2.5">
        <template v-if="hasTrips">
          <span class="whitespace-nowrap font-semibold text-gray-900 dark:text-gray-100">{{ t('logs.legend.term_avg') }}</span>
          <span class="text-gray-600 dark:text-gray-300">{{ t('logs.legend.desc_avg') }}</span>
        </template>
        <span class="whitespace-nowrap font-semibold text-gray-900 dark:text-gray-100">{{ t('logs.legend.term_total') }}</span>
        <span class="text-gray-600 dark:text-gray-300">{{ t('logs.legend.desc_total') }}</span>
      </div>
    </div>
  </div>
</template>
