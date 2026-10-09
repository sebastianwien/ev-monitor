<script setup lang="ts" generic="T extends string">
import { toggleChip, isChipDisabled, MAX_CHIPS } from '../../utils/ratingForm'

/** Mehrfachauswahl als Chips, höchstens {@code max}. Weitere Chips werden gesperrt statt still ignoriert. */
const props = withDefaults(defineProps<{
    modelValue: T[]
    options: { value: T; label: string }[]
    labelledBy: string
    max?: number
}>(), { max: MAX_CHIPS })
const emit = defineEmits<{ 'update:modelValue': [value: T[]] }>()

function toggle(value: T) {
    emit('update:modelValue', toggleChip(props.modelValue, value, props.max))
}
</script>

<template>
    <div role="group" :aria-labelledby="labelledBy" class="flex flex-wrap gap-2">
        <button
            v-for="option in options"
            :key="option.value"
            type="button"
            :aria-pressed="modelValue.includes(option.value)"
            :disabled="isChipDisabled(modelValue, option.value, max)"
            class="min-h-11 px-4 rounded-full border text-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-green-600 disabled:opacity-40 disabled:cursor-not-allowed"
            :class="modelValue.includes(option.value)
                ? 'border-green-600 bg-green-50 dark:bg-green-900/30 text-gray-900 dark:text-gray-100 font-medium'
                : 'border-gray-200 dark:border-gray-600 text-gray-700 dark:text-gray-300'"
            @click="toggle(option.value)"
        >
            {{ option.label }}
        </button>
    </div>
</template>
