<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Moment } from '../../composables/storyMoments'
import { useMomentView, type MomentAction } from '../../composables/useMomentView'
import { keepUnits } from '../../utils/modelStory'

/**
 * Der Moment als Hero: kleine Zeile, was passiert ist, ein Satz in km, Euro und Tagen, eine Handlung.
 * Links fuehren selbst weiter, alles andere (Sheet, Teilen) meldet die Komponente als act.
 */
const props = defineProps<{ moment: Moment; carId: string | null; carName: string; now?: number }>()
const emit = defineEmits<{ act: [id: string]; dismiss: [] }>()

const { t } = useI18n()
const view = useMomentView(props)

const pill = 'inline-flex min-h-[44px] items-center rounded-full bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white transition-transform active:translate-y-0.5 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:bg-gray-100 dark:text-gray-900'
const quiet = 'inline-flex min-h-[44px] items-center text-sm font-semibold text-gray-700 underline underline-offset-2 dark:text-gray-200'
</script>

<template>
  <section class="story-hero grid gap-2" :aria-label="t('story.moment_aria')" :data-testid="`story-moment-${moment.kind}`">
    <p class="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-story-accent dark:text-story-accent-dark">
      <span class="h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true" />{{ view.eyebrow }}
    </p>
    <p class="text-pretty text-[26px] font-extrabold leading-[1.15] tracking-tight text-gray-900 tabular-nums md:text-[38px] dark:text-gray-50" data-testid="story-moment-sentence">
      {{ keepUnits(view.sentence) }}
      <span v-if="view.badge" class="ml-1 inline-block rounded-full border border-gray-300 px-2 py-0.5 align-middle text-[12px] font-semibold tracking-normal text-gray-600 dark:border-gray-600 dark:text-gray-300">{{ view.badge }}</span>
    </p>
    <p v-if="view.sub" class="text-pretty text-[20px] font-bold leading-snug text-story-accent md:text-[26px] dark:text-story-accent-dark">{{ keepUnits(view.sub) }}</p>
    <div v-if="view.action || view.dismiss" class="mt-2 flex flex-wrap items-center gap-x-4 gap-y-2">
      <template v-for="a in [view.action, view.secondary].filter((x): x is MomentAction => !!x)" :key="a.id">
        <router-link v-if="a.to" :to="a.to" :class="a === view.action ? pill : quiet" @click="emit('act', a.id)">{{ a.label }}</router-link>
        <button v-else type="button" :class="a === view.action ? pill : quiet" @click="emit('act', a.id)">{{ a.label }}</button>
      </template>
      <button v-if="view.dismiss" type="button" :class="quiet" @click="emit('dismiss')">{{ t('story.moment_action_dismiss') }}</button>
    </div>
  </section>
</template>
