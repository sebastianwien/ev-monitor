<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { StarIcon } from '@heroicons/vue/24/outline'

/**
 * Gesamtnote sichtbar auf der Seite. Was im Product-JSON-LD als Bewertung steht, muss Besuchern so auch angezeigt
 * werden. count nur beim Schnitt ab 5 Bewertungen.
 */
const props = withDefaults(defineProps<{ value: number; count?: number | null; size?: 'lg' | 'sm'; withHint?: boolean }>(), { count: null, size: 'sm', withHint: true })

const { t, n } = useI18n()
const formatted = computed(() => n(props.value, { minimumFractionDigits: 1, maximumFractionDigits: 1 }))
const filled = computed(() => Math.round(props.value))
</script>

<template>
    <div class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
        <span class="text-gray-600 dark:text-gray-400" :class="size === 'lg' ? 'text-base' : 'text-sm'">{{ t('rating.public.overall') }}</span>
        <span class="flex" role="img" :aria-label="t('rating.public.overall_aria', { v: formatted })">
            <StarIcon v-for="s in 5" :key="s" :class="[size === 'lg' ? 'w-5 h-5' : 'w-4 h-4', s <= filled ? 'fill-current text-amber-500 dark:text-amber-400' : 'text-gray-300 dark:text-gray-600']" aria-hidden="true" />
        </span>
        <span class="font-semibold tabular-nums text-gray-900 dark:text-gray-100" :class="size === 'lg' ? 'text-lg' : 'text-sm'">{{ t('rating.public.overall_value', { v: formatted }) }}</span>
        <span v-if="count" class="text-sm text-gray-500 dark:text-gray-400">{{ t('rating.public.overall_count', { count }, count) }}</span>
        <span v-if="withHint" class="basis-full text-xs text-gray-500 dark:text-gray-400">{{ t(count ? 'rating.public.overall_hint_aggregate' : 'rating.public.overall_hint_single') }}</span>
    </div>
</template>
