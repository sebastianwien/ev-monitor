<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRightIcon, BoltIcon } from '@heroicons/vue/24/outline'
import { useTickerItems, VARIANT_ICON } from '../../composables/useTickerItems'
import { numberParts } from '../../utils/numberParts'

/**
 * "Die Community im Oktober": alle Monatsstatistiken des Community-Tickers, gleiche Icons wie
 * das Band oben, der Monat steht nur im Titel, Zahlen fett. Steht auf der Seite, solange die Rückblick-Karte
 * offen ist, und verschwindet mit ihr. Ohne Daten nichts.
 */
const props = defineProps<{ year: number; month: number }>()
const emit = defineEmits<{ rank: [] }>()

const { t, locale } = useI18n()
const { summaryItems: lines, fetchCommunityMonth } = useTickerItems()

const key = computed(() => `${props.year}-${String(props.month).padStart(2, '0')}`)
const monthName = computed(() => new Intl.DateTimeFormat(locale.value, { month: 'long' }).format(new Date(props.year, props.month - 1, 1)))

watch(key, k => { fetchCommunityMonth(k) }, { immediate: true })
</script>

<template>
  <section v-if="lines.length" data-testid="story-community-month" class="pt-2">
    <h2 class="flex items-center gap-3 text-center text-[18px] font-bold tracking-tight text-story-accent md:text-[20px] dark:text-story-accent-dark">
      <span class="h-px flex-1 bg-edge" aria-hidden="true" />
      <span class="text-balance">{{ t('story.community_month_title', { month: monthName }) }}</span>
      <span class="h-px flex-1 bg-edge" aria-hidden="true" />
    </h2>
    <ul class="mt-3 grid gap-2">
      <li v-for="line in lines" :key="line.key" data-testid="story-community-line" class="flex items-start gap-2.5 text-[15px] text-pretty text-gray-900 tabular-nums dark:text-gray-50">
        <span class="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300" aria-hidden="true">
          <component :is="VARIANT_ICON[line.variant] ?? BoltIcon" class="h-3 w-3" />
        </span>
        <span><template v-for="(part, i) in numberParts(line.text)" :key="i"><strong v-if="part.number" class="font-semibold">{{ part.text }}</strong><template v-else>{{ part.text }}</template></template></span>
      </li>
    </ul>
    <router-link
      to="/leaderboard"
      class="mt-1 inline-flex min-h-[44px] items-center gap-1 text-sm font-semibold text-green-700 underline-offset-4 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-green-700 dark:text-green-300"
      @click="emit('rank')">
      {{ t('story.moment_action_rank') }}
      <ArrowRightIcon class="h-4 w-4" aria-hidden="true" />
    </router-link>
  </section>
</template>
