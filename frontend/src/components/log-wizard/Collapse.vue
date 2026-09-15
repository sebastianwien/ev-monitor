<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'

/**
 * Klappt seinen Inhalt animiert auf null Höhe zusammen statt ihn abrupt zu entfernen.
 * Rein CSS über grid-template-rows, damit keine Höhe gemessen werden muss.
 * Zugeklappt ist der Inhalt inert und nach der Animation display:none, damit
 * space-y/gap des Elternelements keine leere Lücke für ihn reservieren.
 */
const props = defineProps<{ open: boolean }>()
const rendered = ref(props.open)
const expanded = ref(props.open)

watch(() => props.open, async (open) => {
  if (open) {
    rendered.value = true
    await nextTick()
    requestAnimationFrame(() => { expanded.value = true })
  } else {
    expanded.value = false
  }
})

const onEnd = () => { if (!props.open) rendered.value = false }
</script>

<template>
  <div v-show="rendered" :inert="!open" @transitionend.self="onEnd"
    :class="['grid transition-[grid-template-rows,opacity] duration-300 ease-out motion-reduce:transition-none',
             expanded ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0']">
    <div class="min-h-0 overflow-hidden"><slot /></div>
  </div>
</template>
