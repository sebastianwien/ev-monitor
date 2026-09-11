<script setup lang="ts">
defineProps<{ id: string; unit: string; placeholder?: string; step?: string; min?: number; max?: number; inputmode?: 'decimal' | 'numeric'; testid?: string; autofocus?: boolean }>()
const model = defineModel<number | null>()
const onInput = (e: Event) => {
  const v = (e.target as HTMLInputElement).value
  model.value = v === '' ? null : Number(v)
}
</script>

<template>
  <div class="flex items-baseline gap-2 border-b-2 border-gray-200 dark:border-gray-600 focus-within:border-indigo-600 py-1">
    <input :id="id" :data-testid="testid" type="number" :inputmode="inputmode ?? 'decimal'" :step="step ?? 'any'" :min="min" :max="max"
      :placeholder="placeholder" :value="model ?? ''" :autofocus="autofocus" @input="onInput"
      class="flex-1 min-w-0 bg-transparent border-0 p-0 text-4xl font-medium tabular-nums text-gray-900 dark:text-gray-100 placeholder:text-gray-300 dark:placeholder:text-gray-600 focus:ring-0" />
    <span class="text-base text-gray-500 dark:text-gray-400">{{ unit }}</span>
  </div>
</template>
