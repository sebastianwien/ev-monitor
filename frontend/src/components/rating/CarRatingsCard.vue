<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { StarIcon, CheckBadgeIcon } from '@heroicons/vue/24/outline'
import { ratingService, type CarEligibility, type MyRating, type PublicModelRatings } from '../../api/ratingService'
import { STAR_QUESTIONS } from '../../config/ratingQuestions'
import { buildRatingRows, MIN_FOR_AVERAGE } from '../../utils/ratingForm'
import RatingOverall from './RatingOverall.vue'

/**
 * "Fahrer sagen" auf der Fahrzeugseite: die Modellbewertung der Modellseite in kompakt,
 * mit Markierung der eigenen Wertung. Unbewertet und bewertbar: Aufforderung zur eigenen
 * Bewertung. Kein Zaehler zu Schwellen; was andere sagen, ist die Motivation.
 */
const props = defineProps<{ carId: string; eligibility: CarEligibility }>()
const { t, n } = useI18n()

const ratings = ref<PublicModelRatings | null>(null)
const my = ref<MyRating | null>(null)
const loading = ref(true)

async function load() {
  loading.value = true
  const slug = props.eligibility.modelName.replace(/ /g, '_')
  const [pub, mine] = await Promise.all([
    ratingService.getPublicRatings(props.eligibility.brandName, slug).catch(() => null),
    props.eligibility.rated ? ratingService.getMyRating(props.carId).catch(() => null) : Promise.resolve(null),
  ])
  ratings.value = pub
  my.value = mine
  loading.value = false
}
onMounted(load)
watch(() => props.carId, load)

const model = computed(() => props.eligibility.modelName)
const count = computed(() => ratings.value?.count ?? 0)
const enough = computed(() => count.value >= MIN_FOR_AVERAGE)
const rows = computed(() => buildRatingRows(ratings.value?.categories ?? null, my.value?.answers ?? null))
const modelPagePath = computed(() =>
  `/modelle/${encodeURIComponent(props.eligibility.brandName)}/${encodeURIComponent(props.eligibility.modelName.replace(/ /g, '_'))}`)
const ratePath = computed(() => `/cars/${props.carId}/bewerten?src=cars`)
const showCta = computed(() => props.eligibility.eligible && !props.eligibility.rated)
const visible = computed(() => loading.value || count.value > 0 || showCta.value)
const avg = (v: number) => n(v, { minimumFractionDigits: 1, maximumFractionDigits: 1 })
</script>

