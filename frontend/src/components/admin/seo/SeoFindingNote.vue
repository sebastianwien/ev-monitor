<script setup lang="ts">
import { computed } from 'vue'
import { FINDINGS } from './seoHelp'

/** Sichtbare Erklärung zum aktiven Befundfilter: was er bedeutet, was zu tun ist. */
const props = defineProps<{ finding: string | null }>()
const f = computed(() => (props.finding ? FINDINGS[props.finding] : null))
</script>

<template>
  <div v-if="f" class="border-l-2 border-indigo-500 bg-gray-900 px-4 py-3 text-sm">
    <p class="text-gray-200"><span class="font-semibold">{{ f.label }}:</span> {{ f.meaning }}</p>
    <p class="text-gray-300 mt-1"><span class="font-semibold text-gray-200">Was tun:</span> {{ f.action }}</p>
  </div>
</template>
