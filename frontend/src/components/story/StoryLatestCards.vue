<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, MapIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { relativeTimeParts } from '../../utils/recentActivity'
import type { StoryLatest } from '../../composables/storyInsights'
import { keepUnits } from '../../utils/modelStory'

/**
 * "Zuletzt": letzte Ladung und letzte Fahrt als je eine tippbare Zeile mit Chevron, immer da, wenn
 * es sie gibt. Am Kalendertag der Ladung ist die Ladung hervorgehoben: "Heute geladen" mit Kosten,
 * Akku und Reichweite, kWh und Zeit; fehlt der Preis, steht ein Knopf "Preis eintragen" in der Karte.
 * Tippen öffnet den Eintrag zum Ansehen und Bearbeiten. Die Editoren besitzt die View.
 */
const props = defineProps<{
  latest: StoryLatest
  now?: number
}>()
const emit = defineEmits<{ 'open-charge': [id: string, missingPrice: boolean]; 'open-trip': [id: string] }>()
const { t, locale } = useI18n()
const { formatCurrency, formatDistance, formatDecimal } = useLocaleFormat()

const nowMs = () => props.now ?? Date.now()
function when(iso: string): string {
  const parts = relativeTimeParts(Date.parse(iso), nowMs())
  if (!parts) return ''
  if (parts.unit === 'second' || parts.unit === 'minute') return t('story.latest_just_now')
  return new Intl.RelativeTimeFormat(locale.value, { numeric: 'auto' }).format(parts.value, parts.unit)
}
/** Derselbe Kalendertag in Ortszeit. */
function isToday(iso: string): boolean {
  const a = new Date(iso)
  const b = new Date(nowMs())
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

const charge = computed(() => {
  const c = props.latest.charge
  if (!c) return null
  const missingPrice = c.costEur == null
  const kwh = c.kwh != null ? `${formatDecimal(c.kwh, 1)} kWh` : null
  const today = isToday(c.at)
  const title = today
    ? [t('story.latest_today'), c.costEur != null ? formatCurrency(c.costEur) : null].filter(Boolean).join(' · ')
    : c.kwh != null ? keepUnits(t('story.latest_charged', { kwh })) : t('story.latest_charged_plain')
  const battery = today && c.socTo != null
    ? c.rangeKm != null
      ? t('story.latest_soc_range', { soc: Math.round(c.socTo), km: formatDistance(c.rangeKm, { round: true }) })
      : t('story.latest_soc', { soc: Math.round(c.socTo) })
    : null
  const amount = today && kwh ? (c.socFrom != null ? t('story.latest_kwh_from', { kwh, soc: Math.round(c.socFrom) }) : kwh) : null
  const sub = today
    ? [amount, when(c.at), [c.cpoName, c.chargingType].filter(Boolean).join(', ') || null].filter(Boolean).join(' · ')
    : [when(c.at), [c.cpoName, c.chargingType].filter(Boolean).join(', ') || null, c.costEur != null ? formatCurrency(c.costEur) : null].filter(Boolean).join(' · ')
  return { id: c.id, title, battery, sub, missingPrice, today }
})
const trip = computed(() => {
  const tr = props.latest.trip
  if (!tr) return null
  return {
    id: tr.id,
    title: tr.km != null ? keepUnits(t('story.latest_drove', { km: formatDistance(tr.km, { round: true }) })) : t('story.latest_drove_plain'),
    sub: [when(tr.at), tr.routeType ? t(`model.route_type_${tr.routeType.toLowerCase()}`) : null].filter(Boolean).join(' · '),
  }
})

/** Amber-Chip "Preis nachtragen" nur an einer älteren Ladung; heute übernimmt der Knopf in der Karte. */
const chip = computed(() => !!charge.value?.missingPrice && !charge.value.today)

// Hover wie bei den Kacheln darunter, nur ab md: auf Touch bliebe er nach dem Tippen stehen.
const row = 'flex w-full text-left items-center gap-2.5 rounded-xl px-3 transition-[transform,box-shadow,background-color] md:hover:bg-gray-50 md:dark:hover:bg-gray-700/70 active:translate-x-0.5 active:translate-y-0.5 active:shadow-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600'
const plain = `${row} h-12 bg-white edge-card dark:bg-gray-700/50`
</script>

<template>
  <div class="grid gap-2 md:grid-cols-2" data-testid="story-latest">
    <!-- Heute geladen: grün hinterlegt, "Neu", drei Zeilen; die Zeile zeigt die Ladung, ohne Preis führt der Knopf darunter zum Preis -->
    <div v-if="charge && charge.today" class="grid rounded-xl bg-green-50 edge-card dark:bg-green-900/20" :class="trip ? '' : 'md:col-span-2'" data-testid="story-latest-today">
      <button type="button" :class="row" class="min-h-16 py-2.5 rounded-b-none" data-testid="story-latest-charge" @click="emit('open-charge', charge.id, false)">
        <BoltIcon class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
        <span class="min-w-0 flex-1 leading-tight">
          <span class="flex items-center gap-2">
            <span class="truncate text-[15px] font-bold text-gray-900 tabular-nums dark:text-gray-50">{{ charge.title }}</span>
            <span class="shrink-0 rounded-full bg-green-700 px-2 py-0.5 text-[11px] font-bold text-white dark:bg-green-400 dark:text-gray-900">{{ t('story.latest_new') }}</span>
          </span>
          <span v-if="charge.battery" class="mt-0.5 block text-[14px] text-gray-800 tabular-nums dark:text-gray-200">{{ charge.battery }}</span>
          <span v-if="charge.sub" class="block truncate text-[12px] text-gray-500 tabular-nums dark:text-gray-400">{{ charge.sub }}</span>
        </span>
        <ChevronRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
      </button>
      <div v-if="charge.missingPrice" class="px-3 pb-3">
        <button type="button" class="btn-3d cta-shadow-sm min-h-11 w-full rounded-lg border-2 border-edge bg-green-600 px-4 text-[15px] font-bold text-white" data-testid="story-latest-add-price" @click="emit('open-charge', charge.id, true)">{{ t('story.latest_enter_price') }}</button>
      </div>
    </div>
    <button v-else-if="charge" type="button" :class="plain" data-testid="story-latest-charge" @click="emit('open-charge', charge.id, charge.missingPrice)">
      <BoltIcon class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
      <span class="min-w-0 flex-1 leading-tight">
        <span class="block truncate text-[14px] font-semibold text-gray-900 tabular-nums dark:text-gray-50">{{ charge.title }}</span>
        <span v-if="charge.sub" class="block truncate text-[12px] text-gray-500 tabular-nums dark:text-gray-400" :class="chip ? 'md:hidden' : ''">{{ charge.sub }}</span>
        <!-- Ab md (zwei Zeilen nebeneinander) nimmt der Chip den Platz der Unterzeile, damit der Titel lesbar bleibt. -->
        <span v-if="chip" class="mt-0.5 hidden rounded-sm bg-amber-100 px-1.5 text-[12px] font-semibold text-amber-800 md:inline-block dark:bg-amber-900/40 dark:text-amber-300">{{ t('story.latest_add_price') }}</span>
      </span>
      <span v-if="chip" class="shrink-0 rounded-sm bg-amber-100 px-2 py-1 text-[12px] font-semibold text-amber-800 md:hidden dark:bg-amber-900/40 dark:text-amber-300">{{ t('story.latest_add_price') }}</span>
      <ChevronRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
    </button>
    <button v-if="trip" type="button" :class="plain" data-testid="story-latest-trip" @click="emit('open-trip', trip.id)">
      <MapIcon class="h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
      <span class="min-w-0 flex-1 leading-tight">
        <span class="block truncate text-[14px] font-semibold text-gray-900 tabular-nums dark:text-gray-50">{{ trip.title }}</span>
        <span v-if="trip.sub" class="block truncate text-[12px] text-gray-500 tabular-nums dark:text-gray-400">{{ trip.sub }}</span>
      </span>
      <ChevronRightIcon class="h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
    </button>
  </div>
</template>
