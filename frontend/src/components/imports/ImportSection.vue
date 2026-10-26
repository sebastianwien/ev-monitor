<script setup lang="ts">
import { ref, watch, onUnmounted, useId, type Component } from 'vue'
import { ChevronDownIcon } from '@heroicons/vue/24/outline'
import Collapse from '../shared/Collapse.vue'

/**
 * Eine Sektion auf /imports: Kopfzeile zum Auf- und Zuklappen, darunter der Weg (docs/design/ci.md, Collapse).
 * Welche offen ist, hält die Seite (useImportsTab), die Sektion meldet nur den Klick.
 * mark: Kürzel in der Kachel, wenn es für den Anbieter kein Icon gibt (T, SM, VW).
 */
const props = defineProps<{
  open: boolean
  title: string
  subtitle?: string
  /** Ergebnis in Grün unter dem Titel ("AutoSync aktiv für ...") */
  status?: string
  badge?: string
  icon?: Component
  mark?: string
}>()
defineEmits<{ toggle: [] }>()

const bodyId = useId()

// Inhalt nur, solange offen (plus Zuklapp-Animation, 300 ms wie Collapse): die Integrationen laden beim
// Mounten ihren Status, zugeklappt sollen sie weder laden noch nachfragen
const mounted = ref(props.open)
let unmountTimer: ReturnType<typeof setTimeout> | undefined
watch(() => props.open, (open) => {
  clearTimeout(unmountTimer)
  if (open) mounted.value = true
  else unmountTimer = setTimeout(() => { mounted.value = false }, 300)
})
onUnmounted(() => clearTimeout(unmountTimer))
</script>

<template>
  <div>
    <button type="button" :aria-expanded="open" :aria-controls="bodyId"
      class="flex min-h-16 w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-green-600 md:px-8 dark:hover:bg-white/5"
      @click="$emit('toggle')">
      <span class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-200" aria-hidden="true">
        <component :is="icon" v-if="icon" class="h-5 w-5" />
        <span v-else class="text-[13px] font-extrabold leading-none tracking-tight">{{ mark }}</span>
      </span>
      <span class="min-w-0 flex-1">
        <span class="flex flex-wrap items-center gap-2">
          <span class="text-[15px] font-semibold text-gray-900 dark:text-gray-100">{{ title }}</span>
          <span v-if="badge" class="rounded-full bg-gray-100 px-2 py-0.5 text-[11.5px] font-bold uppercase tracking-wide text-gray-600 dark:bg-gray-800 dark:text-gray-300">{{ badge }}</span>
        </span>
        <span v-if="status" class="mt-0.5 block text-[13px] font-semibold text-green-700 dark:text-green-400">{{ status }}</span>
        <span v-else-if="subtitle" class="mt-0.5 block text-[13px] text-gray-500 dark:text-gray-400">{{ subtitle }}</span>
      </span>
      <ChevronDownIcon class="h-5 w-5 shrink-0 text-gray-400 transition-transform duration-300 motion-reduce:transition-none"
        :class="open ? 'rotate-180 ease-out' : 'ease-in'" aria-hidden="true" />
    </button>
    <Collapse :id="bodyId" :open="open">
      <div v-if="mounted" class="px-4 pb-6 pt-1 md:px-8">
        <slot />
      </div>
    </Collapse>
  </div>
</template>
