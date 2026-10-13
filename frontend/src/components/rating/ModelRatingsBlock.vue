<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChatBubbleLeftRightIcon, StarIcon, CheckBadgeIcon } from '@heroicons/vue/24/outline'
import { STAR_QUESTIONS } from '../../config/ratingQuestions'
import { MIN_FOR_AVERAGE } from '../../utils/ratingForm'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { analytics } from '../../services/analytics'
import type { PublicModelRatings } from '../../api/ratingService'

/**
 * "Fahrer sagen" auf der Modellseite. Öffentlich nur absolute Zahlen, ab 5 Bewertungen Schnitt,
 * darunter Einzelkarten. Alle Texte stehen im DOM (auch zugeklappte Berichte), damit Crawler sie sehen.
 */
const props = defineProps<{
    ratings: PublicModelRatings
    model: string
    /** Eigenes, bewertbares und noch unbewertetes Auto dieses Modells. */
    ctaCarId: string | null
    registerPath: string | null
}>()

const { t, n, locale } = useI18n()
const { formatDistance, formatConsumption } = useLocaleFormat()

const enough = computed(() => props.ratings.count >= MIN_FOR_AVERAGE)
const missing = computed(() => MIN_FOR_AVERAGE - props.ratings.count)
const expanded = ref<Set<number>>(new Set())

const categoryRows = computed(() => {
    const c = props.ratings.categories
    if (!c) return []
    return STAR_QUESTIONS.map(q => ({ key: q.key, ...c[q.field] })).filter(r => r.average !== null)
})

function driverLine(km: number, month?: string): string {
    const distance = km > 0 ? t('rating.public.km', { distance: formatDistance(km) }) : t('rating.public.km_below', { distance: formatDistance(5000) })
    const who = t('rating.public.driver', { model: props.model })
    return month ? `${who}, ${distance} · ${formatMonth(month)}` : `${who}, ${distance}`
}

function formatMonth(month: string): string {
    const [year, m] = month.split('-').map(Number)
    return new Intl.DateTimeFormat(locale.value, { month: 'long', year: 'numeric' }).format(new Date(year, m - 1, 1))
}

function toggle(index: number) {
    const next = new Set(expanded.value)
    if (next.has(index)) next.delete(index)
    else next.add(index)
    expanded.value = next
}

