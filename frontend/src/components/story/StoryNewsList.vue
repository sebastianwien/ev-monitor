<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Moment, MomentSeen, ScoredMoment } from '../../composables/storyMoments'
import StoryNewsCard from './StoryNewsCard.vue'
import Confetti from './Confetti.vue'

/**
 * Neuigkeiten ab lg: oben in der rechten Spalte als Liste statt als Stapel von unten. Kein
 * Abdunkeln, kein Wischen; × heißt erledigt wie im Stapel. Höchstens drei, "Alle zeigen" klappt
 * den Rest auf. Gezeigt gilt eine Karte, sobald sie sichtbar ist; Konfetti beim ersten Meilenstein.
 */
const props = defineProps<{
  cards: ScoredMoment[]
  carId: string | null
  carName: string
  seen: MomentSeen
}>()
const emit = defineEmits<{
  act: [moment: Moment, id: string]
  dismiss: [moment: Moment]
  shown: [moment: Moment]
}>()
const { t } = useI18n()

const LIMIT = 3
const expanded = ref(false)
const visible = computed(() => expanded.value ? props.cards : props.cards.slice(0, LIMIT))

const celebrate = ref<string | null>(null)
const reported = new Set<string>()
watch(visible, (cards) => {
  for (const { moment: m } of cards) {
    if (reported.has(m.key)) continue
    reported.add(m.key)
    if (m.kind === 'milestone' && props.seen.shown[m.key] == null) celebrate.value = m.key
    emit('shown', m)
  }
}, { immediate: true })
</script>

<template>
  <section v-if="cards.length" class="grid content-start gap-3" role="region" :aria-label="t('story.news_region')" aria-live="polite" data-testid="story-news-inline">
    <Confetti v-if="celebrate" :key="celebrate" />
    <div class="flex items-center gap-3">
      <span class="h-0.5 flex-1 bg-gray-900 dark:bg-gray-100" aria-hidden="true" />
      <h2 class="text-[18px] font-bold tracking-tight text-gray-900 md:text-[20px] dark:text-gray-100">{{ t('story.news_title', { n: cards.length }) }}</h2>
      <span class="h-0.5 flex-1 bg-gray-900 dark:bg-gray-100" aria-hidden="true" />
    </div>
    <div v-for="c in visible" :key="c.moment.key" class="rounded-2xl bg-white p-4 edge-card dark:bg-gray-700/50">
      <StoryNewsCard :moment="c.moment" :car-id="carId" :car-name="carName" @act="emit('act', c.moment, $event)" @dismiss="emit('dismiss', c.moment)" />
    </div>
    <button v-if="!expanded && cards.length > LIMIT" type="button" class="inline-flex min-h-11 items-center justify-self-start text-sm font-semibold text-gray-700 underline underline-offset-2 dark:text-gray-200" data-testid="story-news-all" @click="expanded = true">{{ t('story.news_show_all') }}</button>
  </section>
</template>
