<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { BoltIcon, ChartBarIcon, CheckCircleIcon } from '@heroicons/vue/24/outline'
import RatingCoreForm from '../components/rating/RatingCoreForm.vue'
import RatingResultPanel from '../components/rating/RatingResultPanel.vue'
import RatingDeepForm from '../components/rating/RatingDeepForm.vue'
import { ratingService, type CarEligibility, type RatingResult, type ReviewState } from '../api/ratingService'
import { useRatingStore } from '../stores/rating'
import { useCoinStore } from '../stores/coins'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { analytics } from '../services/analytics'
import {
    emptyDraft, answeredCount, canSubmitCore, toAnswers, fromMyRating, reviewKind, draftStorage,
    CORE_QUESTION_COUNT,
} from '../utils/ratingForm'

/**
 * Fahrzeug bewerten: Kernfragen, persönliches Ergebnis, optionale Vertiefung.
 * Eigene Route statt Modal, damit Announcement, Dashboard, /cars und Mail direkt hierher verlinken.
 */
type Screen = 'loading' | 'error' | 'not_eligible' | 'core' | 'result' | 'deep' | 'done'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const ratingStore = useRatingStore()
const coinStore = useCoinStore()
const { formatDistance } = useLocaleFormat()

const carId = route.params.carId as string
const source = typeof route.query.src === 'string' ? route.query.src.slice(0, 30) : 'direct'
/** Aus dem Lösch-Flow gekommen: danach zurück und das Löschen fortsetzen. */
const continueDelete = route.query.danach === 'loeschen'
const returnPath = continueDelete ? `/cars?loeschen=${encodeURIComponent(carId)}` : '/cars'

const screen = ref<Screen>('loading')
const car = ref<CarEligibility | null>(null)
const thresholds = ref({ minLogs: 5, minKm: 300 })
const draft = reactive(emptyDraft())
const review = ref<ReviewState | null>(null)
const result = ref<RatingResult | null>(null)
const coins = ref(0)
const wasRated = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)

const model = computed(() => car.value?.modelName ?? '')
const answered = computed(() => answeredCount(draft))
const logsMissing = computed(() => Math.max(0, thresholds.value.minLogs - (car.value?.logCount ?? 0)))
const monthsWithCar = computed(() => {
    const first = car.value?.firstLogDate
    if (!first) return null
    const start = new Date(first)
    const now = new Date()
    return Math.max(1, (now.getFullYear() - start.getFullYear()) * 12 + now.getMonth() - start.getMonth())
})
const modelPagePath = computed(() => car.value
    ? `/modelle/${encodeURIComponent(car.value.brandName)}/${encodeURIComponent(car.value.modelName.replace(/ /g, '_'))}`
    : '/modelle')

onMounted(async () => {
    try {
        const eligibility = await ratingStore.getEligibility(true)
        thresholds.value = { minLogs: eligibility.minLogs, minKm: eligibility.minKm }
        car.value = eligibility.cars.find(c => c.carId === carId) ?? null
        if (!car.value) {
            error.value = t('rating.not_found')
            screen.value = 'error'
            return
        }
        if (!car.value.eligible) {
            screen.value = 'not_eligible'
            return
        }
        const mine = await ratingService.getMyRating(carId)
        if (mine) {
            Object.assign(draft, fromMyRating(mine))
            review.value = mine.review
            wasRated.value = true
        } else {
            const saved = draftStorage.load(carId)
            if (saved) Object.assign(draft, saved)
        }
        screen.value = 'core'
        analytics.track('rating_started', { source, edit: wasRated.value })
    } catch {
        error.value = t('rating.load_error')
        screen.value = 'error'
    }
})

// Entwurf sichern, damit ein Abbruch nichts verliert
watch(draft, () => {
    if (screen.value === 'core' || screen.value === 'deep') draftStorage.save(carId, draft)
}, { deep: true })

function goTo(next: Screen) {
    screen.value = next
    error.value = null
    window.scrollTo({ top: 0 })
}

async function saveRating(): Promise<RatingResult> {
    const saved = await ratingService.submitRating(carId, toAnswers(draft))
    coins.value += saved.coinsAwarded
    if (saved.coinsAwarded > 0) coinStore.refresh()
    ratingStore.invalidate()
    return saved
}

async function submitCore() {
    if (!canSubmitCore(draft) || saving.value) return
    saving.value = true
    error.value = null
    try {
        result.value = await saveRating()
        analytics.track('rating_completed_core', { source, edit: wasRated.value })
        wasRated.value = true
        goTo('result')
    } catch {
        error.value = t('rating.save_error')
    } finally {
        saving.value = false
    }
}

