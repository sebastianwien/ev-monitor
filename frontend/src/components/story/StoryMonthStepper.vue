<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { monthAt, type StoryMonth } from '../../composables/useDashboardStory'

/**
 * Monatswechsel kompakt: "‹ Okt ›". Zurück bis zur ersten Ladung, vor bis zum laufenden Monat. Ein
 * Monat eines anderen Jahres trägt die Jahreszahl ("Mär 25"). Das Wischen auf dem Hero steuert dieselbe
 * Auswahl. Kein Kalender, kein Segment-Schalter: die Seite ist immer der Monat.
 * Neben dem Monatssatz des Heros nur die Pfeile (labelled false), der Satz nennt den Monat schon.
 */
const props = withDefaults(defineProps<{
  offset: number
  /** Frühester wählbarer Monat (erste Ladung). */
  minOffset: number
  labelOf: (m: StoryMonth) => string
  /** Monat sichtbar zwischen den Pfeilen; sonst nur für Screenreader. */
  labelled?: boolean
}>(), { labelled: true })
const emit = defineEmits<{ select: [offset: number] }>()
const { t, locale } = useI18n()

const month = computed(() => monthAt(props.offset))
const label = computed(() => month.value.year === new Date().getFullYear()
  ? props.labelOf(month.value)
  : `${props.labelOf(month.value)} ${String(month.value.year).slice(2)}`)
const longName = computed(() => new Date(month.value.year, month.value.month - 1, 1).toLocaleDateString(locale.value, { month: 'long', year: 'numeric' }))
const canPrev = computed(() => props.offset > props.minOffset)
const canNext = computed(() => props.offset < 0)

const button = 'grid h-11 w-11 place-items-center rounded-full text-gray-900 transition-colors hover:bg-black/5 disabled:cursor-default disabled:text-gray-300 disabled:hover:bg-transparent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:text-gray-100 dark:hover:bg-white/10 dark:disabled:text-gray-600'
</script>

<template>
  <div class="-mr-2 flex items-center" data-testid="story-month-stepper" role="group" :aria-label="t('story.month_aria')">
    <button type="button" :class="button" :disabled="!canPrev" :aria-label="t('story.month_prev')" data-testid="story-month-prev" @click="emit('select', offset - 1)">
      <ChevronLeftIcon class="h-5 w-5" aria-hidden="true" />
    </button>
    <span :class="labelled ? 'min-w-10 text-center text-[15px] font-bold tabular-nums text-gray-900 dark:text-gray-50' : 'sr-only'" data-testid="story-month-label" :aria-label="longName" aria-live="polite">{{ labelled ? label : longName }}</span>
    <button type="button" :class="button" :disabled="!canNext" :aria-label="t('story.month_next')" data-testid="story-month-next" @click="emit('select', offset + 1)">
      <ChevronRightIcon class="h-5 w-5" aria-hidden="true" />
    </button>
  </div>
</template>
