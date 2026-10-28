<script setup lang="ts">
import { ref, watch } from 'vue'

/**
 * Jahreswechsel als kurzes Gleiten: der Inhalt des alten Jahres gleitet zur Seite hinaus, der des neuen von der
 * anderen Seite herein, ein späteres Jahr von rechts, ein früheres von links. Der Behälter beschneidet waagerecht
 * (overflow-x: clip), sonst ragt der Inhalt kurz über den Rand. Transition-Hooks wie @enter reicht er durch.
 */
const props = defineProps<{ year: number }>()
const forward = ref(true)
watch(() => props.year, (y, prev) => { forward.value = y > prev })
</script>

<template>
  <Transition mode="out-in"
    enter-active-class="transition duration-200 ease-out motion-reduce:transition-none" leave-active-class="transition duration-150 ease-in motion-reduce:transition-none"
    :enter-from-class="forward ? 'translate-x-8 opacity-0' : '-translate-x-8 opacity-0'" :leave-to-class="forward ? '-translate-x-8 opacity-0' : 'translate-x-8 opacity-0'">
    <div :key="year"><slot /></div>
  </Transition>
</template>
