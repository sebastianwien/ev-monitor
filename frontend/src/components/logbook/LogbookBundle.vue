<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, XMarkIcon, PencilSquareIcon, ArrowsRightLeftIcon } from '@heroicons/vue/24/outline'
import DayTimeline from '../story/day/DayTimeline.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { bundleTotals, chargePlace, dayKeyOf, eventsOfDay, type DayEvent, type DayLog } from '../../composables/storyDay'

/**
 * Ein Bündel aus Teilladungen: Kopf mit Ort, Zahl der Teile und Summe, darunter die Teile neueste zuerst wie im
 * Ablauf. Ein Tipp auf ein Teil öffnet es zum Bearbeiten. Fehlt Teilen der Preis, führt der Chip zum Nachtragen
 * bei der ältesten; bei mehreren Autos lassen sich alle Teile umbuchen.
 */
const props = withDefaults(defineProps<{
  parts: DayLog[]
  dismiss?: 'close' | 'back'
  canAmend?: boolean
  canReassign?: boolean
}>(), { dismiss: 'close', canAmend: false, canReassign: false })
const emit = defineEmits<{ cancel: []; select: [log: DayLog]; amend: [log: DayLog]; reassign: [] }>()
const { t } = useI18n()
const { formatCurrency, formatDecimal, formatCostPerKwh } = useLocaleFormat()

const AMBER_CHIP = 'inline-flex min-h-11 items-center gap-1 whitespace-nowrap rounded-full bg-amber-100 px-3 text-[13px] font-semibold text-amber-800 transition-colors hover:bg-amber-200 dark:bg-amber-400/15 dark:text-amber-300 dark:hover:bg-amber-400/25'

const totals = computed(() => bundleTotals(props.parts))
const place = computed(() => {
  const p = chargePlace(props.parts[0])
  return p.kind === 'operator' ? p.name : t(`storyday.place_${p.kind}`)
})
const line = computed(() => [
  t('storyday.parts', { n: props.parts.length }, props.parts.length),
  totals.value.kwh != null ? `${formatDecimal(totals.value.kwh, 1)} kWh` : null,
  totals.value.costEur != null ? formatCurrency(totals.value.costEur) : null,
  totals.value.pricePerKwh != null ? formatCostPerKwh(totals.value.pricePerKwh) : null,
].filter(Boolean).join(' · '))
const events = computed(() => eventsOfDay(dayKeyOf(props.parts[0].loggedAt), [], props.parts))
const pick = (e: DayEvent) => { if (e.kind === 'charge') emit('select', e.log) }
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col bg-white dark:bg-gray-900" data-testid="logbook-bundle">
    <div class="flex shrink-0 items-center gap-1 border-b-2 border-gray-100 px-4 py-2 dark:border-gray-800">
      <button v-if="dismiss === 'back'" type="button" :aria-label="t('common.back')" data-testid="bundle-back" @click="emit('cancel')"
        class="-ml-2 grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
        <ChevronLeftIcon class="h-5 w-5" />
      </button>
      <h2 class="min-w-0 flex-1 truncate text-[17px] font-bold text-gray-900 dark:text-gray-50">{{ t('storyday.parts_title') }}</h2>
      <button v-if="dismiss === 'close'" type="button" :aria-label="t('common.cancel')" @click="emit('cancel')"
        class="-mr-2 grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10">
        <XMarkIcon class="h-5 w-5" />
      </button>
    </div>

    <div class="flex-1 overflow-y-auto p-4">
      <p class="text-[16px] font-bold text-gray-900 dark:text-gray-50">{{ place }}</p>
      <p class="mt-0.5 text-[14px] tabular-nums text-gray-600 dark:text-gray-300" data-testid="bundle-sum">{{ line }}</p>
      <div v-if="(canAmend && totals.priceless.length) || canReassign" class="mt-3 flex flex-wrap items-center gap-2">
        <button v-if="canAmend && totals.priceless.length" type="button" :class="AMBER_CHIP" data-testid="bundle-amend"
          @click="emit('amend', totals.priceless[0])">
          <PencilSquareIcon class="h-4 w-4" aria-hidden="true" />{{ t('storyday.missing_price_n', { n: totals.priceless.length }) }}
        </button>
        <button v-if="canReassign" type="button" data-testid="bundle-reassign" @click="emit('reassign')"
          class="inline-flex min-h-11 items-center gap-1.5 rounded-full px-1 text-[14px] font-semibold text-gray-800 hover:text-gray-900 dark:text-gray-200">
          <ArrowsRightLeftIcon class="h-4 w-4" aria-hidden="true" />{{ t('dashboard.action_reassign') }}
        </button>
      </div>
      <div class="mt-3">
        <DayTimeline :events="events" :selected-id="null" :merging="false" :standing="{}" newest-first no-credit @select="pick" />
      </div>
    </div>
  </div>
</template>
