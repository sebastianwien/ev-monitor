<script setup lang="ts">
import { computed } from 'vue'
import { numberParts } from '../../utils/numberParts'

/** Ein fertiger Satz mit fett gesetzten Zahlen. */
const props = defineProps<{ text: string }>()
const parts = computed(() => numberParts(props.text))
</script>

<template>
  <span><template v-for="(p, i) in parts" :key="i"><strong v-if="p.number" class="font-bold">{{ p.text }}</strong><template v-else>{{ p.text }}</template></template></span>
</template>