<template>
  <section v-if="visible" class="rounded-sm bg-white dark:bg-gray-700 border-2 border-gray-300 dark:border-gray-600 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] p-4 md:p-5" data-testid="car-ratings-card">
    <div class="flex items-baseline justify-between gap-3">
      <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('rating.cars.title') }}</h2>
      <a v-if="count > 0" :href="modelPagePath" class="text-xs font-medium text-indigo-600 dark:text-indigo-400">{{ t('rating.cars.model_page') }}</a>
    </div>

    <div v-if="loading" class="mt-4 h-20 animate-pulse rounded-sm bg-gray-100 dark:bg-gray-600" aria-hidden="true" />

    <template v-else>
      <!-- Noch keine Bewertung zum Modell, aber bewertbar: der Anfang -->
      <div v-if="count === 0" class="mt-3 space-y-3">
        <p class="text-sm text-gray-700 dark:text-gray-300">{{ t('rating.public.own_cta', { model }) }}</p>
        <RouterLink :to="ratePath" class="btn-3d inline-flex items-center justify-center min-h-11 rounded-sm bg-green-600 px-4 text-sm font-semibold text-white hover:bg-green-700 transition" data-testid="car-rate-cta">
          {{ t('rating.cars.rate_cta', { model }) }}
        </RouterLink>
      </div>

      <template v-else>
        <!-- Kopf: Gesamtnote ab 5, darunter der Wiederkauf -->
        <div class="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2">
          <RatingOverall v-if="enough && ratings!.aggregate" :value="ratings!.aggregate.ratingValue" :count="ratings!.aggregate.ratingCount" size="lg" :with-hint="false" />
          <p class="text-sm text-gray-700 dark:text-gray-300">
            <span class="font-semibold text-gray-900 dark:text-gray-100">{{ t('rating.result.rebuy_main', { yes: ratings!.rebuy.yes, count }) }}</span>
            {{ t('rating.public.rebuy_label', { model }, count) }}
          </p>
        </div>
        <p v-if="ratings!.totalCost" class="mt-2 text-sm text-gray-600 dark:text-gray-400">
          {{ t('rating.public.cost', { cheaper: ratings!.totalCost.cheaper, expected: ratings!.totalCost.expected, pricier: ratings!.totalCost.pricier }) }}
          <template v-if="my"> {{ t('rating.cars.you_cost', { verdict: t(`rating.public.cost_single.${my.answers.totalCost}`) }) }}</template>
        </p>

        <!-- Ab 5: Zeilen je Frage mit eigener Markierung -->
        <ul v-if="enough" class="mt-3 divide-y divide-gray-100 dark:divide-gray-600">
          <li v-for="row in rows" :key="row.key" class="flex items-center gap-2 py-2 text-sm">
            <span class="min-w-0 flex-1 text-gray-800 dark:text-gray-200">{{ t(`rating.result.categories.${row.key}`) }}</span>
            <span class="flex" :aria-label="t('rating.public.avg_of', { v: avg(row.average), count: row.count })">
              <StarIcon v-for="s in 5" :key="s" class="h-4 w-4" :class="s <= Math.round(row.average) ? 'fill-current text-amber-500 dark:text-amber-400' : 'text-gray-300 dark:text-gray-500'" aria-hidden="true" />
            </span>
            <span class="w-8 text-right tabular-nums font-semibold text-gray-900 dark:text-gray-100">{{ avg(row.average) }}</span>
            <span v-if="row.mine !== null" class="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-indigo-600 px-1 text-[11px] font-bold text-white" :title="t('rating.result.legend_you')">
              <span class="sr-only">{{ t('rating.result.legend_you') }}: </span>{{ row.mine }}
            </span>
            <span v-else class="w-5" aria-hidden="true" />
          </li>
        </ul>

        <!-- Unter 5: Einzelkarten wie auf der Modellseite -->
        <ul v-else class="mt-3 grid gap-3">
          <li v-for="(single, i) in ratings!.singles" :key="i" class="rounded-sm border border-gray-200 dark:border-gray-600 p-3 space-y-2">
            <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ t(`rating.q.rebuy.${single.rebuy}`) }}</p>
            <RatingOverall :value="single.ratingValue" :with-hint="false" />
            <dl class="space-y-1">
              <div v-for="q in STAR_QUESTIONS" :key="q.key" class="flex items-center justify-between gap-2 text-sm">
                <dt class="text-gray-600 dark:text-gray-400">{{ t(`rating.result.categories.${q.key}`) }}</dt>
                <dd v-if="single[q.field] !== null" class="flex" :aria-label="t('rating.stars_aria', { n: single[q.field], label: t(`rating.q.${q.key}.s${single[q.field]}`) })">
                  <StarIcon v-for="s in 5" :key="s" class="w-4 h-4" :class="s <= single[q.field]! ? 'fill-current text-amber-500 dark:text-amber-400' : 'text-gray-300 dark:text-gray-500'" aria-hidden="true" />
                </dd>
                <dd v-else class="text-gray-400 dark:text-gray-500">-</dd>
              </div>
            </dl>
          </li>
        </ul>

        <!-- Eigene Bewertung -->
        <div class="mt-4 flex flex-wrap items-center gap-3">
          <template v-if="eligibility.rated">
            <span class="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-900/30 dark:text-amber-300" data-testid="car-rating-row">
              <CheckBadgeIcon class="h-3.5 w-3.5" aria-hidden="true" />{{ t('rating.badge_rated') }}
            </span>
            <RouterLink :to="ratePath" class="inline-flex items-center min-h-11 text-sm font-medium text-green-700 dark:text-green-400">{{ t('rating.cars.rated_edit') }}</RouterLink>
          </template>
          <RouterLink v-else-if="showCta" :to="ratePath" class="btn-3d inline-flex w-full items-center justify-center min-h-11 rounded-sm bg-green-600 px-4 text-sm font-semibold text-white hover:bg-green-700 transition" data-testid="car-rate-cta">
            {{ t('rating.cars.rate_cta', { model }) }}
          </RouterLink>
        </div>
      </template>
    </template>
  </section>
</template>