async function submitDeep() {
    if (saving.value) return
    saving.value = true
    error.value = null
    try {
        // Stärken und Schwächen gehören zur Bewertung selbst
        result.value = await saveRating()
        const body = draft.body.trim()
        if (body) {
            review.value = await ratingService.submitReview(carId, {
                body,
                pros: draft.pros.map(p => p.trim()).filter(Boolean),
                cons: draft.cons.map(c => c.trim()).filter(Boolean),
                deleteWithCar: draft.deleteWithCar,
            })
            analytics.track('review_submitted', { kind: reviewKind(body) })
        } else if (review.value && review.value.deleteWithCar !== draft.deleteWithCar) {
            review.value = await ratingService.updateReviewSettings(carId, draft.deleteWithCar)
        }
        analytics.track('rating_completed_deep', {
            chips: draft.strengths.length + draft.weaknesses.length,
            text: reviewKind(body),
        })
        draftStorage.clear(carId)
        goTo('done')
    } catch {
        error.value = t('rating.save_error')
    } finally {
        saving.value = false
    }
}

function finish() {
    draftStorage.clear(carId)
    router.push(returnPath)
}
</script>

<template>
    <div class="min-h-screen md:py-10 md:px-4">
        <div class="md:max-w-xl md:mx-auto bg-white dark:bg-gray-800 md:shadow-sm md:border md:rounded-sm border-gray-200 dark:border-gray-700 px-4 sm:px-6 pb-32 md:pb-6">

            <p v-if="screen === 'loading'" class="py-16 text-center text-gray-400 dark:text-gray-500">{{ t('rating.loading') }}</p>

            <div v-else-if="screen === 'error'" class="py-12 text-center space-y-4">
                <p class="text-gray-600 dark:text-gray-300">{{ error }}</p>
                <RouterLink to="/cars" class="inline-block text-green-700 dark:text-green-400 font-medium">{{ t('rating.done.back_to_cars') }}</RouterLink>
            </div>

            <template v-else-if="car">
                <!-- Kopf: Auto, Erfahrung, Anonymität, Messdaten -->
                <header class="pt-5 pb-2 space-y-3">
                    <div class="flex items-center gap-3">
                        <span class="w-10 h-10 shrink-0 flex items-center justify-center rounded-sm bg-green-50 dark:bg-green-900/30">
                            <BoltIcon class="w-6 h-6 text-green-700 dark:text-green-400" aria-hidden="true" />
                        </span>
                        <div class="min-w-0">
                            <p class="font-semibold text-gray-900 dark:text-gray-100 truncate">{{ car.brandName }} {{ car.modelName }}</p>
                            <p class="text-sm text-gray-500 dark:text-gray-400 tabular-nums">
                                <span v-if="car.kmDriven > 0">{{ t('rating.head.km_driven', { distance: formatDistance(car.kmDriven) }) }}</span>
                                <span v-if="car.kmDriven > 0 && monthsWithCar"> · </span>
                                <span v-if="monthsWithCar">{{ t('rating.head.since', { n: monthsWithCar }, monthsWithCar) }}</span>
                            </p>
                        </div>
                    </div>
                    <template v-if="screen === 'core'">
                        <h1 class="text-xl font-bold text-gray-900 dark:text-gray-100 text-balance">{{ t('rating.head.title', { model }) }}</h1>
                        <p class="text-sm text-gray-600 dark:text-gray-300">{{ t('rating.head.lead', { model }) }}</p>
                        <p class="text-sm text-gray-600 dark:text-gray-300">{{ t('rating.head.anonymity', { model }) }}</p>
                        <p class="flex gap-2 rounded-sm bg-gray-50 dark:bg-gray-700/50 p-3 text-sm text-gray-600 dark:text-gray-300">
                            <ChartBarIcon class="w-5 h-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
                            {{ t('rating.head.measured') }}
                        </p>
                    </template>
                    <template v-else-if="screen === 'deep'">
                        <h1 class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ t('rating.deep.title') }}</h1>
                        <p class="text-sm text-gray-600 dark:text-gray-300">{{ t('rating.deep.lead') }}</p>
                    </template>
                </header>

                <!-- Noch nicht bewertbar -->
                <section v-if="screen === 'not_eligible'" class="py-6 space-y-3">
                    <h1 class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ t('rating.not_eligible.title') }}</h1>
                    <p v-if="logsMissing > 0" class="text-gray-700 dark:text-gray-300">
                        {{ t('rating.not_eligible.logs_missing', { n: logsMissing }, logsMissing) }}
                    </p>
                    <p class="text-sm text-gray-500 dark:text-gray-400">
                        {{ t('rating.not_eligible.rule', { logs: thresholds.minLogs, distance: formatDistance(thresholds.minKm) }) }}
                    </p>
                    <RouterLink to="/logs" class="inline-flex items-center min-h-11 px-4 rounded-sm bg-green-600 hover:bg-green-700 text-white font-semibold">
                        {{ t('rating.not_eligible.cta') }}
                    </RouterLink>
                </section>

                <RatingCoreForm v-else-if="screen === 'core'" v-model:draft="draft" :model="model" />
                <RatingResultPanel v-else-if="screen === 'result' && result" :result="result" :model="model" :coins="coins" />
                <RatingDeepForm v-else-if="screen === 'deep'" v-model:draft="draft" :model="model" :review="review" />

                <section v-else-if="screen === 'done'" class="py-8 space-y-4 text-center">
                    <CheckCircleIcon class="w-14 h-14 mx-auto text-green-600 dark:text-green-400" aria-hidden="true" />
                    <h1 class="text-xl font-bold text-gray-900 dark:text-gray-100 text-balance">{{ t('rating.done.title', { model }) }}</h1>
                    <p v-if="review?.status === 'PENDING'" class="text-sm text-gray-600 dark:text-gray-300">{{ t('rating.done.review_pending') }}</p>
                    <p v-if="coins > 0" class="text-sm text-gray-600 dark:text-gray-300">{{ t('rating.done.coins_total', { n: coins }) }}</p>
                    <div class="flex flex-col gap-2 pt-2">
                        <RouterLink v-if="continueDelete" :to="returnPath" class="min-h-11 flex items-center justify-center rounded-sm bg-red-600 hover:bg-red-700 text-white font-semibold">{{ t('rating.delete_sheet.continue_delete') }}</RouterLink>
                        <a v-else :href="modelPagePath" class="min-h-11 flex items-center justify-center rounded-sm bg-green-600 hover:bg-green-700 text-white font-semibold">{{ t('rating.done.model_page', { model }) }}</a>
                        <RouterLink v-if="!continueDelete" to="/cars" class="min-h-11 flex items-center justify-center text-sm font-medium text-gray-600 dark:text-gray-300">{{ t('rating.done.back_to_cars') }}</RouterLink>
                    </div>
                </section>

                <!-- Status eines vorhandenen Texts (Bearbeiten) -->
                <p v-if="screen === 'deep' && review" class="mb-4 rounded-sm bg-gray-50 dark:bg-gray-700/50 p-3 text-sm text-gray-700 dark:text-gray-300" data-testid="review-status">
                    <span class="font-medium">{{ t(`rating.review_status.${review.status}`) }}</span>
                    <template v-if="review.status === 'PENDING' && review.published"> - {{ t('rating.review_status.pending_published') }}</template>
                    <template v-if="review.status === 'REJECTED' && review.rejectionReason">
                        - {{ t('rating.review_status.rejected_reason', { reason: t(`rating.review_status.reasons.${review.rejectionReason}`) }) }}
                        <span v-if="review.rejectionNote" class="block mt-1">{{ review.rejectionNote }}</span>
                    </template>
                </p>
            </template>
        </div>

        <!-- Aktionsleiste: mobil fest über der Bottom-Navigation, ab md im Fluss -->
        <div
            v-if="screen === 'core' || screen === 'result' || screen === 'deep'"
            class="fixed inset-x-0 bottom-[calc(3.5rem+env(safe-area-inset-bottom))] md:static md:max-w-xl md:mx-auto z-20 bg-white dark:bg-gray-800 border-t md:border md:border-t-0 border-gray-200 dark:border-gray-700 px-4 py-3"
        >
            <p v-if="error" class="mb-2 text-sm text-center text-red-600 dark:text-red-400" role="alert">{{ error }}</p>
            <div v-if="screen === 'core'" class="flex items-center gap-3">
                <div class="flex-1 space-y-1 text-xs text-gray-500 dark:text-gray-400 tabular-nums">
                    <span>{{ t('rating.progress', { n: answered, total: CORE_QUESTION_COUNT }) }}</span>
                    <div class="h-1.5 rounded-full bg-gray-200 dark:bg-gray-700 overflow-hidden" role="progressbar"
                         :aria-valuenow="answered" aria-valuemin="0" :aria-valuemax="CORE_QUESTION_COUNT">
                        <div class="h-full bg-green-600 transition-all duration-300" :style="{ width: `${(answered / CORE_QUESTION_COUNT) * 100}%` }" />
                    </div>
                </div>
                <button
                    type="button"
                    :disabled="!canSubmitCore(draft) || saving"
                    class="min-h-12 px-5 rounded-sm bg-green-600 hover:bg-green-700 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:cursor-not-allowed text-white font-semibold"
                    @click="submitCore"
                >
                    {{ saving ? t('rating.saving') : canSubmitCore(draft) ? t('rating.submit') : t('rating.submit_missing', { n: CORE_QUESTION_COUNT - answered }) }}
                </button>
            </div>
            <div v-else-if="screen === 'result'" class="flex items-center gap-3">
                <button type="button" class="min-h-12 px-3 text-gray-600 dark:text-gray-300 font-medium" @click="finish">{{ continueDelete ? t('rating.delete_sheet.continue_delete') : t('rating.result.done') }}</button>
                <button type="button" class="flex-1 min-h-12 px-4 rounded-sm bg-green-600 hover:bg-green-700 text-white font-semibold" @click="goTo('deep')">
                    {{ t('rating.result.deepen') }}
                </button>
            </div>
            <div v-else class="flex items-center gap-3">
                <button type="button" class="min-h-12 px-3 text-gray-600 dark:text-gray-300 font-medium" :disabled="saving" @click="goTo(result ? 'result' : 'core')">{{ t('rating.deep.back') }}</button>
                <button type="button" :disabled="saving" class="flex-1 min-h-12 px-4 rounded-sm bg-green-600 hover:bg-green-700 disabled:bg-gray-300 text-white font-semibold" @click="submitDeep">
                    {{ saving ? t('rating.saving') : t('rating.deep.save') }}
                </button>
            </div>
        </div>
    </div>
</template>