// Einmal melden, wenn der Block wirklich in den Blick kommt
const root = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
onMounted(() => {
    if (!root.value || typeof IntersectionObserver === 'undefined') return
    observer = new IntersectionObserver(entries => {
        if (entries.some(e => e.isIntersecting)) {
            analytics.track('rating_block_viewed', { count: props.ratings.count })
            observer?.disconnect()
            observer = null
        }
    }, { threshold: 0.3 })
    observer.observe(root.value)
})
onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
    <section ref="root" class="min-w-0 bg-white dark:bg-gray-900 border-y border-gray-200 dark:border-gray-800 px-4 py-6 md:rounded-3xl md:border md:p-8 md:shadow-[0_1px_2px_rgba(0,0,0,0.04)] space-y-6" data-testid="model-ratings">
        <h2 class="flex items-center gap-2 text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">
            <ChatBubbleLeftRightIcon class="h-6 w-6 text-green-700 dark:text-green-400" aria-hidden="true" />
            {{ t('rating.public.title') }}
        </h2>

        <!-- Leer -->
        <div v-if="ratings.count === 0" class="space-y-3">
            <p v-if="!ctaCarId" class="text-gray-700 dark:text-gray-300">{{ t('rating.public.empty', { model }) }}</p>
            <a v-if="registerPath && !ctaCarId" :href="registerPath" class="inline-flex items-center min-h-11 font-semibold text-green-700 dark:text-green-400 underline underline-offset-4">
                {{ t('rating.public.empty_cta') }}
            </a>
        </div>

        <template v-else>
            <!-- Wiederkauf: Hauptzahl -->
            <div class="space-y-1">
                <p class="flex flex-wrap items-baseline gap-x-2">
                    <span class="text-4xl font-bold tabular-nums whitespace-nowrap text-green-700 dark:text-green-400">
                        {{ t('rating.result.rebuy_main', { yes: ratings.rebuy.yes, count: ratings.count }) }}
                    </span>
                    <span class="text-gray-700 dark:text-gray-300">{{ t('rating.public.rebuy_label', { model }, ratings.count) }}</span>
                </p>
                <p v-if="ratings.rebuy.brandOtherModel" class="text-sm text-gray-500 dark:text-gray-400">
                    {{ t('rating.result.rebuy_brand', { n: ratings.rebuy.brandOtherModel }, ratings.rebuy.brandOtherModel) }}<template v-if="ratings.rebuyReasons.length">.
                        {{ t('rating.public.reasons', { list: ratings.rebuyReasons.map(r => `${t(`rating.q.rebuy.reasons.${r.value}`)} (${r.count})`).join(', ') }) }}</template>
                </p>
                <p v-if="ratings.rebuy.no" class="text-sm text-gray-500 dark:text-gray-400">
                    {{ t('rating.result.rebuy_no', { n: ratings.rebuy.no }, ratings.rebuy.no) }}
                </p>
            </div>

            <!-- Ab 5: Schnitt je Kategorie, Kosten, Top-Chips -->
            <template v-if="enough">
                <div class="space-y-3">
                    <div v-for="row in categoryRows" :key="row.key" class="space-y-1">
                        <div class="flex justify-between text-sm">
                            <span class="text-gray-900 dark:text-gray-100">{{ t(`rating.result.categories.${row.key}`) }}</span>
                            <span class="tabular-nums text-gray-500 dark:text-gray-400">
                                {{ t('rating.public.avg_of', { v: n(row.average!, { minimumFractionDigits: 1, maximumFractionDigits: 1 }), count: row.count }) }}
                            </span>
                        </div>
                        <div class="h-2.5 rounded-full bg-gray-200 dark:bg-gray-700" aria-hidden="true">
                            <div class="h-full rounded-full bg-green-600 dark:bg-green-500" :style="{ width: `${(row.average! / 5) * 100}%` }" />
                        </div>
                    </div>
                </div>
                <p v-if="ratings.totalCost" class="text-sm text-gray-700 dark:text-gray-300">
                    {{ t('rating.public.cost', { cheaper: ratings.totalCost.cheaper, expected: ratings.totalCost.expected, pricier: ratings.totalCost.pricier }) }}
                </p>
                <div class="grid gap-4 sm:grid-cols-2">
                    <div v-for="list in (['strengths', 'weaknesses'] as const)" :key="list" v-show="ratings[list].length" class="space-y-2">
                        <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ t(`rating.public.${list}`) }}</p>
                        <ul class="flex flex-wrap gap-2">
                            <li v-for="chip in ratings[list]" :key="chip.value" class="px-3 py-1 rounded-full border border-gray-200 dark:border-gray-600 text-sm text-gray-700 dark:text-gray-300">
                                {{ t(`rating.chips.${chip.value}`) }} <span class="tabular-nums text-gray-400">{{ chip.count }}</span>
                            </li>
                        </ul>
                    </div>
                </div>
            </template>

            <!-- Unter 5: Einzelkarten -->
            <template v-else>
                <ul class="grid gap-3 sm:grid-cols-2">
                    <li v-for="(single, i) in ratings.singles" :key="i" class="rounded-sm border border-gray-200 dark:border-gray-700 p-4 space-y-2">
                        <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ t(`rating.q.rebuy.${single.rebuy}`) }}</p>
                        <dl class="space-y-1">
                            <div v-for="q in STAR_QUESTIONS" :key="q.key" class="flex items-center justify-between gap-2 text-sm">
                                <dt class="text-gray-600 dark:text-gray-400">{{ t(`rating.result.categories.${q.key}`) }}</dt>
                                <dd v-if="single[q.field] !== null" class="flex" :aria-label="t('rating.stars_aria', { n: single[q.field], label: t(`rating.q.${q.key}.s${single[q.field]}`) })">
                                    <StarIcon v-for="s in 5" :key="s" class="w-4 h-4" :class="s <= single[q.field]! ? 'fill-current text-amber-500 dark:text-amber-400' : 'text-gray-300 dark:text-gray-600'" aria-hidden="true" />
                                </dd>
                                <dd v-else class="text-gray-400 dark:text-gray-500">-</dd>
                            </div>
                        </dl>
                        <p class="text-sm text-gray-600 dark:text-gray-400">{{ t(`rating.q.total_cost.${single.totalCost}`) }}</p>
                        <p class="text-xs text-gray-500 dark:text-gray-400">
                            {{ driverLine(single.kmRounded) }}<template v-if="single.businessCar"> · {{ t('rating.public.business_car') }}</template>
                        </p>
                    </li>
                </ul>
                <p class="text-sm text-gray-600 dark:text-gray-300">{{ t('rating.public.unlock', { n: missing }, missing) }}</p>
            </template>

            <!-- Zitate -->
            <figure v-for="(quote, i) in ratings.quotes" :key="`q${i}`" class="border-l-4 border-green-600 pl-4 space-y-1">
                <blockquote class="text-gray-800 dark:text-gray-200 whitespace-pre-line">{{ quote.text }}</blockquote>
                <figcaption class="text-xs text-gray-500 dark:text-gray-400">{{ driverLine(quote.kmRounded, quote.month) }}</figcaption>
            </figure>
        </template>

        <!-- Erfahrungsberichte: Text vollständig im DOM, zugeklappt nur per line-clamp -->
        <div v-if="ratings.reports.length" class="space-y-4">
            <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ t('rating.public.reports_title') }}</h3>
            <article v-for="(report, i) in ratings.reports" :key="`r${i}`" class="rounded-sm border border-gray-200 dark:border-gray-700 p-4 space-y-3">
                <p class="text-xs text-gray-500 dark:text-gray-400">{{ driverLine(report.kmRounded, report.month) }}</p>
                <p :id="`report-${i}`" class="text-gray-800 dark:text-gray-200 whitespace-pre-line" :class="{ 'line-clamp-6': !expanded.has(i) }">{{ report.text }}</p>
                <button type="button" class="min-h-11 text-sm font-medium text-green-700 dark:text-green-400" :aria-expanded="expanded.has(i)" :aria-controls="`report-${i}`" @click="toggle(i)">
                    {{ expanded.has(i) ? t('rating.public.read_less') : t('rating.public.read_more') }}
                </button>
                <div v-if="report.pros.length || report.cons.length" class="grid gap-3 sm:grid-cols-2 text-sm">
                    <ul v-if="report.pros.length" class="space-y-1">
                        <li class="font-medium text-gray-900 dark:text-gray-100">{{ t('rating.deep.pros') }}</li>
                        <li v-for="(p, j) in report.pros" :key="j" class="text-gray-700 dark:text-gray-300">+ {{ p }}</li>
                    </ul>
                    <ul v-if="report.cons.length" class="space-y-1">
                        <li class="font-medium text-gray-900 dark:text-gray-100">{{ t('rating.deep.cons') }}</li>
                        <li v-for="(c, j) in report.cons" :key="j" class="text-gray-700 dark:text-gray-300">- {{ c }}</li>
                    </ul>
                </div>
                <p v-if="report.logCount" class="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                    <CheckBadgeIcon class="w-4 h-4 text-green-700 dark:text-green-400" aria-hidden="true" />
                    {{ t('rating.public.proof', { logs: report.logCount }) }}<template v-if="report.consumptionKwhPer100km"> · {{ t('rating.public.proof_consumption', { consumption: formatConsumption(report.consumptionKwhPer100km) }) }}</template>
                </p>
            </article>
        </div>

        <!-- Eigenes Auto dieses Modells, noch nicht bewertet -->
        <a v-if="ctaCarId" :href="`/cars/${ctaCarId}/bewerten?src=modellseite`" class="flex items-center justify-center min-h-11 px-4 rounded-sm bg-green-600 hover:bg-green-700 text-white font-semibold text-center">
            {{ t('rating.public.own_cta', { model }) }}
        </a>

        <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('rating.public.transparency') }}</p>
    </section>
</template>
