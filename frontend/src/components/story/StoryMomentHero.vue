<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Moment } from '../../composables/storyMoments'
import { useMomentView, type MomentAction } from '../../composables/useMomentView'
import { keepUnits } from '../../utils/modelStory'

/**
 * Der Moment als Hero im Raster des Monats-Heros: kleine Zeile, was passiert ist, ein Satz in
 * km, Euro und Tagen, eine graue Unterzeile, eine Handlung.
 * Links fuehren selbst weiter, alles andere (Sheet, Teilen) meldet die Komponente als act.
 */
const props = defineProps<{ moment: Moment; carId: string | null; carName: string; now?: number }>()
const emit = defineEmits<{ act: [id: string]; dismiss: [] }>()

const { t } = useI18n()
const view = useMomentView(props)

// Primärknopf grün mit Kante wie die Modellseiten, die einzige grüne Fläche im Hero.
const pill = 'btn-3d inline-flex min-h-[44px] items-center rounded-sm border-2 border-gray-900 bg-green-600 px-3.5 text-[13px] font-semibold text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:border-transparent'
const quiet = 'inline-flex min-h-[44px] items-center text-sm font-semibold text-gray-700 underline underline-offset-2 dark:text-gray-200'
</script>

<template>
  <section class="story-hero grid gap-2" :aria-label="t('story.moment_aria')" :data-testid="`story-moment-${moment.kind}`">
    <p class="text-[12px] font-semibold uppercase tracking-[0.06em] text-green-700 dark:text-green-400">{{ view.eyebrow }}</p>
    <h1 class="text-pretty text-[26px] font-bold leading-[1.1] tracking-tight text-gray-900 tabular-nums md:text-[36px] dark:text-gray-50" data-testid="story-moment-sentence">
      {{ keepUnits(view.sentence) }}
      <span v-if="view.badge" class="ml-1 inline-block rounded-full border border-gray-300 px-2 py-0.5 align-middle text-[12px] font-semibold tracking-normal text-gray-600 dark:border-gray-600 dark:text-gray-300">{{ view.badge }}</span>
    </h1>
    <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
      <p v-if="view.sub" class="text-pretty text-[14px] text-gray-500 tabular-nums dark:text-gray-400">{{ keepUnits(view.sub) }}</p>
      <template v-for="a in [view.action, view.secondary].filter((x): x is MomentAction => !!x)" :key="a.id">
        <router-link v-if="a.to" :to="a.to" :class="a === view.action ? pill : quiet" @click="emit('act', a.id)">{{ a.label }}</router-link>
        <button v-else type="button" :class="a === view.action ? pill : quiet" @click="emit('act', a.id)">{{ a.label }}</button>
      </template>
      <button v-if="view.dismiss" type="button" :class="quiet" @click="emit('dismiss')">{{ t('story.moment_action_dismiss') }}</button>
    </div>
  </section>
</template>
