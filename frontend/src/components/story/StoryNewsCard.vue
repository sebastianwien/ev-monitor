<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, ShareIcon, ChevronRightIcon, SunIcon } from '@heroicons/vue/24/outline'
import CloudRainIcon from '../icons/CloudRainIcon.vue'
import type { Moment } from '../../composables/storyMoments'
import { useMomentView, type MomentAction } from '../../composables/useMomentView'
import { keepUnits } from '../../utils/modelStory'

/**
 * Eine Neuigkeit als Karte: kleine grüne Zeile, was passiert ist, ein Satz in km, Euro und Tagen,
 * eine graue Unterzeile. Handlungen nach Art: Tun (Preis nachtragen, Erfassen, AutoSync,
 * Rückblick teilen) als Knopf in ganzer Breite, Ansehen (Winterwerte, Fahrten, Preise, Akku,
 * Rangliste) als grüner Link mit Pfeil, Teilen beim Meilenstein als Symbol oben rechts. × heißt erledigt.
 */
const props = defineProps<{ moment: Moment; carId: string | null; carName: string; now?: number }>()
const emit = defineEmits<{ act: [id: string]; dismiss: [] }>()
const { t } = useI18n()
const view = useMomentView(props)

/** Ansehen führt zu einer Ansicht; alles andere ist Tun. */
const VIEW_ACTIONS = new Set(['prices', 'trips', 'winter', 'battery', 'rank'])
const shareIcon = computed(() => props.moment.kind === 'milestone' && view.value.action?.id === 'share')
const primary = computed<MomentAction | null>(() => shareIcon.value ? null : view.value.action ?? null)
const primaryIsView = computed(() => !!primary.value && VIEW_ACTIONS.has(primary.value.id))

const button = 'btn-3d cta-shadow-sm flex min-h-12 w-full items-center justify-center rounded-lg border-2 border-edge bg-green-600 px-4 text-[15px] font-bold text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600'
const link = 'inline-flex min-h-11 items-center gap-0.5 text-[15px] font-semibold text-green-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:text-green-400'
const quiet = 'inline-flex min-h-11 items-center text-sm font-semibold text-gray-700 underline underline-offset-2 dark:text-gray-200'
</script>

<template>
  <article class="grid gap-2" :data-testid="`story-news-${moment.kind}`" :aria-label="view.eyebrow">
    <div class="flex items-start justify-between gap-3">
      <p class="pt-1 text-[12px] font-semibold uppercase tracking-[0.06em] text-green-700 dark:text-green-400">{{ view.eyebrow }}</p>
      <span class="-mr-2 -mt-1 flex shrink-0 items-center">
        <button v-if="shareIcon" type="button" class="grid h-10 w-10 place-items-center rounded-full text-gray-700 hover:bg-black/5 dark:text-gray-200 dark:hover:bg-white/10" :aria-label="t('story.moment_action_share')" data-testid="story-news-share" @click="emit('act', 'share')">
          <ShareIcon class="h-5 w-5" aria-hidden="true" />
        </button>
        <button type="button" class="grid h-10 w-10 place-items-center rounded-full text-gray-700 hover:bg-black/5 dark:text-gray-200 dark:hover:bg-white/10" :aria-label="t('story.news_done')" data-testid="story-news-dismiss" @click="emit('dismiss')">
          <XMarkIcon class="h-5 w-5" aria-hidden="true" />
        </button>
      </span>
    </div>
    <p class="text-pretty text-[20px] font-bold leading-tight tracking-tight text-gray-900 tabular-nums md:text-[22px] dark:text-gray-50" data-testid="story-news-sentence">
      {{ keepUnits(view.sentence) }}
      <span v-if="view.badge" class="ml-1 inline-block rounded-full border border-gray-300 px-2 py-0.5 align-middle text-[12px] font-semibold tracking-normal text-gray-600 dark:border-gray-600 dark:text-gray-300">{{ view.badge }}</span>
    </p>
    <section v-if="view.forecast" class="mt-1 grid gap-1.5" data-testid="story-news-forecast">
      <h3 class="text-[13px] font-semibold text-gray-700 dark:text-gray-200">{{ t('story.moment_trip_forecast_title') }}</h3>
      <p class="sr-only">{{ view.forecast.sentence }}</p>
      <ul class="grid grid-cols-2 gap-2" aria-hidden="true">
        <li v-for="d in view.forecast.days" :key="d.label" class="edge-box flex items-start gap-2 rounded-lg bg-white px-3 py-2 dark:bg-gray-800">
          <CloudRainIcon v-if="d.rain" class="mt-0.5 h-6 w-6 shrink-0 text-sky-600 dark:text-sky-400" />
          <SunIcon v-else class="mt-0.5 h-6 w-6 shrink-0 text-amber-500 dark:text-amber-400" />
          <span class="grid min-w-0">
            <span class="text-[12px] font-semibold text-gray-600 dark:text-gray-300">{{ d.label }}</span>
            <span class="whitespace-nowrap text-[15px] font-bold text-gray-900 tabular-nums dark:text-gray-50">{{ keepUnits(d.range) }}</span>
            <span v-if="d.rain" class="text-[12px] text-sky-700 dark:text-sky-300">{{ t('story.moment_trip_rain') }}</span>
          </span>
        </li>
      </ul>
    </section>
    <p v-if="view.sub" class="text-pretty text-[14px] text-gray-600 tabular-nums dark:text-gray-300">{{ keepUnits(view.sub) }}</p>
    <div v-if="primary || view.secondary || view.dismiss" class="mt-1 grid gap-1" :class="primary && !primaryIsView ? '' : 'justify-items-start'">
      <template v-if="primary">
        <router-link v-if="primary.to" :to="primary.to" :class="primaryIsView ? link : button" @click="emit('act', primary.id)">{{ primary.label }}<ChevronRightIcon v-if="primaryIsView" class="h-4 w-4" aria-hidden="true" /></router-link>
        <button v-else type="button" :class="primaryIsView ? link : button" @click="emit('act', primary.id)">{{ primary.label }}<ChevronRightIcon v-if="primaryIsView" class="h-4 w-4" aria-hidden="true" /></button>
      </template>
      <div class="flex flex-wrap items-center gap-x-4">
        <template v-if="view.secondary">
          <router-link v-if="view.secondary.to" :to="view.secondary.to" :class="quiet" @click="emit('act', view.secondary.id)">{{ view.secondary.label }}</router-link>
          <button v-else type="button" :class="quiet" @click="emit('act', view.secondary.id)">{{ view.secondary.label }}</button>
        </template>
        <button v-if="view.dismiss" type="button" :class="quiet" @click="emit('dismiss')">{{ t('story.moment_action_dismiss') }}</button>
      </div>
    </div>
  </article>
</template>
