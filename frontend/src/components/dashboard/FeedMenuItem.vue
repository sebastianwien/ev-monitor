<script setup lang="ts">
import type { Component } from 'vue'

/** Ein Eintrag im FeedActionMenu. `separated` setzt ihn mit einer Linie ab (Loeschen). */
defineProps<{ icon: Component; separated?: boolean }>()
</script>

<template>
  <button role="menuitem" type="button"
    class="flex min-h-11 w-full items-center gap-3 px-3.5 text-left text-[14px] font-medium text-gray-800 transition hover:bg-gray-50 focus:outline-none focus-visible:bg-gray-100 dark:text-gray-200 dark:hover:bg-white/5 dark:focus-visible:bg-white/10"
    :class="separated ? 'mt-1 border-t border-gray-200 dark:border-gray-700' : ''">
    <component :is="icon" class="h-4 w-4 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" /><slot />
  </button>
</template>
