<script setup lang="ts">
import { computed } from 'vue'
import { FINDINGS, TONE_CLASS } from './seoHelp'

const props = defineProps<{ finding: string }>()
const f = computed(() => FINDINGS[props.finding])
</script>

<template>
  <span v-if="f" :class="['inline-block text-xs px-2 py-0.5 rounded-full whitespace-nowrap', TONE_CLASS[f.tone]]" :title="`${f.meaning} Was tun: ${f.action}`">
    {{ f.label }}
  </span>
</template>
