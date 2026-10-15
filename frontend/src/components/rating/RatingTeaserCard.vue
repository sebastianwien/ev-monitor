<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { StarIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useRatingStore } from '../../stores/rating'
import { isRatingTeaserVisible, ratingTeaserStorage } from '../../utils/ratingEntry'
import { analytics } from '../../services/analytics'

/**
 * Hinweis, sobald das Auto bewertbar ist. Je Auto wegklickbar, der Zustand gilt für Dashboard und /cars gemeinsam.
 * `embedded`: ohne eigenen Rahmen, als Kopfzeile in einer Karte (z. B. /cars).
 */
const props = withDefaults(defineProps<{ carId: string; source?: string; embedded?: boolean }>(), { source: 'dashboard', embedded: false })
const emit = defineEmits<{ dismiss: [] }>()
const { t } = useI18n()
const ratingStore = useRatingStore()

const dismissed = ref(false)

watch(() => props.carId, carId => {
    dismissed.value = ratingTeaserStorage.isDismissed(carId)
    ratingStore.getEligibility().catch(() => { /* ohne Hinweis weiter */ })
}, { immediate: true })

const car = computed(() => ratingStore.forCar(props.carId))
const visible = computed(() => isRatingTeaserVisible(car.value, dismissed.value))

function dismiss() {
    ratingTeaserStorage.dismiss(props.carId)
    dismissed.value = true
    analytics.track('rating_teaser_dismissed', { source: props.source })
    emit('dismiss')
}
</script>

<template>
    <div
        v-if="visible && car"
        class="flex items-center gap-3 p-3 md:p-4"
        :class="embedded
            ? 'bg-amber-50 dark:bg-amber-900/20 border-b-2 border-gray-300 dark:border-gray-600'
            : 'mb-4 rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800'"
        data-testid="rating-teaser"
    >
        <StarIcon class="w-6 h-6 shrink-0 text-amber-500 dark:text-amber-400" aria-hidden="true" />
        <p class="flex-1 text-sm text-gray-700 dark:text-gray-300">{{ t('rating.teaser.text', { model: car.modelName }) }}</p>
        <RouterLink
            :to="`/cars/${carId}/bewerten?src=${source}`"
            class="shrink-0 min-h-11 flex items-center px-4 rounded-sm bg-green-600 hover:bg-green-700 text-white text-sm font-semibold"
        >
            {{ t('rating.teaser.cta') }}
        </RouterLink>
        <button type="button" class="shrink-0 w-11 h-11 -mr-2 flex items-center justify-center text-gray-400 hover:text-gray-700 dark:hover:text-gray-200" :aria-label="t('rating.teaser.dismiss')" @click="dismiss">
            <XMarkIcon class="w-5 h-5" aria-hidden="true" />
        </button>
    </div>
</template>
