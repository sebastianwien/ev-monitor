<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { BoltIcon, ChevronDownIcon, ChevronUpIcon, HomeIcon } from '@heroicons/vue/24/outline'

/**
 * Erklaerkasten fuer den Log-Feed. Loest die wiederkehrende Nutzerfrage, warum der Feed
 * einen niedrigeren Verbrauch zeigt als das Dashboard: Feed = reiner Fahrverbrauch,
 * Startseite = Gesamtverbrauch zwischen zwei Ladungen inkl. Verbrauch im Stand.
 *
 * Zwei Modi, damit der rechtsbuendige Trigger in der Aufloesungs-Zeile stehen kann,
 * das Panel aber volle Breite darunter bekommt:
 * - `trigger`: die farbige Slim-Legende (Chip-Farben) plus Aufklapp-Button.
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
  <!-- Trigger: rechtsbuendige Slim-Legende + Aufklapp-Button. -->
  <button v-if="mode === 'trigger'" type="button" @click="$emit('toggle')" :aria-expanded="open"
          class="inline-flex items-center gap-x-3 gap-y-1 flex-wrap justify-end text-xs
                 text-gray-600 dark:text-gray-300 group">
    <span class="inline-flex items-center gap-1.5">
      <span class="w-2 h-2 rounded-full bg-emerald-500 dark:bg-emerald-400 shrink-0" aria-hidden="true" />
      {{ t('logs.legend.slim_driven') }}
    </span>
    <span v-if="hasCharges" class="inline-flex items-center gap-1.5">
      <span class="w-2 h-2 rounded-full bg-indigo-500 dark:bg-indigo-400 shrink-0" aria-hidden="true" />
      {{ t('logs.legend.slim_charged') }}
    </span>
    <span v-if="showPhantom" class="inline-flex items-center gap-1.5">
      <span class="w-2 h-2 rounded-full bg-amber-500 shrink-0" aria-hidden="true" />
      {{ t('logs.legend.slim_phantom') }}
    </span>
    <span class="inline-flex items-center gap-0.5 text-gray-400 dark:text-gray-500
                 group-hover:text-gray-600 dark:group-hover:text-gray-300 transition-colors">
      {{ t('logs.legend.toggle') }}
      <ChevronUpIcon v-if="open" class="w-3.5 h-3.5" />
      <ChevronDownIcon v-else class="w-3.5 h-3.5" />
    </span>
  </button>

  <!-- Panel: zwei Bloecke, Begriff/Chip + Beschreibung im Grid ausgerichtet. -->
  <div v-else
       class="rounded-lg border border-gray-200 dark:border-gray-700/60 bg-gray-50 dark:bg-gray-800/40
              divide-y divide-gray-200 dark:divide-gray-700/60 text-xs overflow-hidden">
    <!-- Block 1: was in diesem Feed steht. -->
    <div class="p-3 space-y-2">
      <p class="text-[10px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500">
        {{ t('logs.legend.section_feed') }}
      </p>
      <div class="grid grid-cols-[max-content_1fr] gap-x-2.5 gap-y-2 items-baseline leading-snug">
        <template v-if="hasTrips">
          <span class="justify-self-start inline-flex items-baseline gap-1 px-2 py-0.5 border rounded-full font-medium whitespace-nowrap
                       bg-emerald-50 dark:bg-emerald-900/30 border-emerald-300/60 dark:border-emerald-700/50 text-emerald-700 dark:text-emerald-300">
            12&nbsp;kWh/100km<span class="text-[10px] font-normal opacity-75">&minus;36%</span>
          </span>
          <span class="text-gray-500 dark:text-gray-400">{{ t('logs.legend.desc_driving') }}</span>
        </template>
        <template v-if="showPhantom">
          <span class="justify-self-start inline-flex items-center gap-0.5 px-2 py-0.5 border rounded-full font-medium whitespace-nowrap
                       bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-amber-500">
            <BoltIcon class="w-3 h-3" />2&nbsp;kWh
          </span>
          <span class="text-gray-500 dark:text-gray-400">{{ t('logs.legend.desc_phantom') }}</span>
        </template>
        <template v-if="hasCharges">
          <span class="justify-self-start inline-flex items-center gap-0.5 px-2 py-0.5 border rounded-full font-medium whitespace-nowrap
                       bg-indigo-100/80 dark:bg-indigo-900/40 border-indigo-300/70 dark:border-indigo-700/50 text-indigo-700 dark:text-indigo-300">
            <BoltIcon class="w-3 h-3" />+48&nbsp;kWh
          </span>
          <span class="text-gray-500 dark:text-gray-400">{{ t('logs.legend.desc_charged') }}</span>
        </template>
        <template v-if="hasTrips">
          <span class="justify-self-start flex items-end gap-[3px] h-4 self-center" aria-hidden="true">
            <span class="w-1.5 rounded-t-sm bg-emerald-300 dark:bg-emerald-600" style="height: 7px" />
            <span class="w-1.5 rounded-t-sm bg-emerald-300 dark:bg-emerald-600" style="height: 13px" />
            <span class="flex flex-col items-stretch justify-end w-1.5 h-full">
              <span class="rounded-t-sm bg-emerald-300 dark:bg-emerald-600" style="height: 9px" />
              <span class="bg-indigo-500 dark:bg-indigo-400" style="height: 3px" />
            </span>
          </span>
          <span class="text-gray-500 dark:text-gray-400">{{ t('logs.legend.bars') }}</span>
        </template>
      </div>
      <p class="pt-2 mt-1 border-t border-gray-200 dark:border-gray-700/60 text-gray-500 dark:text-gray-400 leading-snug">
        {{ t('logs.legend.percent_note') }}
      </p>
    </div>

    <!-- Block 2: warum die Startseite andere Werte zeigt - neutraler, weil anderer Screen. -->
    <div class="p-3 space-y-2 bg-white/50 dark:bg-gray-900/20">
      <div class="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500">
        <HomeIcon class="w-3 h-3" aria-hidden="true" />
        {{ t('logs.legend.section_home') }}
      </div>
      <p class="text-gray-500 dark:text-gray-400 leading-snug">{{ t('logs.legend.section_home_hint') }}</p>
      <div class="grid grid-cols-[max-content_1fr] gap-x-2.5 gap-y-2 items-baseline leading-snug">
        <template v-if="hasTrips">
          <span class="justify-self-start font-semibold text-gray-700 dark:text-gray-200 whitespace-nowrap">{{ t('logs.legend.term_avg') }}</span>
          <span class="text-gray-500 dark:text-gray-400">{{ t('logs.legend.desc_avg') }}</span>
        </template>
        <span class="justify-self-start font-semibold text-gray-700 dark:text-gray-200 whitespace-nowrap">{{ t('logs.legend.term_total') }}</span>
        <span class="text-gray-500 dark:text-gray-400">{{ t('logs.legend.desc_total') }}</span>
      </div>
    </div>
  </div>
</template>
