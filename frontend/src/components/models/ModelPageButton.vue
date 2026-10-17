<template>
  <!-- The one way to a model page from the list's open row and the compare page: dark, bold, the arrow behind.
       compact: dense columns on the phone (three models side by side), no arrow below md. -->
  <RouterLink :to="href" class="btn-3d cta-shadow-sm inline-flex min-h-11 min-w-0 items-center justify-center gap-2 rounded-xl border-2 border-gray-900 bg-gray-900 font-bold text-white hover:bg-gray-800 dark:border-gray-100 dark:bg-gray-100 dark:text-gray-900"
     :class="compact ? 'px-2 text-[13.5px] md:px-4 md:text-base' : 'px-4'">
    <span class="truncate">{{ label }}</span>
    <ArrowRightIcon class="h-5 w-5 flex-none" :class="compact ? 'hidden md:block' : ''" aria-hidden="true" />
  </RouterLink>
</template>

<script setup lang="ts">
import { ArrowRightIcon } from '@heroicons/vue/24/outline'

defineProps<{
  href: string
  label: string
  compact?: boolean
}>()
</script>
