<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarDaysIcon } from '@heroicons/vue/24/outline'
import type { StoryMonth } from '../../composables/useDashboardStory'
import { useActiveIndicator } from '../../composables/useActiveIndicator'

/**
 * Die Kapsel des Monats-Schalters, einmal im Kopf der Box und einmal deckungsgleich als Kopfzeile des Monatsrasters
 * (StoryMonthSwitch). Der Kalender liegt links außerhalb des Rands; aufgeklappt (Raster offen oder älterer Monat
 * gewählt) gleitet der Rand nach links über ihn, und die Fläche des gewählten Monats kann dorthin gleiten.
 */
const props = withDefaults(defineProps<{
  months: StoryMonth[]
  offset: number
  labelOf: (m: StoryMonth) => string
  /** Kalender zeigen (es gibt ältere Ladungen). */
  calendar: boolean
  expanded: boolean
  /** Gewählter älterer Monat: steht im Kalender-Segment, kurz beschriftet. */
  olderLabel: string | null
  olderAria: string
  calendarOpen: boolean
  /** Nur die Kapsel im Kopf trägt testids, die Kopie im Raster nicht. */
  testids?: boolean
  /** Eigener Rand; die Kopie im Raster bekommt ihn vom Panel, das aus ihm nach unten wächst. */
  framed?: boolean
}>(), { testids: true, framed: true })
const emit = defineEmits<{ select: [offset: number]; calendar: [] }>()
const { t } = useI18n()
const group = ref<HTMLElement | null>(null)
const calendarButton = ref<HTMLButtonElement | null>(null)
const pill = useActiveIndicator(group, '[data-active="true"]', () => [props.offset, props.olderLabel])
defineExpose({ el: group, calendarButton })

// Ohne Innenabstand: Monate px-3.5, der Kalender als Symbol genau 32 px (der Rand rechnet mit 32 px plus 10 px Abstand)
const segment = (active: boolean) => `relative z-10 min-h-8 rounded-full text-[13.5px] font-semibold transition-colors duration-300 before:absolute before:inset-x-0 before:-inset-y-1.5 before:content-[''] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 ${
  active ? 'text-white dark:text-gray-900' : 'text-gray-600 hover:text-gray-900 dark:text-gray-300 dark:hover:text-white'
}`
</script>

<template>
  <div ref="group" role="group" :aria-label="t('story.month_aria')" class="relative inline-flex shrink-0 items-center p-1"
    :data-testid="testids ? 'story-month-frame' : undefined" :data-expanded="expanded ? 'true' : 'false'">
    <!-- Rand: umschließt die Monate, aufgeklappt auch den Kalender (32 px Kalender plus 10 px Abstand) -->
    <span v-if="framed" aria-hidden="true" class="absolute inset-y-0 right-0 rounded-full border-2 border-edge bg-white transition-[left] duration-300 motion-reduce:transition-none dark:bg-gray-900"
      :class="calendar && !expanded ? 'left-[42px] ease-in' : 'left-0 ease-out'" />
    <span aria-hidden="true" class="pointer-events-none absolute inset-y-1 left-0 z-[1] rounded-full bg-gray-900 dark:bg-gray-100"
      :class="[pill.glide.value ? 'transition-[transform,width] duration-300 ease-out motion-reduce:transition-none' : '', pill.visible.value ? '' : 'opacity-0']"
      :style="pill.style.value" />
    <button v-if="calendar" ref="calendarButton" type="button"
      :data-testid="testids ? 'story-month-older' : undefined"
      aria-haspopup="dialog" :aria-expanded="calendarOpen" :aria-label="olderAria"
      :data-active="olderLabel ? 'true' : 'false'"
      :class="[segment(!!olderLabel), 'mr-2.5', olderLabel ? 'px-3.5' : 'grid w-8 place-items-center', expanded ? '' : 'hover:bg-black/5 dark:hover:bg-white/10']"
      @click="emit('calendar')">
      <span v-if="olderLabel" class="whitespace-nowrap">{{ olderLabel }}</span>
      <CalendarDaysIcon v-else class="h-4 w-4" aria-hidden="true" />
    </button>
    <button
      v-for="m in months" :key="m.offset"
      type="button"
      :aria-pressed="m.offset === offset"
      :data-active="m.offset === offset ? 'true' : 'false'"
      :class="[segment(m.offset === offset), 'px-3.5']"
      :data-testid="testids ? `story-month-${m.offset}` : undefined"
      @click="emit('select', m.offset)">
      {{ labelOf(m) }}
    </button>
  </div>
</template>
