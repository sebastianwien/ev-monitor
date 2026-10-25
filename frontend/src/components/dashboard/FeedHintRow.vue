<script setup lang="ts">
import { computed, type Component } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronRightIcon, XMarkIcon } from '@heroicons/vue/24/outline'

/**
 * Eine Zeile der Hinweis-Karte über dem Log-Feed: Symbol, Satz, Ziel, Schließen.
 * Aufgaben tragen Amber (etwas fehlt), Angebote und Tipps bleiben grau. Die ganze Zeile ist
 * das Ziel (Link oder Knopf), Schließen ist ein eigener Knopf daneben. `plain` ist eine reine
 * Info-Zeile, deren Link im Text steht.
 */
const props = defineProps<{
  icon: Component
  task?: boolean
  to?: string
  plain?: boolean
  dismissLabel: string
}>()
const emit = defineEmits<{ open: []; dismiss: [] }>()
const tag = computed(() => (props.plain ? 'div' : props.to ? RouterLink : 'button'))
</script>

<template>
  <li class="flex items-stretch">
    <component :is="tag" :to="to" :type="tag === 'button' ? 'button' : undefined"
      class="flex min-h-14 min-w-0 flex-1 items-center gap-3 py-2.5 pl-3 pr-1 text-left"
      :class="plain ? '' : 'group rounded-2xl transition-colors hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-story-accent dark:hover:bg-white/5'"
      @click="!plain && !to && emit('open')">
      <span class="grid h-8 w-8 shrink-0 place-items-center rounded-full"
        :class="task ? 'bg-amber-100 text-amber-800 dark:bg-amber-400/15 dark:text-amber-300' : 'bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-gray-300'">
        <component :is="icon" class="h-[18px] w-[18px]" aria-hidden="true" />
      </span>
      <span class="min-w-0 flex-1">
        <span class="block text-[14px] leading-snug text-pretty text-gray-900 dark:text-gray-100"><slot /></span>
        <span v-if="$slots.sub" class="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-[12.5px] tabular-nums"
          :class="task ? 'font-semibold text-amber-800 dark:text-amber-300' : 'text-gray-500 dark:text-gray-400'"><slot name="sub" /></span>
      </span>
      <ChevronRightIcon v-if="!plain" class="h-4 w-4 shrink-0 text-gray-400 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
    </component>
    <button type="button"
      class="grid w-11 shrink-0 place-items-center rounded-2xl text-gray-400 hover:text-gray-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-story-accent dark:hover:text-gray-200"
      :aria-label="dismissLabel" :title="dismissLabel" @click="emit('dismiss')">
      <XMarkIcon class="h-4 w-4" aria-hidden="true" />
    </button>
  </li>
</template>
