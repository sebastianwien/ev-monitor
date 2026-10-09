<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { StarIcon } from '@heroicons/vue/24/outline'
import { SKIP } from '../../utils/ratingForm'

/**
 * Fünf Sterne als Radiogruppe, jeder Stern mit Klartext. Optional eine Ausweich-Antwort
 * ("Fahre ich selten Langstrecke"), die als beantwortet zählt, aber keine Sterne vergibt.
 */
const props = defineProps<{
    modelValue: number | typeof SKIP | null
    labels: string[]
    labelledBy: string
    skipLabel?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: number | typeof SKIP] }>()

const { t } = useI18n()
const buttons = ref<HTMLButtonElement[]>([])

const stars = computed(() => (typeof props.modelValue === 'number' ? props.modelValue : 0))
const hint = computed(() => {
    if (typeof props.modelValue === 'number') return props.labels[props.modelValue - 1]
    if (props.modelValue === SKIP) return t('rating.skip_hint')
    return ''
})

/** Roving Tabindex: nur der gewählte (oder erste) Stern ist per Tab erreichbar. */
function tabIndexFor(value: number): number {
    return (stars.value || 1) === value ? 0 : -1
}

function select(value: number) {
    emit('update:modelValue', value)
}

function onKey(event: KeyboardEvent, value: number) {
    const step = event.key === 'ArrowRight' || event.key === 'ArrowUp' ? 1
        : event.key === 'ArrowLeft' || event.key === 'ArrowDown' ? -1 : 0
    if (!step) return
    event.preventDefault()
    const next = Math.min(5, Math.max(1, value + step))
    select(next)
    buttons.value[next - 1]?.focus()
}
</script>

<template>
    <div>
        <div class="flex flex-wrap items-center gap-1">
            <div role="radiogroup" :aria-labelledby="labelledBy" class="flex items-center">
                <button
                    v-for="value in 5"
                    :key="value"
                    :ref="el => { if (el) buttons[value - 1] = el as HTMLButtonElement }"
                    type="button"
                    role="radio"
                    :aria-checked="modelValue === value"
                    :aria-label="t('rating.stars_aria', { n: value, label: labels[value - 1] })"
                    :tabindex="tabIndexFor(value)"
                    class="w-11 h-11 flex items-center justify-center rounded-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-green-600"
                    @click="select(value)"
                    @keydown="onKey($event, value)"
                >
                    <StarIcon
                        class="w-8 h-8 transition-colors"
                        :class="value <= stars ? 'fill-current text-amber-500 dark:text-amber-400' : 'text-gray-300 dark:text-gray-600'"
                        aria-hidden="true"
                    />
                </button>
            </div>
            <button
                v-if="skipLabel"
                type="button"
                :aria-pressed="modelValue === SKIP"
                class="ml-1 min-h-11 px-3 rounded-full border text-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-green-600"
                :class="modelValue === SKIP
                    ? 'border-green-600 bg-green-50 dark:bg-green-900/30 text-gray-900 dark:text-gray-100 font-medium'
                    : 'border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300'"
                @click="emit('update:modelValue', SKIP)"
            >
                {{ skipLabel }}
            </button>
        </div>
        <!-- Feste Höhe: kein Layout-Sprung, wenn der Klartext erscheint -->
        <p class="min-h-5 mt-1 text-sm text-gray-500 dark:text-gray-400" aria-live="polite">{{ hint }}</p>
    </div>
</template>
